"""Cloud test driver (stdlib only). Runs on the cloud network (run-cloud-test.sh) and talks to the systems under test
the way any Arrowhead consumer does: mutual TLS with its own certificate and an Authentication identity token bound to
it. Clients: WfClient (workflowRecipe, workflowInstance), WfOperator (workflowInstance), WfIntruder (no rights), Sysop
(management)."""
import json, os, ssl, sys, time, urllib.error, urllib.parse, urllib.request

WFM = "https://workflowmanager:8482"
AUTH = "https://authentication:8444/authentication/identity/login"
SR = "https://serviceregistry:8443/serviceregistry"
STATS = "http://wf-equipment:9000/stats"
CHOREO = "https://choreographer:8454/choreographer"
EXECUTORS = {"WfeAgv", "WfeA", "WfePull", "WfeChor"}
results = []
tokens = {}


def ctx_for(name):
    if name == "Sysop":
        c = ssl.create_default_context(cafile="/cloud-certs/ca.crt")
        c.load_cert_chain("/cloud-certs/sysop.crt", "/cloud-certs/sysop.key")
    else:
        c = ssl.create_default_context(cafile=f"/state/systems/{name}/ca.crt")
        c.load_cert_chain(f"/state/systems/{name}/system.crt", f"/state/systems/{name}/system.key")
    return c


def raw(method, url, body=None, cert="WfClient", token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer IDENTITY-TOKEN//" + token
    req = urllib.request.Request(url, method=method, headers=headers, data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, context=ctx_for(cert), timeout=60) as r:
            data = r.read()
            return r.status, json.loads(data) if data else None
    except urllib.error.HTTPError as e:
        data = e.read()
        try:
            return e.code, json.loads(data) if data else None
        except ValueError:
            return e.code, data.decode(errors="replace")


def token(name):
    if name not in tokens:
        pw = os.environ["SYSOP_PASSWORD"] if name == "Sysop" else open(f"/state/systems/{name}/identity-password").read().strip()
        code, body = raw("POST", AUTH, {"systemName": name, "credentials": {"password": pw}}, cert=name)
        assert code == 200, (name, code, body)
        tokens[name] = body["token"]
    return tokens[name]


def call(method, path, body=None, who="WfClient"):
    return raw(method, WFM + path, body, cert=who, token=token(who))


def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else "  -> " + str(detail)[:600]), flush=True)


def stats():
    with urllib.request.urlopen(STATS, timeout=10) as r:
        return json.loads(r.read())


def wait_end(sid, limit=120):
    s = None
    for _ in range(limit * 4):
        _, s = call("GET", f"/workflowmanager/instance/sessions/{sid}")
        if s["status"] != "running":
            return s
        time.sleep(0.25)
    return s


def start(recipe, params, dispatch=None, version=None, who="WfClient"):
    code, s = call("POST", "/workflowmanager/instance/start", {"recipe": recipe, "version": version, "parameters": params, "dispatch": dispatch, "subject": "unit"}, who)
    assert code == 201, (code, s)
    return s["id"]


def trace(sid):
    return call("GET", f"/workflowmanager/instance/sessions/{sid}/trace")[1]


# 0 the systems in the ServiceRegistry, and the manager's view of the executors
for _ in range(60):
    code, body = raw("POST", SR + "/service-discovery/lookup", {"serviceDefinitionNames": ["workflowExecution"]}, cert="Sysop", token=token("Sysop"))
    found = {e["provider"]["name"] for e in body.get("entries", [])} if code == 200 else set()
    if EXECUTORS <= found and stats().get("registered"):
        break
    time.sleep(2)
check("ServiceRegistry: workflowExecution of WfeAgv, WfeA, WfePull", EXECUTORS <= found, (code, found))
unrestricted = all(str(e.get("metadata", {}).get("unrestrictedDiscovery")).lower() == "true" for e in body.get("entries", []) if e["provider"]["name"] in EXECUTORS)
check("ServiceRegistry: workflowExecution offered with unrestrictedDiscovery", unrestricted, body)
code, body = raw("POST", SR + "/service-discovery/lookup", {"providerNames": ["WorkflowManager"]}, cert="Sysop", token=token("Sysop"))
services = {e["serviceDefinition"]["name"] for e in body.get("entries", [])} if code == 200 else set()
need = {"workflowRecipe", "workflowInstance", "workflowWorkItem", "workflowContext", "workflowLease", "workflowManagement"}
check("ServiceRegistry: WorkflowManager services, all generic_https/CERT_AUTH", need <= services and all(
    i["policy"] == "CERT_AUTH" and i["templateName"] == "generic_https" for e in body["entries"] for i in e["interfaces"]), (code, services))
