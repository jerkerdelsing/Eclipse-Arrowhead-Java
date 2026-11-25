"""Operator side of the cloud test (stdlib only). Runs in a container on the cloud network (run-cloud-test.sh), as Sysop.

  cloudctl.py setup      Authentication identities, ConsumerAuthorization policies, authorization.verify extended,
                         shared policies extended (choreographerExecutor; localCloudConfiguration, soLocalCloudConfiguration
                         and serviceRegistryManagement for the WorkflowConfigurator; the stored policies are saved first)
  cloudctl.py teardown   identities, sessions, policies and ServiceRegistry entries of the test systems removed,
                         authorization.verify restored to the cloud's declared list, the shared policies restored
                         to the saved ones, the test plans (with their sessions) and executors removed from the Choreographer

Inputs: /state (the .state folder), /backup (writable), /cloud-certs (ca.crt, sysop.crt, sysop.key), environment
SYSOP_PASSWORD, VERIFIERS (the cloud's declared verify list, JSON), SYSTEMS, EXECUTORS, CHOREOGRAPHER_EXECUTORS,
EQUIPMENT_SERVICES."""
import json, os, ssl, sys, urllib.parse, urllib.request, urllib.error

AUTH = "https://authentication:8444/authentication"
CAU = "https://consumerauthorization:8445/consumerauthorization/authorization/mgmt"
SR = "https://serviceregistry:8443/serviceregistry"
CHOREO = "https://choreographer:8454/choreographer/mgmt"
CHOREO_POLICY_ID = "MGMT|LOCAL|Choreographer|SERVICE_DEF|choreographerExecutor"
# test plans in the Choreographer: names start with this prefix (compared without case and separators)
PLAN_PREFIX = "wfcloud"
SYSTEMS = [e.split("|")[0] for e in os.environ["SYSTEMS"].split()]
EXECUTORS = json.loads(os.environ["EXECUTORS"])
CHOREOGRAPHER_EXECUTORS = json.loads(os.environ["CHOREOGRAPHER_EXECUTORS"])
EQUIPMENT = os.environ["EQUIPMENT_SERVICES"].split()
VERIFIERS = json.loads(os.environ["VERIFIERS"])
ctx = ssl.create_default_context(cafile="/cloud-certs/ca.crt")
ctx.load_cert_chain("/cloud-certs/sysop.crt", "/cloud-certs/sysop.key")
token = None


def call(method, url, body=None, auth=True):
    headers = {"Content-Type": "application/json"}
    if auth:
        headers["Authorization"] = "Bearer IDENTITY-TOKEN//" + token
    req = urllib.request.Request(url, method=method, headers=headers, data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=20) as r:
            data = r.read()
            return r.status, (json.loads(data) if data else None)
    except urllib.error.HTTPError as e:
        data = e.read()
        try:
            return e.code, json.loads(data)
        except ValueError:
            return e.code, data.decode(errors="replace")


def login():
    global token
    code, body = call("POST", AUTH + "/identity/login", {"systemName": "Sysop", "credentials": {"password": os.environ["SYSOP_PASSWORD"]}}, auth=False)
    if code != 200:
        sys.exit(f"Sysop login failed: {code} {body}")
    token = body["token"]


def wl(names):
    return {"policyType": "WHITELIST", "policyList": names}


def policies():
    p = lambda prov, target, desc, names: {"provider": prov, "targetType": "SERVICE_DEF", "target": target, "description": desc,
                                           "defaultPolicy": wl(names), "scopedPolicies": {}}
    out = [p("WorkflowManager", "workflowRecipe", "Workflow cloud test: engineering client and the WorkflowConfigurator", ["WfClient", "WorkflowConfigurator"]),
           p("WorkflowConfigurator", "workflowConfiguration", "Workflow cloud test: engineering tools use the configurator", ["WfClient"]),
           p("WorkflowManager", "workflowInstance", "Workflow cloud test: clients start sessions", ["WfClient", "WfOperator"]),
           p("WorkflowManager", "workflowWorkItem", "Workflow cloud test: executors claim and report", EXECUTORS),
           p("WorkflowManager", "workflowContext", "Workflow cloud test: executors read the session context", EXECUTORS),
           p("WorkflowManager", "workflowLease", "Workflow cloud test: executors lease shared resources", EXECUTORS)]
    out += [p(e, "workflowExecution", "Workflow cloud test: the WorkflowManager dispatches work items", ["WorkflowManager"]) for e in EXECUTORS]
    out += [p("WfEquipment", s, "Workflow cloud test: plant service used by the executors", EXECUTORS) for s in EQUIPMENT]
    return out


