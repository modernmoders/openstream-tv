# Setup links

A **setup link** installs a whole set of addons in one paste (DECISIONS #14).
It points at a small JSON file you host anywhere reachable over HTTPS:

```json
{
  "openstream": 1,
  "name": "Living-room setup",
  "addons": [
    { "name": "Cinemeta",   "url": "https://v3-cinemeta.strem.io/manifest.json" },
    { "name": "My streams", "url": "https://example.com/abc/manifest.json" }
  ]
}
```

- `openstream: 1` is required — it marks the file as a profile (and versions
  the format).
- `addons` order matters: it becomes the install order, which is the
  stream-group order on the streams screen (MASTER_PLAN §4.1.7). Put
  metadata/catalog addons first and stream addons last.
- Entries whose `url` isn't a valid `…/manifest.json` URL are shown as
  broken in the preview and skipped — one bad addon never blocks the rest.

On the TV: **Addons → Add addon**, paste the link (or use the browser entry
page shown on that screen and paste it from your phone). Every addon is
previewed on the TV and nothing installs until you confirm there.

## Generating profiles per person

`tools/make_profiles.py` builds one profile per user from a private
`users.json` (see the script header for the expected shape). A sidecar
`profiles.config.json` next to it holds:

- `skip` — names to not generate for
- `links` — assigned filenames, so a person's URL stays stable across runs

Optional but recommended first: `tools/pull_stremio_addons.py` logs into
each person's Stremio account (credentials from users.json) and saves their
real installed-addon list; make_profiles then merges it in, so a profile
mirrors what each person actually uses (DECISIONS #15).

## Name-lookup page (optional)

`tools/make_hosting_bundle.py` produces an upload-ready folder: the profile
files plus an `index.php` where a person types "first name + last initial"
and gets their setup link with a copy button. Upload the folder's contents
to any PHP host. On Dreamhost: Panel → Websites → Manage → your domain →
Manage Files, create a `setup` folder, upload everything into it, then the
page is at `https://yourdomain/setup/`. Re-run the bundle script and
re-upload whenever profiles change.

Tradeoff to understand: with the lookup page, knowing the domain + a first
name + last initial is enough to fetch that person's profile URL. That is
deliberate convenience for a family install; add a shared PIN to index.php
if you need more than that.

## Hosting rules (these files contain personal tokens)

1. **HTTPS only**, any static host works: a plain web server, Cloudflare
   Pages, GitHub Pages on a private repo is NOT ok (pages are public but
   that's fine only if filenames are unguessable AND the repo is private —
   when in doubt, use a real host you control.)
2. **Unguessable filenames** — the generator appends a random token
   (`name-Xy8Kq2fLpQw.json`) precisely so the URL is the secret. Never
   host an index/directory listing of the folder.
3. **Never commit profiles or users.json** to this repo — the private
   reference folder is gitignored; keep it that way.
4. Rotating a link = delete its entry from `links` in
   `profiles.config.json`, regenerate, re-host, send the new URL.
