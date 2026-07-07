#!/usr/bin/env python3
"""Generate per-person OpenStream setup profiles from the owner's users.json.

A setup profile is the app's paste-one-link install format (DECISIONS #14):

    {"openstream": 1, "name": "...", "addons": [{"name": "...", "url": "..."}]}

Each person gets one JSON file; host it anywhere private (your domain, a
secret gist) and they paste its URL into Add addon — every addon previews
and installs on the TV in one confirm.

SECURITY: users.json and the generated profiles contain personal tokens.
This script therefore writes into the gitignored private folder by default
and never prints a URL. THE GENERATED FILES MUST NEVER BE COMMITTED.

Usage:
    python3 tools/make_profiles.py                      # all users, primary instance
    python3 tools/make_profiles.py --instance backup    # use backup AIOStreams
    python3 tools/make_profiles.py --users path/to/users.json --out /tmp/profiles
"""
import argparse
import json
import re
import secrets
import sys
from pathlib import Path

CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"  # public, safe to embed
DEFAULT_USERS = Path(__file__).resolve().parent.parent / "docs/reference/StremioSurfer/users.json"

# Owner's own per-person custom addons (2026-07-07 finalize/trim decision):
# MediaFusion and TMDB duplicate scrapers/catalogs AIOStreams already wraps
# internally (Service Wrap) — excluded from the generated profile by default.
# This does NOT touch users.json: the manifest URLs stay there untouched,
# just unused when assembling a profile. Override per-run via
# profiles.config.json's "exclude_addon_keys" (fully replaces this default
# when that key is present, even if set to an empty list).
DEFAULT_EXCLUDED_ADDON_KEYS = {"mediafusion_manifest", "tmdb_manifest"}


def is_manifest_url(value):
    return isinstance(value, str) and value.startswith(("http://", "https://")) \
        and value.endswith("manifest.json")


def profile_for(user, instance, pulled=None, exclude_addon_keys=None):
    """Addon order is meaningful (§4.1.7: install order = stream-group order):
    metadata sources first (Cinemeta fallback, then AIOMetadata), then the
    person's live Stremio collection (tools/pull_stremio_addons.py) or the
    assembled AIOStreams URL — both come BEFORE the supplementary catalog
    addons below (2026-07-07: owner reordered so AIOStreams' own catalogs
    lead Home instead of trailing behind them). Union, deduped by URL: the
    live account is truth for stream addons, but the curated catalog addons
    in users.json aren't in those accounts and would be lost otherwise
    (DECISIONS #15)."""
    exclude_addon_keys = DEFAULT_EXCLUDED_ADDON_KEYS if exclude_addon_keys is None else exclude_addon_keys
    addons = [{"name": "Cinemeta", "url": CINEMETA}]

    aiometadata = (user.get("aiometadata") or {}).get("manifest_url")
    if is_manifest_url(aiometadata):
        addons.append({"name": "AIOMetadata", "url": aiometadata})

    account = (pulled or {}).get(user.get("email") or "")
    if account and account.get("addons"):
        addons.extend(account["addons"])  # live collection, account order
    else:
        aiostreams = user.get("aiostreams") or {}
        chosen = aiostreams.get(instance) or {}
        url = chosen.get("manifest_url")
        if not is_manifest_url(url):  # fall back to any instance that has one
            for fallback, data in aiostreams.items():
                candidate = (data or {}).get("manifest_url")
                if is_manifest_url(candidate):
                    url, instance = candidate, fallback
                    break
        if is_manifest_url(url):
            addons.append({"name": f"AIOStreams ({instance})", "url": url})

    for key, value in sorted((user.get("addons") or {}).items()):
        if key in exclude_addon_keys:
            continue
        if is_manifest_url(value):
            pretty = re.sub(r"_manifest$", "", key).replace("_", " ").title()
            addons.append({"name": pretty, "url": value})

    seen, unique = set(), []
    for addon in addons:
        if addon["url"] not in seen:
            seen.add(addon["url"])
            unique.append(addon)
    return {"openstream": 1, "name": user.get("name", "OpenStream setup"), "addons": unique}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--users", type=Path, default=DEFAULT_USERS)
    parser.add_argument("--out", type=Path, default=None,
                        help="output dir (default: <users.json dir>/profiles)")
    parser.add_argument("--instance", default="primary",
                        choices=["primary", "backup", "nightly"])
    args = parser.parse_args()

    if not args.users.exists():
        sys.exit(f"users file not found: {args.users}")
    out_dir = args.out or args.users.parent / "profiles"
    out_dir.mkdir(parents=True, exist_ok=True)

    # Sidecar config (lives next to users.json, so private by construction):
    #   skip  — user names (case-insensitive) to not generate for
    #   links — name -> filename, so a person's URL survives regeneration.
    # Filenames carry a random token: profiles get hosted on a public domain,
    # and a guessable name like jody-miller.json would hand out her tokens.
    config_path = args.users.parent / "profiles.config.json"
    config = json.loads(config_path.read_text()) if config_path.exists() \
        else {"skip": [], "links": {}}
    skip = {s.lower() for s in config.get("skip", [])}
    exclude_addon_keys = set(config["exclude_addon_keys"]) if "exclude_addon_keys" in config \
        else DEFAULT_EXCLUDED_ADDON_KEYS

    pulled_path = args.users.parent / "stremio_addons.json"
    pulled = json.loads(pulled_path.read_text()) if pulled_path.exists() else {}

    data = json.loads(args.users.read_text())
    for user in data.get("users", []):
        name = user.get("name", "")
        if name.lower() in skip:
            print(f"(skipped {name})")
            continue
        profile = profile_for(user, args.instance, pulled, exclude_addon_keys)
        filename = config["links"].get(name)
        if not filename:
            slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "user"
            filename = f"{slug}-{secrets.token_urlsafe(8)}.json"
            config["links"][name] = filename
        path = out_dir / filename
        path.write_text(json.dumps(profile, indent=2) + "\n")
        # names and counts only — never echo URLs
        print(f"{path.name}: {len(profile['addons'])} addons "
              f"({', '.join(a['name'] for a in profile['addons'])})")

    config_path.write_text(json.dumps(config, indent=2) + "\n")
    print(f"\nWrote to {out_dir} — these files contain tokens; host privately, never commit.")


if __name__ == "__main__":
    main()