def verify_policy(names):
    return {"provider": "ConsumerAuthorization", "targetType": "SERVICE_DEF", "target": "authorization",
            "description": "Providers verify their consumers (orchestrated authorization.verify)",
            "defaultPolicy": wl(["Sysop"]), "scopedPolicies": {"verify": wl(names)}}


def pid(p):
    return f"MGMT|LOCAL|{p['provider']}|{p['targetType']}|{p['target']}"


def revoke(instance_id):
    call("DELETE", CAU + "/revoke?instanceIds=" + urllib.parse.quote(instance_id, safe=""))


def apply(p):
    revoke(pid(p))
    code, body = call("POST", CAU + "/grant", {"list": [p]})
    ok = isinstance(body, dict) and any(e.get("instanceId") == pid(p) for e in body.get("entries", []))
    print(("  policy " if ok else "  FAILED policy ") + pid(p) + ("" if ok else f": {code} {body}"))
    if not ok:
        sys.exit(1)


def stored_policy(instance_id):
    code, body = call("POST", CAU + "/query", {"pagination": {"page": 0, "size": 1000}, "level": "MGMT"})
    if code != 200:
        sys.exit(f"policy query failed: {code} {body}")
    return next((e for e in body.get("entries", []) if e.get("instanceId") == instance_id), None)


def as_grant(e):
    """A stored policy as a grant request (without the bookkeeping fields)."""
    return {k: e[k] for k in ("provider", "targetType", "target", "description", "defaultPolicy", "scopedPolicies") if e.get(k) is not None}


# Shared cloud policies the test extends: id -> what it adds. The stored policy is saved in /backup before the first change
# and restored at teardown; a policy that did not exist is revoked again. "default" extends the default whitelist,
# "scoped" the whitelists of the named operations.
WFC = "WorkflowConfigurator"
SHARED = {
    CHOREO_POLICY_ID: {"default": CHOREOGRAPHER_EXECUTORS, "description": "Executor systems that perform plan steps"},
    "MGMT|LOCAL|LocalCloudConfigurator|SERVICE_DEF|localCloudConfiguration": {"scoped": {op: [WFC] for op in ("catalog", "list-configurations", "get-configuration")}},
    "MGMT|LOCAL|SoLocalCloudConfigurator|SERVICE_DEF|soLocalCloudConfiguration": {"scoped": {op: [WFC] for op in ("list-designs", "get-design")}},
    "MGMT|LOCAL|ServiceRegistry|SERVICE_DEF|serviceRegistryManagement": {"scoped": {op: [WFC] for op in ("system-query", "service-query")},
                                                                       "description": "Workflow cloud test: the WorkflowConfigurator lists systems and services"},
}


def backup_file(instance_id):
    return "/backup/" + instance_id.replace("|", "_") + ".json"


def merge(names, extra):
    return names + [n for n in extra if n not in names]


def extend_shared(instance_id, spec):
    stored = stored_policy(instance_id)
    path = backup_file(instance_id)
    if not os.path.exists(path):  # a second setup without teardown must not save the extended policy
        with open(path, "w") as f:
            json.dump(as_grant(stored) if stored else None, f)
    base = json.load(open(path))
    _, _, provider, target_type, target = instance_id.split("|")
    p = json.loads(json.dumps(base)) if base else {"provider": provider, "targetType": target_type, "target": target,
                                                    "description": spec.get("description", "Workflow cloud test"), "defaultPolicy": wl(["Sysop"])}
    if "default" in spec:
        p["defaultPolicy"]["policyList"] = merge(p["defaultPolicy"]["policyList"], spec["default"])
    for op, names in spec.get("scoped", {}).items():
        scoped = p.setdefault("scopedPolicies", {})
        scoped[op] = wl(merge(scoped[op]["policyList"], names)) if op in scoped else wl(merge(list(p["defaultPolicy"]["policyList"]), names))
    apply(p)


def restore_shared(instance_id, spec):
    path = backup_file(instance_id)
    if os.path.exists(path):
        base = json.load(open(path))
        if base:
            apply(base)
        else:
            revoke(instance_id)
            print("  policy " + instance_id + " revoked (it did not exist before)")
        os.remove(path)
        return
    stored = stored_policy(instance_id)  # no backup: only take the test systems out of the stored policy
    if stored:
        p = as_grant(stored)
        added = set(spec.get("default", [])) | {n for names in spec.get("scoped", {}).values() for n in names}
        p["defaultPolicy"]["policyList"] = [n for n in p["defaultPolicy"]["policyList"] if n not in added]
        for v in (p.get("scopedPolicies") or {}).values():
            v["policyList"] = [n for n in v["policyList"] if n not in added]
        apply(p)