check("plant WfEquipment registered (mutual TLS provider)", stats().get("registered"), stats())
code, ex = call("GET", "/workflowmanager/mgmt/executors", who="Sysop")
names = {e["name"] for e in ex.get("entries", [])} if code == 200 else set()
check("WorkflowManager discovers the executors in the ServiceRegistry (RESTRICTED discovery)", EXECUTORS <= names and all(
    e["source"] == "registry" for e in ex["entries"]), (code, ex))

# 1 security: authentication, token binding, ConsumerAuthorization at the provider
temp = json.load(open("/examples/temperature-control.recipe.json"))
fork = json.load(open("/examples/shared-tool-tightening.recipe.json"))
fast = json.loads(json.dumps(fork)); fast["version"] = "1.0.1"; fast["resources"][0]["leaseDurationSeconds"] = 3
for a in fast["actions"]:
    for s in a["steps"]:
        if s.get("policy", {}).get("nominalDurationSeconds"):
            s["policy"]["nominalDurationSeconds"] = 1
code, _ = raw("POST", WFM + "/workflowmanager/recipe/register", temp, cert="WfClient", token=None)
check("no identity token: 401", code == 401, code)
code, _ = raw("POST", WFM + "/workflowmanager/recipe/register", temp, cert="WfOperator", token=token("WfClient"))
check("WfClient's token on WfOperator's certificate: refused (token bound to certificate)", code in (401, 403), code)
code, _ = call("POST", "/workflowmanager/recipe/register", temp, "WfIntruder")
check("ConsumerAuthorization: WfIntruder may not register recipes (403)", code == 403, code)
code, _ = call("POST", "/workflowmanager/recipe/register", temp, "WfOperator")
check("ConsumerAuthorization: WfOperator may not register recipes (403)", code == 403, code)
code, _ = raw("POST", "https://wfe-a:8483/workflowexecutor/execution/start", {"workItemId": "x"}, cert="WfClient", token=token("WfClient"))
check("ConsumerAuthorization at the executor: WfClient may not start work items (403)", code == 403, code)

for r in (temp, fork, fast):
    code, body = call("POST", "/workflowmanager/recipe/register", r)
    check(f"WfClient registers {r['name']}@{r['version']}", code in (200, 201), body)

# 2 temperature control, push: DSO orchestrates the plant service for each executor
sid = start("temperature-control", {"threshold": 23.0})
s = wait_end(sid)
tr = trace(sid)
check("temperature: completed in 11 transitions (Theorem 1)", s["status"] == "completed" and tr["modelTransitions"] == 11 and tr["withinModel"], (s, tr))
check("temperature: heating switched on (21.0 < 23.0) via kappa_s", tr["results"]["switch"]["on"] is True, tr["results"])
providers = {b.get("provider") for b in tr["bindings"].values()}
check("temperature: every step bound by DynamicServiceOrchestration to WfEquipment", providers == {"WfEquipment"}, tr["bindings"])
callers = set(sum((stats()["callers"].get(x, []) for x in ("temperatureRead", "temperatureEvaluate", "heatingSwitch")), []))
check("temperature: the plant was called by executors with their own certificates", callers and callers <= EXECUTORS, callers)

# 3 three concurrent forklift lines sharing NR7 through the lease
ids = [start("shared-tool-tightening", {"line": f"L{i}", "product": f"P{i}"}) for i in (1, 2, 3)]
ends = [wait_end(i) for i in ids]
counts = [trace(i)["modelTransitions"] for i in ids]
check("forklift x3: all completed, 33 transitions each", all(e["status"] == "completed" for e in ends) and counts == [33, 33, 33], (ends, counts))
tr = trace(ids[0])
check("forklift: AGV steps assigned to WfeAgv (eta = agv)", tr["executorAssignment"].get("move") == "WfeAgv" and tr["executorAssignment"].get("back") == "WfeAgv", tr["executorAssignment"])
st = stats()
check("Proposition 1: no overlapping tool use between lines", st["overlaps"] == 0 and st["toolUses"] >= 12, st)

