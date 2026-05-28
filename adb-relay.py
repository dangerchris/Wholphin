#!/usr/bin/env python3
"""
ADB relay server for TekSavvy TV channel tuning.
Runs on server.local, receives HTTP requests from Wholphin on Fire TV,
and sends ADB keystroke commands to the SHIELD TV.

Usage:
    python3 adb-relay.py [--host 0.0.0.0] [--port 8765] [--shield 192.168.1.211:5555]
"""

import argparse
import json
import subprocess
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse

TEKSAVVY_ACTIVITY = "com.schange.android.tv.cview.newteksavvy/com.nitrox5.MainActivity"


class AdbRelayHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        print(f"[{self.log_date_time_string()}] {format % args}")

    def do_POST(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)

        if parsed.path == "/tune":
            channel = params.get("channel", [None])[0]
            if not channel:
                self._json_response(400, {"status": "error", "message": "Missing channel parameter"})
                return

            shield_addr = self.server.shield_addr
            adb = ["adb", "-s", shield_addr]

            try:
                # Foreground TekSavvy app first
                subprocess.run(
                    adb + ["shell", "am", "start", "-n", TEKSAVVY_ACTIVITY],
                    capture_output=True,
                    timeout=10,
                )
                # Small delay to let the app come to foreground
                time.sleep(0.5)

                # Send each digit with 0.1s delay
                for i, digit in enumerate(channel):
                    if digit.isdigit():
                        subprocess.run(
                            adb + ["shell", "input", "keyevent", f"KEYCODE_{digit}"],
                            capture_output=True,
                            timeout=5,
                        )
                        if i < len(channel) - 1:
                            time.sleep(0.1)

                # Confirm channel with enter
                time.sleep(0.1)
                subprocess.run(
                    adb + ["shell", "input", "keyevent", "KEYCODE_DPAD_CENTER"],
                    capture_output=True,
                    timeout=5,
                )

                self._json_response(200, {"status": "ok", "channel": channel})
            except subprocess.TimeoutExpired as e:
                self._json_response(500, {"status": "error", "message": f"ADB command timed out: {e}"})
            except Exception as e:
                self._json_response(500, {"status": "error", "message": str(e)})

        elif parsed.path == "/launch":
            shield_addr = self.server.shield_addr
            try:
                subprocess.run(
                    ["adb", "-s", shield_addr, "shell", "am", "start", "-n", TEKSAVVY_ACTIVITY],
                    capture_output=True,
                    timeout=10,
                )
                self._json_response(200, {"status": "ok"})
            except Exception as e:
                self._json_response(500, {"status": "error", "message": str(e)})

        else:
            self._json_response(404, {"status": "error", "message": "Not found"})

    def do_GET(self):
        if urlparse(self.path).path == "/health":
            self._json_response(200, {"status": "ok"})
        else:
            self._json_response(404, {"status": "error", "message": "Not found"})

    def _json_response(self, code, body):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(body).encode())


class AdbRelayServer(HTTPServer):
    def __init__(self, server_address, handler_class, shield_addr):
        super().__init__(server_address, handler_class)
        self.shield_addr = shield_addr


def main():
    parser = argparse.ArgumentParser(description="ADB relay for TekSavvy TV channel tuning")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind to")
    parser.add_argument("--port", type=int, default=8765, help="Port to listen on")
    parser.add_argument("--shield", default="192.168.1.211:5555", help="SHIELD TV ADB address")
    args = parser.parse_args()

    server = AdbRelayServer((args.host, args.port), AdbRelayHandler, args.shield)
    print(f"ADB relay listening on {args.host}:{args.port}")
    print(f"  SHIELD TV: {args.shield}")
    print(f"  Endpoints:")
    print(f"    POST /tune?channel=403  - Tune to channel")
    print(f"    POST /launch            - Launch TekSavvy app")
    print(f"    GET  /health            - Health check")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()


if __name__ == "__main__":
    main()
