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
import sys
from pathlib import Path

CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"  # public, safe to embed
DEFAULT_USERS = Path(__file__).resolve().parent.parent / "docs/reference/StremioSurfer/users.json"


def is_manifest_url(value):
    return isinstance(value, str) and value.startswith(("http://", "https://")) \
        and value.endswith("manifest.json")


def profile_for(user, instance):
    """Addon order is meaningful (§4.1.7: install order = stream-group order):
    metadata sources first (Cinemeta fallback, then AIOMetadata), extra
    catalog addons, and AIOStreams last."""
    addons = [{"name": "Cinemeta", "url": CINEMETA}]

    aiometadata = (user.get("aiometadata") or {}).get("manifest_url")
    if is_manifest_url(aiometadata):
        addons.append({"name": "AIOMetadata", "url": aiometadata})

    for key, value in sorted((user.get("addons") or {}).items()):
        if is_manifest_url(value):
            pretty = re.sub(r"_manifest$", "", key).replace("_", " ").title()
            addons.append({"name": pretty, "url": value})

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

    return {"openstream": 1, "name": user.get("name", "OpenStream setup"), "addons": addons}


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

    data = json.loads(args.users.read_text())
    for user in data.get("users", []):
        profile = profile_for(user, args.instance)
        slug = re.sub(r"[^a-z0-9]+", "-", profile["name"].lower()).strip("-") or "user"
        path = out_dir / f"{slug}.json"
        path.write_text(json.dumps(profile, indent=2) + "\n")
        # names and counts only — never echo URLs
        print(f"{path.name}: {len(profile['addons'])} addons "
              f"({', '.join(a['name'] for a in profile['addons'])})")
    print(f"\nWrote to {out_dir} — these files contain tokens; host privately, never commit.")


if __name__ == "__main__":
    main()