# 4 failure: AGV route blocked for L2; the failed holder's lease expires and L1, L3 complete
before = stats()["overlaps"]
f2 = start("shared-tool-tightening", {"line": "L2", "product": "P2", "block": True}, version="1.0.1")
time.sleep(0.5)
others = [start("shared-tool-tightening", {"line": f"L{i}", "product": f"P{i}"}, version="1.0.1") for i in (1, 3)]
e2 = wait_end(f2)
t2 = trace(f2)
check("blocked AGV: S-FailInvoke(move) after 9 transitions", e2["status"] == "failed" and t2["trace"][-1]["rule"] == "S_FAIL_INVOKE"
      and t2["trace"][-1]["target"] == "move" and t2["modelTransitions"] == 9, [(t["rule"], t.get("target")) for t in t2["trace"]])
oe = [wait_end(i) for i in others]
check("blocked AGV: L1 and L3 complete after the lease expires", all(e["status"] == "completed" for e in oe), oe)
check("lease variant: no overlap observed", stats()["overlaps"] == before, stats())

# 5 pull mode
sid = start("temperature-control", {"threshold": 20.0}, dispatch="pull")
s = wait_end(sid)
tr = trace(sid)
check("pull: completed by WfePull (claims through workflowWorkItem)", s["status"] == "completed" and set(tr["runner"].values()) == {"WfePull"}
      and tr["results"]["switch"]["on"] is False, (s, tr.get("runner")))

# 6 owner-only abort, compile, management
sid = start("shared-tool-tightening", {"line": "L9", "product": "P9"})
code, _ = call("DELETE", f"/workflowmanager/instance/abort/{sid}", who="WfOperator")
check("abort: WfOperator (authorized, not the owner) refused (403)", code == 403, code)
code, s = call("DELETE", f"/workflowmanager/instance/abort/{sid}")
check("abort: the owner WfClient aborts (x-abort, outside the model)", code == 200 and s["status"] == "failed" and "abort" in s["extensions"], s)
code, plan = call("POST", "/workflowmanager/recipe/compile", {"name": "shared-tool-tightening", "version": "1.0.0"})
check("compile: Choreographer PlanRequestDTO shape", code == 200 and plan["actions"][0]["firstStepNames"] == ["reserve", "program"], plan)
code, _ = call("GET", "/workflowmanager/mgmt/worklog", who="WfClient")
code2, wl = call("GET", f"/workflowmanager/mgmt/worklog?sessionId={ids[0]}", who="Sysop")
check("management: sysop-only", code == 403 and code2 == 200 and wl["count"] > 0, (code, code2))

# 7 Choreographer compatibility (option A): the cloud's running Choreographer drives WfeChor with a compiled plan.
# WfeChor registered itself (choreographerExecutor.register); the Choreographer asks service-info in camelCase, has
# DynamicServiceOrchestration orchestrate for WfeChor (push), calls start, and WfeChor reports notify-step-done.
def choreo(method, path, body=None):
    return raw(method, CHOREO + path, body, cert="Sysop", token=token("Sysop"))


for _ in range(30):
    code, ex = choreo("GET", "/mgmt/executors")
    chor = [e for e in (ex or {}).get("entries", []) if e["name"] == "WfeChor"] if code == 200 else []
    if chor and len(chor[0]["serviceDefinitions"]) >= 3:
        break
    time.sleep(2)
defs = {d["serviceDefinitionName"] for d in chor[0]["serviceDefinitions"]} if chor else set()
check("Choreographer: WfeChor registered itself as executor of the three temperature services", chor and chor[0]["basePath"] == "/workflowexecutor/choreographer"
      and {"temperatureRead", "temperatureEvaluate", "heatingSwitch"} <= defs, (code, ex))
code, plan = call("POST", "/workflowmanager/recipe/compile", {"name": "temperature-control", "version": "1.0.0"})
plan_request = {"name": "wf-cloud-temperature-control", "firstActionName": plan["firstActionName"], "actions": plan["actions"]}
code, created = choreo("POST", "/mgmt/plans", plan_request)
check("Choreographer accepts the compiled plan (create-plan)", code == 201, (code, created))
code, run = choreo("POST", "/mgmt/sessions/start", {"sessions": [{"planId": created.get("id") if isinstance(created, dict) else None, "quantity": 1}]})
csid = run["entries"][0]["sessionId"] if code == 201 and run.get("entries") and run["entries"][0].get("sessionId") else None
session = None
for _ in range(240):
    if csid is None:
        break
    code, sessions = choreo("GET", f"/mgmt/sessions?page=0&size=100&planId={created['id']}")
    session = next((x for x in sessions.get("entries", []) if x["id"] == csid), None) if code == 200 else None
    if session and session["status"] not in ("INITIATED", "RUNNING"):
        break
    time.sleep(0.5)
