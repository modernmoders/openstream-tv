#!/usr/bin/env python3
"""Build the bundled IMDb -> MyAnimeList map (app asset anime_imdb_mal.json).

Why this exists: AniSkip (intro/credits skip) is keyed by MAL id + episode,
but the owner's anime plays under IMDb ids (tt...). Two community datasets
together bridge that:

  * Fribb/anime-lists  (anime-list-full.json): one entry per anime "part",
    carrying imdb_id + mal_id and, when a show has several parts sharing one
    IMDb/TVDB series, which TVDB season the part is ("season": {"tvdb": N}).
  * ScudLee/Anime-Lists (anime-list.xml, anidb<->tvdb): defaulttvdbseason
    ("a" = the show is ABSOLUTE-numbered, one entry over all seasons - Naruto)
    and episodeoffset (several parts inside ONE tvdb season - split cours).

Output (minified, read by ImdbMalBridge.parseAnimeMap):
    {"tt2560140": [[16498,1,0],[25777,2,0],...], "tt0409591": [[20,-1,0]], ...}
row = [malId, tvdbSeason (-1 = absolute), episodeOffset]

Run from the repo root (downloads ~9 MB, writes the asset):
    python3 tools/build_anime_map.py
Optionally point at pre-downloaded copies:
    python3 tools/build_anime_map.py --fribb fribb.json --scudlee anime-list.xml
"""

import argparse
import json
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

FRIBB_URL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-full.json"
SCUDLEE_URL = "https://raw.githubusercontent.com/Anime-Lists/anime-lists/master/anime-list.xml"
ASSET = Path(__file__).resolve().parent.parent / "app/src/main/assets/anime_imdb_mal.json"

ABSOLUTE = -1
# Movies resolve trivially (no episode arithmetic) and the player only asks
# for series; leaving them out keeps the asset small.
SERIES_TYPES = {"TV", "ONA", "OVA", "SPECIAL", "UNKNOWN"}


def fetch(url: str, cache: Path) -> str:
    if cache.exists():
        return cache.read_text()
    with urllib.request.urlopen(url, timeout=120) as resp:
        text = resp.read().decode("utf-8")
    cache.write_text(text)
    return text


def scudlee_seasons(xml_text: str) -> dict:
    """anidb id -> (tvdb season or ABSOLUTE, episode offset)."""
    out = {}
    for anime in ET.fromstring(xml_text).iter("anime"):
        anidb = anime.get("anidbid")
        season = anime.get("defaulttvdbseason")
        if not anidb or season is None:
            continue
        offset = int(anime.get("episodeoffset") or 0)
        if season == "a":
            out[int(anidb)] = (ABSOLUTE, offset)
        elif season.isdigit() and int(season) > 0:  # season 0 = specials: skip
            out[int(anidb)] = (int(season), offset)
    return out


def as_list(value):
    return value if isinstance(value, list) else [value]


def build(fribb: list, scudlee: dict) -> dict:
    result = {}
    skipped_no_season = 0
    for entry in fribb:
        mal = entry.get("mal_id")
        imdbs = [i for i in as_list(entry.get("imdb_id") or []) if isinstance(i, str) and i.startswith("tt")]
        if not isinstance(mal, int) or not imdbs:
            continue
        if (entry.get("type") or "UNKNOWN").upper() not in SERIES_TYPES:
            continue

        # Season: Fribb's explicit tvdb season wins; else ScudLee via anidb.
        season_info = entry.get("season") or {}
        fribb_season = season_info.get("tvdb") if isinstance(season_info, dict) else None
        anidb = entry.get("anidb_id")
        scud = scudlee.get(anidb) if isinstance(anidb, int) else None
        if isinstance(fribb_season, int) and fribb_season > 0:
            season, offset = fribb_season, (scud[1] if scud and scud[0] == fribb_season else 0)
        elif scud:
            season, offset = scud
        else:
            skipped_no_season += 1
            continue  # no confident placement -> wrong-window risk, leave out

        for imdb in imdbs:
            result.setdefault(imdb, []).append([mal, season, offset])

    for rows in result.values():
        rows.sort(key=lambda r: (r[1], r[2], r[0]))
    print(f"shows: {len(result)}, entries: {sum(len(v) for v in result.values())}, "
          f"skipped (no season info): {skipped_no_season}")
    return result


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--fribb", type=Path, help="local anime-list-full.json")
    ap.add_argument("--scudlee", type=Path, help="local anime-list.xml")
    args = ap.parse_args()

    fribb_text = args.fribb.read_text() if args.fribb else fetch(FRIBB_URL, Path("/tmp/fribb-anime-list.json"))
    scud_text = args.scudlee.read_text() if args.scudlee else fetch(SCUDLEE_URL, Path("/tmp/scudlee-anime-list.xml"))

    result = build(json.loads(fribb_text), scudlee_seasons(scud_text))

    # Sanity floor: a broken upstream must never silently ship a tiny map.
    if len(result) < 3000:
        print(f"REFUSING to write: only {len(result)} shows (expected thousands)", file=sys.stderr)
        return 1

    ASSET.parent.mkdir(parents=True, exist_ok=True)
    ASSET.write_text(json.dumps(result, separators=(",", ":"), sort_keys=True))
    print(f"wrote {ASSET} ({ASSET.stat().st_size / 1024:.0f} KB)")

    for probe in ("tt0409591", "tt2560140"):  # Naruto (absolute), AoT (seasonal)
        print(f"  {probe}: {result.get(probe)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
