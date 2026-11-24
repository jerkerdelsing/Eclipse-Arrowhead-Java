"""Simulated plant of the cloud test, as an Arrowhead provider (stdlib only).

WfEquipment logs in to Authentication, registers itself and its services in the ServiceRegistry (generic_https,
CERT_AUTH) and serves them over mutual TLS: only clients with a certificate of the cloud CA get through, and every call
is recorded with the caller's certificate system name. Tool uses of the shared nutrunner (transportTool, configureTool,
tightenFasteners) are checked for overlap between lines (Proposition 1). GET /stats on the plain port 9000 (published to
127.0.0.1 only) returns the counters."""
import json, os, ssl, threading, time, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

NAME = "WfEquipment"
HOST = os.environ.get("EQUIPMENT_HOST", "wf-equipment")
PORT = 9443
CERTS = "/certs"
SR = os.environ.get("SERVICE_REGISTRY_URL", "https://serviceregistry:8443")
SERVICES = os.environ["EQUIPMENT_SERVICES"].split()
PASSWORD = open("/run/secrets/ah.identity-password").read().strip()

lock = threading.Lock()
holder = {"line": None}
stats = {"overlaps": 0, "toolUses": 0, "calls": {}, "callers": {}, "registered": False, "error": None}
TOOL = {"transportTool", "configureTool", "tightenFasteners"}


def client_context():
    c = ssl.create_default_context(ssl.Purpose.SERVER_AUTH, cafile=CERTS + "/ca.crt")
    c.load_cert_chain(CERTS + "/system.crt", CERTS + "/system.key")
    return c


def request(url, payload, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer IDENTITY-TOKEN//" + token
    req = urllib.request.Request(url, data=json.dumps(payload).encode(), headers=headers, method="POST")
    with urllib.request.urlopen(req, context=client_context(), timeout=10) as r:
        body = r.read()
        return json.loads(body) if body else None


def register():
    while True:
        try:
            token = request(os.environ.get("AUTHENTICATION_URL", "https://authentication:8444") + "/authentication/identity/login",
                            {"systemName": NAME, "credentials": {"password": PASSWORD}})["token"]
            request(SR + "/serviceregistry/system-discovery/register",
                    {"metadata": {"role": "workflow-cloud-test-plant"}, "version": "1.0.0", "addresses": [HOST], "deviceName": None}, token)
            for s in SERVICES:
                request(SR + "/serviceregistry/service-discovery/register", {
                    "serviceDefinitionName": s, "version": "1.0.0", "expiresAt": None, "metadata": {"plant": "workflow-cloud-test", "unrestrictedDiscovery": True},
                    "interfaces": [{"templateName": "generic_https", "protocol": "https", "policy": "CERT_AUTH", "properties": {
                        "accessAddresses": [HOST], "accessPort": PORT, "basePath": "/" + s,
                        "operations": {"run": {"method": "POST", "path": "/run"}}}}]}, token)
            with lock:
                stats["registered"], stats["error"] = True, None
            print("registered " + ", ".join(SERVICES), flush=True)
            return
        except Exception as e:  # the cloud may still be starting
            with lock:
                stats["error"] = str(e)
            print("registration failed: " + str(e), flush=True)
            time.sleep(5)


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


def caller(handler):
    cert = handler.connection.getpeercert() if hasattr(handler.connection, "getpeercert") else None
    for rdn in (cert or {}).get("subject", ()):
        for k, v in rdn:
            if k == "commonName":
                return v.split(".")[0]
    return None


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
        if self.path == "/stats" and self.server.server_port == 9000:
            with lock:
                return self.reply(200, stats)
        self.reply(404, {"error": "unknown"})

    def do_POST(self):
        if self.server.server_port != PORT:
            return self.reply(404, {"error": "unknown"})
        service = self.path.strip("/").split("/")[0]
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length") or 0)) or b"null") or {}
        who = caller(self)
        with lock:
            stats["calls"][service] = stats["calls"].get(service, 0) + 1
            stats["callers"].setdefault(service, [])
            if who not in stats["callers"][service]:
                stats["callers"][service].append(who)
        params = body.get("parameters", {}) if isinstance(body, dict) else {}
        line = params.get("line", "?")
        if service == "transportTool" and params.get("block") and body.get("params", {}).get("to") == "station":
            return self.reply(409, {"error": "AGV route blocked"})
        if service in TOOL:
            use_tool(line, 0.3)
        out = {
            "temperatureRead": lambda: {"temperature": 21.0},
            "temperatureEvaluate": lambda: {"heat": body.get("temperature", 0) < body.get("threshold", 0)},
            "heatingSwitch": lambda: {"on": body.get("on")},
            "getProgram": lambda: {"program": "P7"},
            "tightenFasteners": lambda: {"torque": 42.0, "line": line},
        }.get(service, lambda: {"ok": True, "service": service, "line": line})()
        self.reply(200, out)


tls = ThreadingHTTPServer(("0.0.0.0", PORT), H)
ctx = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH, cafile=CERTS + "/ca.crt")
ctx.load_cert_chain(CERTS + "/system.crt", CERTS + "/system.key")
ctx.verify_mode = ssl.CERT_REQUIRED
ctx.minimum_version = ssl.TLSVersion.TLSv1_2
tls.socket = ctx.wrap_socket(tls.socket, server_side=True)
threading.Thread(target=tls.serve_forever, daemon=True).start()
threading.Thread(target=register, daemon=True).start()
ThreadingHTTPServer(("0.0.0.0", 9000), H).serve_forever()