code, steps = choreo("GET", f"/mgmt/session-steps?page=0&size=100&sessionId={csid}") if csid else (None, {})
steps = steps.get("entries", []) if isinstance(steps, dict) else []
code, worklog = choreo("GET", f"/mgmt/worklog?page=0&size=100&sessionId={csid}") if csid else (None, {})
check("Choreographer session of the compiled plan: DONE", session and session["status"] == "DONE",
      (run, session, [(x["stepName"], x["status"], x["message"]) for x in steps], [w.get("message") for w in (worklog or {}).get("entries", [])][-8:]))
check("Choreographer: read, evaluate, switch all DONE on WfeChor", len(steps) == 3 and all(x["status"] == "DONE" and x["executorName"] == "WfeChor" for x in steps),
      [(x["stepName"], x["status"], x["executorName"], x["message"]) for x in steps])
callers = {svc: stats()["callers"].get(svc, []) for svc in ("temperatureRead", "temperatureEvaluate", "heatingSwitch")}
check("Choreographer: DSO orchestrated the plant for WfeChor, which called it with its own certificate", all("WfeChor" in c for c in callers.values()), callers)
code, _ = raw("POST", "https://wfe-chor:8483/workflowexecutor/choreographer/start", {"sessionId": 1, "sessionStepId": 1, "quantity": 1},
              cert="WfClient", token=token("WfClient"))
check("WfeChor's Choreographer interface refuses other systems (403)", code == 403, code)

# 8 WorkflowConfigurator: configured local clouds, their services, and a guarded workflow stored in the WorkflowManager.
# The browser console (plain HTTP, password, local Host only) is driven like the editor does; the Arrowhead connector
# (mutual TLS, ConsumerAuthorization) like another engineering tool.
CONSOLE = "http://workflowconfigurator:8485"
console_cookie = []


