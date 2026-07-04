#!/usr/bin/env python3
"""Local Stremio-protocol fixture addon for emulator playback testing.

Serves a one-movie catalog (Big Buck Bunny) and the video file itself with
HTTP Range support (ExoPlayer seek/resume needs it). The emulator reaches
the host Mac at 10.0.2.2; install http://10.0.2.2:8090/manifest.json via the
app's Add-addon screen (upserts over any older "Local Test Addon").

Setup (one-time): download the video next to this script or point
TEST_VIDEO at one. Known-good source (Google's gtv-videos-bucket 403s now):
  curl -L -o tools/bbb_720p.mov \
    https://download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_720p_h264.mov

Run: python3 tools/test_addon_server.py
"""
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

VIDEO = os.environ.get(
    "TEST_VIDEO", os.path.join(os.path.dirname(__file__), "bbb_720p.mov")
)
BASE = "http://10.0.2.2:8090"

MANIFEST = {
    "id": "dev.openstream.localtest",
    "version": "1.0.0",
    "name": "Local Test Addon",
    "description": "Throwaway fixture addon for playback testing",
    "resources": ["catalog", "meta", "stream"],
    "types": ["movie"],
    "idPrefixes": ["bbb"],
    "catalogs": [{"type": "movie", "id": "test", "name": "Local Test"}],
}

META = {
    "id": "bbb1",
    "type": "movie",
    "name": "Big Buck Bunny",
    "poster": f"{BASE}/poster.jpg",
    "description": "Blender Foundation open movie (test fixture).",
    "releaseInfo": "2008",
    "runtime": "10 min",
}

STREAMS = [{"name": "Local 720p H.264", "title": "bbb_720p.mov served from host", "url": f"{BASE}/video.mov"}]


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def send_json(self, obj):
        body = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802
        p = self.path
        if p == "/manifest.json":
            self.send_json(MANIFEST)
        elif p == "/catalog/movie/test.json":
            self.send_json({"metas": [META]})
        elif p == "/meta/movie/bbb1.json":
            self.send_json({"meta": META})
        elif re.fullmatch(r"/stream/movie/bbb.*\.json", p):
            self.send_json({"streams": STREAMS})
        elif p == "/video.mov":
            self.serve_video()
        else:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()

    def serve_video(self):
        size = os.path.getsize(VIDEO)
        rng = self.headers.get("Range")
        start, end = 0, size - 1
        if rng:
            m = re.match(r"bytes=(\d*)-(\d*)", rng)
            if m:
                if m.group(1):
                    start = int(m.group(1))
                if m.group(2):
                    end = min(int(m.group(2)), size - 1)
        length = end - start + 1
        self.send_response(206 if rng else 200)
        self.send_header("Content-Type", "video/quicktime")
        self.send_header("Accept-Ranges", "bytes")
        if rng:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(length))
        self.end_headers()
        with open(VIDEO, "rb") as f:
            f.seek(start)
            remaining = length
            while remaining > 0:
                chunk = f.read(min(65536, remaining))
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except BrokenPipeError:
                    break  # player seeked/closed; normal
                remaining -= len(chunk)

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} {fmt % args}", flush=True)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8090), Handler).serve_forever()
