"""Simple HTTP proxy that adds Content-Type for iptv-epg.org images."""
from http.server import HTTPServer, BaseHTTPRequestHandler
import urllib.request

BASE = "https://iptv-epg.org"

class Proxy(BaseHTTPRequestHandler):
    def do_GET(self):
        url = BASE + self.path
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = resp.read()
            content_type = "image/png" if self.path.lower().endswith(".png") else "image/png"
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", len(data))
            self.send_header("Cache-Control", "public, max-age=86400")
            self.end_headers()
            self.wfile.write(data)
        except Exception as e:
            self.send_error(502, str(e))

    def log_message(self, format, *args):
        pass

HTTPServer(('127.0.0.1', 8099), Proxy).serve_forever()