def console(method, path, body=None, cookie=True):
    headers = {"Content-Type": "application/json", "Host": "localhost:8485"}
    if cookie and console_cookie:
        headers["Cookie"] = console_cookie[0]
    req = urllib.request.Request(CONSOLE + path, method=method, headers=headers, data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            if r.headers.get("Set-Cookie"):
                console_cookie[:] = [r.headers["Set-Cookie"].split(";")[0]]
            data = r.read()
            return r.status, json.loads(data) if data and r.headers.get_content_type() == "application/json" else data
    except urllib.error.HTTPError as e:
        data = e.read()
        try:
            return e.code, json.loads(data)
        except ValueError:
            return e.code, data.decode(errors="replace")


API_PATH = "/workflowconfigurator/api"
code, _ = console("GET", API_PATH + "/clouds", cookie=False)
code2, _ = console("POST", API_PATH + "/session", {"password": "wrong"}, cookie=False)
check("configurator console: login required (401), wrong password refused", code == 401 and code2 == 401, (code, code2))
code, page = console("GET", "/index.html", cookie=False)
check("configurator console: editor page served", code == 200 and b"WorkflowConfigurator" in page, code)
code, _ = console("POST", API_PATH + "/session", {"password": open("/state/console-password").read().strip()}, cookie=False)
check("configurator console: login with the console password", code == 200 and console_cookie, code)
code, clouds = console("GET", API_PATH + "/clouds")
ids = [c["id"] for c in clouds.get("entries", [])] if code == 200 else []
sources = {(x["system"], x["operation"]): x["available"] for x in clouds.get("sources", [])} if code == 200 else {}
check("configurator: configured local clouds from ServiceRegistry, LocalCloudConfigurator and SoLocalCloudConfigurator",
      "live" in ids and any(i.startswith("lcc:") for i in ids) and any(i.startswith("solc:") for i in ids) and all(sources.values()), (code, ids, clouds.get("sources")))
code, services = console("GET", API_PATH + "/clouds/live/services")
offers = {(e["provider"], e["serviceDefinition"]): e for e in services.get("entries", [])} if code == 200 else {}
mode = "discovery fallback" if services.get("note") else "serviceRegistryManagement"
print(f"     (running cloud's services read by {mode}: {services.get('note', '')})", flush=True)
check(f"configurator: microservices of the running cloud, with operations (WfEquipment temperatureRead run; via {mode})",
      ("WfEquipment", "temperatureRead") in offers and offers[("WfEquipment", "temperatureRead")]["operations"] == ["run"]
      and not any(sd == "sysMonitor" for _, sd in offers), (code, sorted(offers)[:20]))
design = next((c for c in clouds.get("entries", []) if c["id"].startswith("lcc:")), None)
code, dsv = console("GET", API_PATH + "/clouds/" + urllib.parse.quote(design["id"], safe=":") + "/services") if design else (None, {})
names = {s["name"] for s in design["systems"]} if design else set()
check("configurator: a LocalCloudConfigurator design lists only its own systems' services",
      code == 200 and all(e["provider"] in names for e in dsv.get("entries", [])), (code, design and design["name"], [e["provider"] for e in dsv.get("entries", [])][:10]))
code, ex = console("GET", API_PATH + "/executors")
check("configurator: executors with capabilities for η(s)", code == 200 and any(e["name"] == "WfeAgv" and "agv" in e["capabilities"] for e in ex.get("entries", [])), (code, ex))

branching = json.load(open("/examples/temperature-branching.recipe.json"))
branching["name"] = "wfc-branching"
broken = json.loads(json.dumps(branching))
broken["actions"][0]["steps"][1]["guards"]["heatOff"] = "${result.confirm.on} == true"
code, res = console("POST", API_PATH + "/recipes", broken)
check("configurator store: a guard reading a later step is refused (422, G03) and not stored",
      code == 422 and not res["stored"] and any(f["code"] == "G03" for f in res["report"]["findings"]), (code, res))
code, res = console("POST", API_PATH + "/recipes", branching)
check("configurator store: the guarded workflow is stored in the WorkflowManager (201)", code == 201 and res["stored"] and res["created"], (code, res))
code2, res2 = console("POST", API_PATH + "/recipes", branching)
changed = json.loads(json.dumps(branching)); changed["description"] = "changed"
code3, res3 = console("POST", API_PATH + "/recipes", changed)
check("configurator store: same content again is 200, different content under the same version 409", code2 == 200 and code3 == 409, (code2, res2, code3, res3))
code, stored = call("GET", "/workflowmanager/recipe/recipes/wfc-branching/1.0.0")
check("WorkflowManager holds the stored workflow with its guards", code == 200 and stored["recipe"]["actions"][0]["steps"][1].get("guards", {}).get("heatOn"), (code, stored))

for threshold, taken, skipped in ((23.0, "heatOn", "heatOff"), (20.0, "heatOff", "heatOn")):
    sid = start("wfc-branching", {"threshold": threshold})
    s = wait_end(sid)
    tr = trace(sid)
    rules = [(t["rule"], t.get("target")) for t in tr["trace"]]
    check(f"guarded session (threshold {threshold}): {taken} runs, {skipped} skipped (x-skip), confirm joins",
          s["status"] == "completed" and ("X_SKIP", skipped) in rules and ("S_DONE", taken) in rules and ("S_DONE", "confirm") in rules
          and "guard" in s.get("extensions", []) and not tr["withinModel"], (s, rules))

code, _ = raw("GET", "https://workflowconfigurator:8484" + API_PATH + "/clouds", cert="WfIntruder", token=token("WfIntruder"))
code2, body2 = raw("GET", "https://workflowconfigurator:8484" + API_PATH + "/recipes", cert="WfClient", token=token("WfClient"))
check("configurator Arrowhead connector: ConsumerAuthorization refuses WfIntruder (403), admits WfClient", code == 403 and code2 == 200, (code, code2, body2))
code, _ = raw("GET", "https://workflowconfigurator:8484/index.html", cert="WfClient", token=token("WfClient"))
check("configurator Arrowhead connector: no console pages there (404)", code == 404, code)

print(f"\n{sum(ok for _, ok in results)}/{len(results)} checks passed", flush=True)
sys.exit(0 if all(ok for _, ok in results) else 1)
