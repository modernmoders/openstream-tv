#!/usr/bin/env python3
"""Pull each user's real addon collection from their Stremio account.

Logs into api.strem.io with the credentials in the owner's private
users.json (the owner manages these family accounts) and saves each
account's installed-addon list — names, order, and transport URLs — to
stremio_addons.json NEXT TO users.json (private folder, gitignored).
make_profiles.py prefers this data over the hand-assembled list, so a
generated profile mirrors what the person actually uses in Stremio today,
in the same order.

Never prints URLs, passwords, or auth keys — names and counts only.

Usage:
    python3 tools/pull_stremio_addons.py
    python3 tools/pull_stremio_addons.py --users path/to/users.json
"""
import argparse
import json
import ssl
import sys
import urllib.request
from pathlib import Path

API = "https://api.strem.io/api/"
DEFAULT_USERS = Path(__file__).resolve().parent.parent / "docs/reference/StremioSurfer/users.json"


def tls_context():
    """macOS pythons often lack a CA bundle; fall back to certifi / system pem."""
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        system_pem = Path("/etc/ssl/cert.pem")
        if system_pem.exists():
            return ssl.create_default_context(cafile=str(system_pem))
        return ssl.create_default_context()


TLS = tls_context()


def api_call(method, payload):
    req = urllib.request.Request(
        API + method,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=20, context=TLS) as resp:
        body = json.loads(resp.read())
    if body.get("error"):
        raise RuntimeError(body["error"].get("message", str(body["error"])))
    result = body.get("result")
    if result is None:
        raise RuntimeError("empty result")
    return result


def pull_account(email, password):
    """Returns this account's addons as [{"name":…, "url":…}], account order."""
    login = api_call("login", {"type": "Login", "email": email, "password": password})
    auth_key = login.get("authKey")
    if not auth_key:
        raise RuntimeError("login gave no authKey")
    collection = api_call(
        "addonCollectionGet",
        {"type": "AddonCollectionGet", "authKey": auth_key, "update": True},
    )
    addons = []
    for addon in collection.get("addons", []):
        url = addon.get("transportUrl", "")
        name = (addon.get("manifest") or {}).get("name", "") or "Unnamed addon"
        # Local-machine addons (Stremio's local files server etc.) can't work
        # from a TV; drop them instead of shipping guaranteed-broken entries.
        if "127.0.0.1" in url or "localhost" in url:
            continue
        if url.startswith(("http://", "https://")):
            addons.append({"name": name, "url": url})
    return addons


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--users", type=Path, default=DEFAULT_USERS)
    args = parser.parse_args()
    if not args.users.exists():
        sys.exit(f"users file not found: {args.users}")

    config_path = args.users.parent / "profiles.config.json"
    skip = set()
    if config_path.exists():
        skip = {s.lower() for s in json.loads(config_path.read_text()).get("skip", [])}

    data = json.loads(args.users.read_text())
    out, failures = {}, []
    for user in data.get("users", []):
        name = user.get("name", "?")
        if name.lower() in skip:
            print(f"(skipped {name})")
            continue
        email, password = user.get("email"), user.get("password")
        if not email or not password:
            failures.append((name, "no email/password in users.json"))
            continue
        try:
            addons = pull_account(email, password)
        except Exception as e:  # one bad account must not kill the run
            failures.append((name, str(e)))
            continue
        out[email] = {"name": name, "addons": addons}
        print(f"{name}: {len(addons)} addons ({', '.join(a['name'] for a in addons)})")

    out_path = args.users.parent / "stremio_addons.json"
    out_path.write_text(json.dumps(out, indent=2) + "\n")
    print(f"\nSaved {len(out)} accounts to {out_path} (private folder — never commit).")
    for name, why in failures:
        print(f"FAILED {name}: {why}")


if __name__ == "__main__":
    main()
