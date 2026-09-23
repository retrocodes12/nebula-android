"""A stream add-on the emulator reaches at http://10.0.2.2:8799/manifest.json (the CI runner itself): every tt id gets
one row, a clear public test stream, so the walk can open a real stream row and play it (screens.yml)."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

STREAM = 'https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd'
MANIFEST = {'id': 'org.nebula.gate', 'version': '1.0.0', 'name': 'Gate Streams', 'description': 'test streams',
            'resources': ['stream'], 'types': ['movie', 'series'], 'idPrefixes': ['tt'], 'catalogs': []}


class H(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith('/manifest.json'): body = MANIFEST
        elif self.path.startswith('/stream/'): body = {'streams': [{'name': 'Gate', 'title': 'Test stream', 'url': STREAM}]}
        else:
            self.send_response(404); self.end_headers(); return
        data = json.dumps(body).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json'); self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)

    def log_message(self, *a):
        pass


ThreadingHTTPServer(('0.0.0.0', 8799), H).serve_forever()
