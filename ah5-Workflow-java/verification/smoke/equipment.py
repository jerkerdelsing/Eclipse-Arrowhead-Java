"""Simulated plant for the WorkflowManager/WorkflowExecutor smoke test (stdlib only).

Every service is POST /<service>/run with a JSON body. Tool uses of the shared nutrunner (transport-tool,
configure-tool, tighten-fasteners) are checked for overlap between lines (Proposition 1)."""
import json, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

lock = threading.Lock()
holder = {"line": None}
stats = {"overlaps": 0, "toolUses": 0, "calls": {}}
TOOL = {"transport-tool", "configure-tool", "tighten-fasteners"}

def use_tool(line, seconds):
    with lock:
        stats["toolUses"] += 1
        if holder["line"] is not None and holder["line"] != line:
            stats["overlaps"] += 1
        holder["line"] = line
    time.sleep(seconds)
    with lock:
        if holder["line"] == line:
            holder["line"] = None

class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def reply(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/stats":
            with lock:
                return self.reply(200, stats)
        self.reply(404, {"error": "unknown"})

    def do_POST(self):
        service = self.path.strip("/").split("/")[0]
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length") or 0)) or b"null") or {}
        with lock:
            stats["calls"][service] = stats["calls"].get(service, 0) + 1
        params = body.get("parameters", {}) if isinstance(body, dict) else {}
        line = params.get("line", "?")
        if service == "transport-tool" and params.get("block") and body.get("params", {}).get("to") == "station":
            return self.reply(409, {"error": "AGV route blocked"})
        if service in TOOL:
            use_tool(line, 0.3)
        out = {
            "temperature-read": lambda: {"temperature": 21.0},
            "temperature-evaluate": lambda: {"heat": body.get("temperature", 0) < body.get("threshold", 0)},
            "heating-switch": lambda: {"on": body.get("on")},
            "get-program": lambda: {"program": "P7"},
            "tighten-fasteners": lambda: {"torque": 42.0, "line": line},
        }.get(service, lambda: {"ok": True, "service": service, "line": line})()
        self.reply(200, out)

ThreadingHTTPServer(("0.0.0.0", 9000), H).serve_forever()
