import json, sys, time, urllib.request, urllib.error, threading
WFM = "http://127.0.0.1:18482"
EXAMPLES = sys.argv[1]
results = []

def call(method, path, body=None, who="Operator"):
    req = urllib.request.Request(WFM + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", "Authorization": "Bearer SYSTEM//" + who})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            data = r.read()
            return r.status, json.loads(data) if data else None
    except urllib.error.HTTPError as e:
        data = e.read()
        return e.code, json.loads(data) if data else None

def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else "  -> " + str(detail)[:400]))

def wait_end(sid, limit=90):
    for _ in range(limit * 5):
        _, s = call("GET", f"/workflowmanager/instance/sessions/{sid}")
        if s["status"] != "running":
            return s
        time.sleep(0.2)
    return s

def start(recipe, params, dispatch=None, version=None, who="LineController"):
    code, s = call("POST", "/workflowmanager/instance/start", {"recipe": recipe, "version": version, "parameters": params, "dispatch": dispatch, "subject": "unit"}, who)
    assert code == 201, (code, s)
    return s["id"]

temp = json.load(open(EXAMPLES + "/temperature-control.recipe.json"))
fork = json.load(open(EXAMPLES + "/shared-tool-tightening.recipe.json"))
# smoke variant: short lease so expiry is observable (lease must exceed every nominal tool-step duration, intent V10)
fast = json.loads(json.dumps(fork)); fast["version"] = "1.0.1"; fast["resources"][0]["leaseDurationSeconds"] = 3
for a in fast["actions"]:
    for s in a["steps"]:
        if s.get("policy", {}).get("nominalDurationSeconds"):
            s["policy"]["nominalDurationSeconds"] = 1

for r in (temp, fork, fast):
    code, body = call("POST", "/workflowmanager/recipe/register", r, "Engineer")
    check(f"register {r['name']}@{r['version']}", code in (200, 201), body)

code, body = call("POST", "/workflowmanager/recipe/validate", {**temp, "actions": [{**temp["actions"][0], "steps": temp["actions"][0]["steps"][::-1]}]}, "Engineer")
check("validator still accepts reordered step list", code == 200 and body["verdict"] == "ACCEPT", body)

# 1 temperature control, push
sid = start("temperature-control", {"threshold": 23.0})
s = wait_end(sid)
_, tr = call("GET", f"/workflowmanager/instance/sessions/{sid}/trace")
check("temperature: completed in 11 transitions (Theorem 1)", s["status"] == "completed" and tr["modelTransitions"] == 11 and tr["withinModel"], (s, tr.get("modelTransitions")))
check("temperature: heating switched on (21.0 < 23.0) via kappa_s", tr["results"]["switch"]["on"] is True, tr["results"])

# 2 three concurrent forklift lines sharing NR7 through the lease
ids = [start("shared-tool-tightening", {"line": f"L{i}", "product": f"P{i}"}) for i in (1, 2, 3)]
ends = [wait_end(i) for i in ids]
counts = [call("GET", f"/workflowmanager/instance/sessions/{i}/trace")[1]["modelTransitions"] for i in ids]
check("forklift x3: all completed, 33 transitions each", all(e["status"] == "completed" for e in ends) and counts == [33, 33, 33], (ends, counts))
_, tr = call("GET", f"/workflowmanager/instance/sessions/{ids[0]}/trace")
check("forklift: AGV steps assigned to WfeAgv (epsilon, eta = agv)", tr["executorAssignment"].get("move") == "WfeAgv" and tr["executorAssignment"].get("back") == "WfeAgv", tr["executorAssignment"])
check("forklift: eta undefined steps not in epsilon (V1)", "program" not in tr["executorAssignment"] and tr["runner"].get("program") in ("WfeA", "WfeAgv"), tr)
stats = json.loads(urllib.request.urlopen("http://127.0.0.1:19000/stats").read())
check("Proposition 1: no overlapping tool use between lines", stats["overlaps"] == 0 and stats["toolUses"] >= 12, stats)

# 3 failure: AGV route blocked for L2 while L1 and L3 queue; the failed holder's lease expires
before = json.loads(urllib.request.urlopen("http://127.0.0.1:19000/stats").read())["overlaps"]
f2 = start("shared-tool-tightening", {"line": "L2", "product": "P2", "block": True}, version="1.0.1")
time.sleep(0.5)
others = [start("shared-tool-tightening", {"line": f"L{i}", "product": f"P{i}"}, version="1.0.1") for i in (1, 3)]
e2 = wait_end(f2)
_, t2 = call("GET", f"/workflowmanager/instance/sessions/{f2}/trace")
check("blocked AGV: S-FailInvoke(move) after 9 transitions", e2["status"] == "failed" and t2["trace"][-1]["rule"] == "S_FAIL_INVOKE" and t2["trace"][-1]["target"] == "move" and t2["modelTransitions"] == 9,
      [(t["rule"], t.get("target")) for t in t2["trace"]])
oe = [wait_end(i) for i in others]
check("blocked AGV: L1 and L3 complete after the lease expires", all(e["status"] == "completed" for e in oe), oe)
after = json.loads(urllib.request.urlopen("http://127.0.0.1:19000/stats").read())["overlaps"]
check("lease variant: no overlap observed", after == before, (before, after))

# 4 pull mode: wfe-pull claims
sid = start("temperature-control", {"threshold": 20.0}, dispatch="pull")
s = wait_end(sid)
_, tr = call("GET", f"/workflowmanager/instance/sessions/{sid}/trace")
check("pull: completed by the claiming executor", s["status"] == "completed" and set(tr["runner"].values()) == {"WfePull"} and tr["results"]["switch"]["on"] is False, (s, tr.get("runner")))

# 5 owner-only abort, 6 compile, 7 management sysop-only
sid = start("shared-tool-tightening", {"line": "L9", "product": "P9"})
code, _ = call("DELETE", f"/workflowmanager/instance/abort/{sid}", who="Intruder")
check("abort: refused for a non-owner (403)", code == 403, code)
code, s = call("DELETE", f"/workflowmanager/instance/abort/{sid}", who="LineController")
check("abort: owner aborts (x-abort, outside the model)", code == 200 and s["status"] == "failed" and "abort" in s["extensions"], s)
code, plan = call("POST", "/workflowmanager/recipe/compile", {"name": "shared-tool-tightening", "version": "1.0.0"}, "Engineer")
check("compile: Choreographer PlanRequestDTO shape", code == 200 and plan["actions"][0]["firstStepNames"] == ["reserve", "program"], plan)
code, _ = call("GET", "/workflowmanager/mgmt/worklog", who="Operator")
code2, wl = call("GET", f"/workflowmanager/mgmt/worklog?sessionId={ids[0]}", who="Sysop")
check("management: sysop-only", code == 403 and code2 == 200 and wl["count"] > 0, (code, code2))

print(f"\n{sum(ok for _, ok in results)}/{len(results)} checks passed")
sys.exit(0 if all(ok for _, ok in results) else 1)