def choreographer_cleanup():
    """Test plans (with their sessions) and test executors out of the Choreographer; also before setup, after a KEEP=1 run."""
    canon = lambda n: "".join(ch for ch in n.lower() if ch.isalnum())
    code, plans = call("GET", CHOREO + "/plans")
    for plan in (plans or {}).get("entries", []) if code == 200 else []:
        if canon(plan["name"]).startswith(PLAN_PREFIX):
            c, _ = call("DELETE", CHOREO + f"/plans/{plan['id']}")
            print(f"  plan {plan['name']} (and its sessions) removed: HTTP {c}")
    code, ex = call("GET", CHOREO + "/executors")
    for e in (ex or {}).get("entries", []) if code == 200 else []:
        if e.get("name") in SYSTEMS:
            c, _ = call("DELETE", CHOREO + f"/executors/{e['id']}")
            print(f"  executor {e['name']} removed: HTTP {c}")


def names_query():
    return ",".join(SYSTEMS)


def setup():
    login()
    print("Authentication identities:")
    call("DELETE", AUTH + "/mgmt/sessions?names=" + names_query())
    call("DELETE", AUTH + "/mgmt/identities?names=" + names_query())
    ids = [{"systemName": n, "credentials": {"password": open(f"/state/systems/{n}/identity-password").read().strip()}, "sysop": False} for n in SYSTEMS]
    code, body = call("POST", AUTH + "/mgmt/identities", {"authenticationMethod": "PASSWORD", "identities": ids})
    created = {e.get("systemName") for e in (body.get("identities") or body.get("entries") or [])} if isinstance(body, dict) else set()
    for n in SYSTEMS:
        print(("  " if n in created else "  FAILED ") + n)
    if set(SYSTEMS) - created:
        sys.exit(f"identities not created: {code} {body}")
    print("ConsumerAuthorization policies:")
    for p in policies():
        apply(p)
    print("authorization.verify extended for the provider-side checks:")
    # ServiceRegistry too: with management.policy=authorization it asks authorization.verify about serviceRegistryManagement
    # callers, and the WorkflowConfigurator lists the cloud's services that way
    apply(verify_policy(VERIFIERS + [n for n in ["WorkflowManager", "WorkflowConfigurator", "ServiceRegistry", *EXECUTORS] if n not in VERIFIERS]))
    print("Choreographer: leftovers of earlier runs removed")
    choreographer_cleanup()
    print("Shared policies extended (stored policies saved in .state/backup):")
    for instance_id, spec in SHARED.items():
        extend_shared(instance_id, spec)


def teardown():
    login()
    print("authorization.verify restored to the cloud's declared list")
    apply(verify_policy(VERIFIERS))
    print("Shared policies restored from .state/backup")
    for instance_id, spec in SHARED.items():
        restore_shared(instance_id, spec)
    print("Choreographer: test plans and executors")
    choreographer_cleanup()
    print("ConsumerAuthorization policies revoked")
    for p in policies():
        revoke(pid(p))
    print("ServiceRegistry entries removed")
    code, body = call("DELETE", SR + "/mgmt/systems?names=" + names_query())
    print(f"  systems: HTTP {code}")
    print("Authentication sessions and identities removed")
    for target in ("sessions", "identities"):
        code, _ = call("DELETE", AUTH + f"/mgmt/{target}?names=" + names_query())
        print(f"  {target}: HTTP {code}")


def status():
    login()
    code, body = call("POST", SR + "/service-discovery/lookup", {"providerNames": SYSTEMS})
    for e in (body or {}).get("entries", []) if isinstance(body, dict) else []:
        print(e["provider"]["name"], e["serviceDefinition"]["name"], e.get("metadata"))
    print(f"lookup HTTP {code}")


def choreographer_executors():
    """Exit status 0 when the Choreographer lists none of the test systems as executor (they unregistered themselves)."""
    login()
    code, ex = call("GET", CHOREO + "/executors")
    left = [e["name"] for e in (ex or {}).get("entries", []) if e.get("name") in SYSTEMS] if code == 200 else ["(query failed)"]
    print(("PASS " if not left else "FAIL ") + "WfeChor unregistered from the Choreographer at shutdown" + ("" if not left else f"  -> still listed: {left}"))
    sys.exit(1 if left else 0)


{"setup": setup, "teardown": teardown, "status": status, "choreographer-executors": choreographer_executors}[sys.argv[1]]()
