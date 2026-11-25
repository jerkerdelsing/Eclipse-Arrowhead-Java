r"""Workflow services: operations, behaviour and examples. Paths, methods and status codes are taken from
workflow-model wire/WorkflowServices.java and ServiceOperations.java, the *API.java controllers, WorkflowEngine,
RecipeService and LeaseService, and the WorkflowConfigurator's ConfigurationAPI and ConfiguratorOperations."""

WFM, WFE, WFC = "WorkflowManager", "WorkflowExecutor", "WorkflowConfigurator"

E400 = ("400", r"The request is missing or malformed (\mref{ErrorMessage}, \texttt{INVALID\_PARAMETER}).")
E401 = ("401", r"No valid identity token, or the token is bound to another certificate (\texttt{AUTH}).")
E403 = ("403", r"The consumer has no ConsumerAuthorization grant for this operation (\texttt{FORBIDDEN}).")
E404 = ("404", r"The referenced object does not exist (\texttt{DATA\_NOT\_FOUND}).")
E500 = ("500", r"Internal error, e.g. a database failure.")
COMMON = [E401, E403]


def op(name, method, path, inp, out, sd, ok, errors=(), params=(), req=None, resp=None, note=None):
    return dict(name=name, method=method, path=path, inp=inp, out=out, sd=sd, ok=ok, errors=list(errors) + COMMON,
                params=list(params), req=req, resp=resp, note=note)


RECIPE_EXAMPLE = """{
  "name": "temperature-branching",
  "version": "1.0.0",
  "firstAction": "control",
  "parameters": [ { "name": "threshold", "type": "number" } ],
  "actions": [ {
    "name": "control",
    "steps": [
      { "name": "read", "service": { "serviceDefinition": "temperature-read" },
        "next": ["evaluate"] },
      { "name": "evaluate", "service": { "serviceDefinition": "temperature-evaluate" },
        "next": ["heatOn", "heatOff"], "dep": ["read"],
        "input": { "temperature": "${result.read.temperature}",
                   "threshold": "${param.threshold}" },
        "guards": { "heatOn": "${result.evaluate.heat} == true",
                    "heatOff": "!${result.evaluate.heat}" } },
      { "name": "heatOn", "service": { "serviceDefinition": "heating-switch" },
        "next": ["confirm"], "input": { "on": true } },
      { "name": "heatOff", "service": { "serviceDefinition": "heating-switch" },
        "next": ["confirm"], "input": { "on": false } },
      { "name": "confirm", "service": { "serviceDefinition": "temperature-read" },
        "dep": ["heatOn", "heatOff"] }
    ] } ]
}"""

SERVICES = {}


def service(name, **kw):
    kw["name"] = name
    kw.setdefault("version", "1.0.0")
    kw.setdefault("extra_sd", "")
    kw.setdefault("extra_idd", "")
    kw.setdefault("structs", [])
    SERVICES[name] = kw


# ====================================================================================== workflowRecipe
service("workflowRecipe", system=WFM, base="/workflowmanager/recipe",
        consumers=["WorkflowConfigurator", "Engineering tool", "Upper WorkflowManager"],
        summary=r"validates, registers, lists, retires and compiles workflow recipes",
        overview=r"""The \texttt{workflowRecipe} service is the recipe catalogue of a WorkflowManager. A recipe is the
design-time description of a workflow: which Arrowhead services are consumed (service definitions and capabilities,
never endpoints), in which order and under which conditions, and how data flows between the exchanges. The service
validates recipes against the well-formedness conditions of the formal recipe model~\cite{varga2026} and the
WorkflowManager's own checks, stores accepted recipes immutably by name and version, and compiles them to plans of the
existing Choreographer.""",
        prior_art=r"""The recipe model is the formal definition $W = (P, A, S, \theta, \iota, \mu, q, pre, \eta, qnty, dep, \kappa)$
of Varga~\cite{varga2026}, with its well-formedness conditions (W1)--(W4). Separating the workflow description from the
consumers that execute it follows Derhamy's design principles of loose coupling and specialisation~\cite{derhamy2018}
and the workflow management system of Garcia Represa~\cite{represa2022}. The plan compiled by \oref{compile} is the
plan model of the Arrowhead Choreographer (4.6.2 to 5.4.0).""",
        use=r"""An engineering tool, normally the WorkflowConfigurator, calls \oref{validate} while a workflow is being
designed and \oref{register} when it is accepted. A product, line controller or upper WorkflowManager then starts
sessions of the registered recipe through \texttt{workflowInstance}. Old versions are retired with \oref{retire}: they
remain readable for traceability but cannot be started. A recipe that should run on an existing Choreographer is
compiled with \oref{compile} and submitted to the Choreographer's plan management.""",
        delimitations=r"""\begin{itemize}
\item Recipes are immutable: registering different content under an existing name and version is refused (409). A change needs a new version.
\item The validator decides on the structure, the data flow, the lease discipline and, in a connected deployment, on the availability of services and executors in the running cloud (checks C01, C02). It does not check the semantic correctness of the equipment operations.
\item \texttt{quantity} $>$ 1 is not accepted (M01); repeated execution is outside the proven model.
\item Compilation to a Choreographer plan is lossy: inputs ($\kappa_s$), guards, dependencies and policies cannot be expressed in a plan and are listed in \texttt{lossySteps}.
\item Retiring is the only life-cycle change; recipes are never deleted through this service.
\end{itemize}""",
        ops=[
            op("validate", "POST", "/validate", "Recipe", "ValidationReport",
               r"Validates a recipe without storing it. Every finding is reported; the verdict is the worst outcome.",
               "200 OK with the report, whatever the verdict.", [("400", "The body is not a recipe (unknown field, wrong type).")],
               req=RECIPE_EXAMPLE,
               resp="""{ "recipe": "temperature-branching@1.0.0", "verdict": "ACCEPT",
  "findings": [ { "code": "G04", "outcome": "ACCEPT", "basis": "x-guard",
    "location": "recipe", "message": "Recipe uses guarded transitions (extension x-guard)" } ] }"""),
            op("register", "POST", "/register", "Recipe", "RecipeRegistration",
               r"Validates the recipe and stores it when the verdict is ACCEPT. The registering system is recorded as \texttt{registeredBy}.",
               "201 Created for a new version; 200 OK when identical content is already registered.",
               [("400", r"The verdict is not ACCEPT; the body is a \mref{RecipeRegistration} with \texttt{recipe = null} and the report."),
                ("409", "The name and version exist with different content.")],
               req=RECIPE_EXAMPLE,
               resp="""{ "recipe": { "id": 7, "name": "temperature-branching", "version": "1.0.0",
    "status": "ACTIVE", "registeredBy": "WorkflowConfigurator",
    "recipe": { ... }, "createdAt": "2026-11-21T09:12:44Z" },
  "validation": { "recipe": "temperature-branching@1.0.0", "verdict": "ACCEPT", "findings": [ ... ] },
  "created": true }"""),
            op("query", "GET", "/recipes", "RecipeFilter", "RecipeList",
               "Lists registered recipes in registration order, optionally only the versions of one recipe.",
               "200 OK.", [], params=[("name", "query", "Name", "Optional recipe name."),
                                      ("summary", "query", "Boolean", r"Optional, default false. When true, each entry leaves out \texttt{recipe}, so the list stays small however large the recipes are; \oref{get} returns one document.")]),
            op("get", "GET", "/recipes/{name}/{version}", "RecipeKey", "RecipeRecord",
               "Returns one registered recipe version, active or retired.", "200 OK.", [E404],
               params=[("name", "path", "Name", "Recipe name."), ("version", "path", "Version", "Recipe version.")]),
            op("retire", "DELETE", "/recipes/{name}/{version}", "RecipeKey", "RecipeRecord",
               r"Marks a recipe version \texttt{RETIRED}. Running sessions continue; new sessions are refused.",
               "200 OK with the retired record.", [E404],
               params=[("name", "path", "Name", "Recipe name."), ("version", "path", "Version", "Recipe version.")]),
            op("compile", "POST", "/compile", "RecipeKey", "ChoreographerPlan",
               r"Compiles an active recipe version (the latest when \texttt{version} is absent) to a Choreographer plan request.",
               "200 OK.", [E404, ("409", "The recipe version is retired.")],
               req="""{ "name": "temperature-control", "version": "1.0.0" }""",
               resp="""{ "name": "temperature-control", "firstActionName": "control",
  "actions": [ { "name": "control", "firstStepNames": ["read"],
    "steps": [ { "name": "read", "quantity": 1,
      "serviceRequirement": { "serviceDefinition": "temperature-read" },
      "localCloudOnly": true, "nextStepNames": ["evaluate"] }, ... ] } ],
  "lossySteps": ["evaluate", "switch"] }"""),
        ],
        structs=["ValidationReport", "RecipeRegistration", "RecipeList", "RecipeRecord", "RecipeKey", "RecipeFilter", "ChoreographerPlan"],
        authorization=r"Each operation is authorized separately (\texttt{workflowRecipe.validate}, \ldots). The WorkflowConfigurator needs \texttt{validate}, \texttt{register}, \texttt{query} and \texttt{get}.",
        )

# ====================================================================================== workflowInstance
service("workflowInstance", system=WFM, base="/workflowmanager/instance",
        consumers=["Intelligent Product", "Line controller", "Upper WorkflowManager"],
        summary=r"starts, observes and aborts workflow sessions",
        overview=r"""The \texttt{workflowInstance} service starts and observes \emph{sessions}. A session is one
execution of one immutable recipe version for one subject: a product unit, a control period or a batch. Its state is
$z = (M, M_A, \beta, \varepsilon, \xi, status)$~\cite{varga2026}: the step and action markings, the service bindings,
the executor assignment, the stored results and the status. The WorkflowManager advances the state with the transition
rules of the model, dispatches enabled steps to WorkflowExecutors, and exposes the markings and the full trace through
this service.""",
        prior_art=r"""The session state and its eight transition rules (A-Start, S-Enable, S-Bind, S-Done, A-Finish, Z-Close,
S-FailBind, S-FailInvoke) are those of Varga~\cite{varga2026}, which also proves that a failure-free run of a
well-formed recipe takes exactly $3|S| + 2|A|$ transitions (Theorem~1) and always progresses (Theorem~2). The
product-driven start, where the product hands its recipe to the work centre, is Derhamy's Intelligent Product
pattern~\cite{derhamy2018workflow}.""",
        use=r"""A consumer starts a session with \oref{start}, naming a registered recipe (and optionally its version), the
session parameters $\rho$ and the dispatch mode. Alternatively it names a \texttt{product} system: the WorkflowManager
then retrieves the recipe fragment and parameters from the product's \texttt{workflowProduct} service and stores the
results back when the session completes. The owner follows the session with \oref{get-status} and \oref{get-trace} and may
stop it with \oref{abort}. \oref{query} lists sessions, e.g. all \texttt{running} ones.""",
        delimitations=r"""\begin{itemize}
\item Failure is absorbing: a failed session never resumes. A new session must be started.
\item Theorems~1--2 hold only for sessions without extensions. The \texttt{extensions} field and \texttt{withinModel} in the trace say whether a session used rebind or retry, abort, timeout, guarded skipping or lease compensation.
\item Only the owner (the starting system) or a sysop may abort a session.
\item Sessions run in one local cloud. Delegation of actions to WorkflowManagers in other clouds is not implemented.
\item The \texttt{event} dispatch mode is accepted but handled as \texttt{pull}.
\end{itemize}""",
        ops=[
            op("start", "POST", "/start", "StartSessionRequest", "Session",
               r"Creates a session of an active recipe version (or of the product's recipe), fires the first transitions and dispatches the enabled steps.",
               "201 Created with the new session.",
               [("400", "Missing request or recipe, parameters not an object, required parameter missing, unknown dispatch mode, or a product recipe that does not validate."),
                E404, ("409", "The recipe version is retired."),
                ("503", r"The product's \texttt{workflowProduct} returned no recipe (\texttt{EXTERNAL\_SERVER\_ERROR}).")],
               req="""{ "recipe": "temperature-branching", "version": "1.0.0",
  "subject": "period-2026-11-21T10:00", "parameters": { "threshold": 23 },
  "dispatch": "push" }""",
               resp="""{ "id": 42, "recipe": "temperature-branching", "version": "1.0.0",
  "subject": "period-2026-11-21T10:00", "owner": "WfClient", "status": "running",
  "dispatch": "push", "extensions": [],
  "stepMarking": { "read": "running", "evaluate": "inactive", "heatOn": "inactive",
                   "heatOff": "inactive", "confirm": "inactive" },
  "actionMarking": { "control": "active" }, "modelTransitions": 2,
  "transitionBound": 17, "createdAt": "...", "updatedAt": "..." }"""),
            op("abort", "DELETE", "/abort/{id}", "SessionRef", "Session",
               r"Aborts a running session (extension x-abort). Its active work items become \texttt{ABORTED} and their executors receive \texttt{workflowExecution.abort}; leases are released when \texttt{workflow.lease.release-on-failure} is set.",
               "200 OK with the failed session.",
               [("403", "The caller is neither the owner nor a sysop."), E404, ("409", "The session is already completed or failed.")],
               params=[("id", "path", "SessionId", "Session."), ("reason", "query", "String", "Optional reason.")]),
            op("query", "GET", "/sessions", "SessionFilter", "SessionList",
               "Lists sessions, newest first, optionally in one status.", "200 OK.",
               [("400", "Unknown status.")], params=[("status", "query", "SessionStatus", "Optional status.")]),
            op("get-status", "GET", "/sessions/{id}", "SessionRef", "Session",
               "Returns the markings and status of one session.", "200 OK.", [E404],
               params=[("id", "path", "SessionId", "Session.")]),
            op("get-trace", "GET", "/sessions/{id}/trace", "SessionRef", "Trace",
               r"Returns the run: every transition, $\xi$, $\beta$, $\varepsilon$, and whether the run stayed within the proven model.",
               "200 OK.", [E404], params=[("id", "path", "SessionId", "Session.")],
               resp="""{ "sessionId": 42, "status": "completed",
  "trace": [ { "seq": 1, "rule": "S_ENABLE", "target": "read", "at": "..." },
             { "seq": 2, "rule": "S_BIND", "target": "read", "detail": "WfeA", "at": "..." },
             ... ,
             { "seq": 14, "rule": "Z_CLOSE", "at": "..." } ],
  "results": { "read": { "temperature": 21.5 }, "evaluate": { "heat": true }, ... },
  "bindings": { "read": { "provider": "WfEquipment", ... } },
  "executorAssignment": {}, "runner": { "read": "WfeA", ... },
  "modelTransitions": 14, "transitionBound": 17, "withinModel": false }"""),
        ],
        structs=["StartSessionRequest", "Session", "SessionList", "SessionRef", "SessionFilter", "Trace"],
        authorization=r"Each operation is authorized separately. \texttt{abort} additionally requires the caller to be the session owner or a sysop.",
        )

# ====================================================================================== workflowWorkItem
service("workflowWorkItem", system=WFM, base="/workflowmanager/work-item",
        consumers=["WorkflowExecutor"],
        summary=r"lets executors claim work items and report their outcome",
        overview=r"""The \texttt{workflowWorkItem} service is the interface between the WorkflowManager, which keeps the
books, and the WorkflowExecutors, which do the work. A \emph{work item} is one attempt to run one step of one session.
In pull mode executors \oref{claim} open work items they can run (the blackboard of~\cite{derhamy2018workflow}); in
every mode the executor holding a work item \oref{report}s what happened: the provider it was bound to, the result, or
the failure. The reports drive the transition rules S-Bind, S-Done, S-FailBind and S-FailInvoke.""",
        prior_art=r"""Derhamy's RetrieveOperation and StoreOperationResult interfaces~\cite{derhamy2018workflow}; the
Choreographer's \texttt{notify-step-done} and its result codes SUCCESS, ERROR, FATAL\_ERROR and ABORTED.""",
        use=r"""A pull-mode executor calls \oref{claim} periodically with its capabilities and services. It receives the
first open work item it can run, or no content. After orchestration it reports \texttt{BOUND} (with the binding) or
\texttt{BIND\_FAILED}; after the invocation it first stores the result through \texttt{workflowContext.put-result} and
then reports \texttt{SUCCESS}, \texttt{ERROR} or \texttt{FATAL\_ERROR}. Push-mode executors receive their work items
through \texttt{workflowExecution.start} and report the same way.""",
        delimitations=r"""\begin{itemize}
\item The claim is atomic: one executor per work item attempt. Only the holder (or a sysop) may report on a work item.
\item A published work item that nobody claims within \texttt{workflow.pull.claim-timeout} (default 300\,s) fails the step with S-FailBind. A held work item is limited by the step's \texttt{timeoutSeconds} (extension x-timeout, counted from its creation), when the policy sets one, and by the liveness check: the WorkflowManager asks the executor with \texttt{workflowExecution.get-status} whether it still runs it.
\item A report on a work item that is no longer active (already done or failed), or that waits for its retry (\texttt{RETRY\_WAIT}), is refused (409). A late report on an aborted work item is accepted and ignored.
\end{itemize}""",
        ops=[
            op("claim", "POST", "/claim", "ClaimRequest", "WorkItem",
               r"Atomically claims the first open work item the caller can run: its services cover $\theta(s)$ and its capabilities satisfy $\eta(s)$.",
               "200 OK with the work item; 204 No Content when none is open.", [],
               req="""{ "capabilities": ["heating"], "services": ["*"], "workstation": "cell-1" }""",
               resp="""{ "workItemId": "5d2c0b1e-...", "sessionId": 42, "recipe": "temperature-branching@1.0.0",
  "action": "control", "step": "evaluate", "attempt": 1,
  "service": { "serviceDefinition": "temperature-evaluate" },
  "input": { "temperature": "${result.read.temperature}", "threshold": "${param.threshold}" },
  "parameters": { "threshold": 23 }, "dependencyResults": { "read": { "temperature": 21.5 } } }"""),
            op("report", "POST", "/report", "WorkItemReport", "",
               r"Reports on a held work item. \texttt{BOUND} completes S-Bind; \texttt{SUCCESS} fires S-Done with the stored result; \texttt{BIND\_FAILED} tries the next executor or fires S-FailBind; \texttt{ERROR} causes x-rebind or x-retry while the step's retry policy allows another attempt, and otherwise fails the session; \texttt{FATAL\_ERROR} fails the session; \texttt{ABORTED} acknowledges an abort, and fails the session when the executor stopped on its own. A failure is recorded as S-FailInvoke when the step was bound and as S-FailBind when it was not.",
               "200 OK, empty body.",
               [("400", r"\texttt{workItemId} or \texttt{status} missing, or unknown status."),
                ("403", "The work item is held by another executor."),
                ("409", "The work item is no longer active, or waits for its retry.")],
               req="""{ "workItemId": "5d2c0b1e-...", "status": "SUCCESS", "result": { "heat": true } }"""),
        ],
        structs=["ClaimRequest", "WorkItem", "WorkItemReport"],
        authorization=r"Each operation is authorized separately; executors need both. The holder check is made in addition.",
        )

# ====================================================================================== workflowContext
service("workflowContext", system=WFM, base="/workflowmanager/context",
        consumers=["WorkflowExecutor"],
        summary=r"gives executors the session parameters and the results their step may read, and stores step results",
        overview=r"""The \texttt{workflowContext} service holds the session context: the parameters $\rho$ fixed at the
start of a session and the results $\xi$ stored by completed steps. An executor holding a work item for step $s$ reads
$\rho$ and $\xi|dep(s)$, the results of the steps $s$ depends on, composes the request $\kappa_s(\rho, \xi|dep(s))$ and
stores the provider's answer as $\xi(s)$ before reporting success. The context replaces ad hoc data passing between
executors and keeps the data with the WorkflowManager, from where it is returned to the product.""",
        prior_art=r"""The session-context service of the Arrowhead workflow kits (\texttt{github.com/palvarga/arrowhead-workflows})
and Lemma~2 of~\cite{varga2026}: for a well-formed recipe a step never runs on missing data.""",
        use=r"""Before invoking the provider of step $s$, the executor calls \oref{get-parameters} and \oref{get-results}
for its work item; after a successful invocation it calls \oref{put-result} and only then reports \texttt{SUCCESS}
through \texttt{workflowWorkItem}.""",
        delimitations=r"""\begin{itemize}
\item Only the holder of the work item (or a sysop) may read or write its context (403 otherwise).
\item Results are restricted to $dep(s)$; there is no way to read other results of the session.
\item A dependency skipped by a guard (extension x-guard) is returned as null. A missing result of a completed dependency would violate Lemma~2 and is refused with 409.
\item \oref{put-result} stores a pending result. When the following \texttt{SUCCESS} report also carries a result, the reported one is used.
\end{itemize}""",
        ops=[
            op("get-parameters", "GET", "/parameters/{workItemId}", "WorkItemRef", "Parameters",
               r"Returns $\rho$ of the work item's session.", "200 OK.",
               [("403", "The work item is held by another executor."), E404, ("409", "The work item is no longer active.")],
               params=[("workItemId", "path", "WorkItemId", "Held work item.")], resp="""{ "threshold": 23 }"""),
            op("get-results", "GET", "/results/{workItemId}", "WorkItemRef", "StepResults",
               r"Returns $\xi|dep(s)$ for the work item's step.", "200 OK.",
               [("403", "The work item is held by another executor."), E404, ("409", "A result in dep(s) is missing, or the work item is no longer active.")],
               params=[("workItemId", "path", "WorkItemId", "Held work item.")], resp="""{ "read": { "temperature": 21.5 } }"""),
            op("put-result", "POST", "/result", "PutResultRequest", "",
               r"Stores $\xi(s)$ for the work item's step.", "200 OK, empty body.",
               [("400", "Missing request."), ("403", "The work item is held by another executor."), E404, ("409", "The work item is no longer active.")],
               req="""{ "workItemId": "5d2c0b1e-...", "result": { "heat": true } }"""),
        ],
        structs=["Parameters", "StepResults", "WorkItemRef", "PutResultRequest"],
        authorization=r"Each operation is authorized separately; the holder check is made in addition.",
        )

# ====================================================================================== workflowLease
service("workflowLease", system=WFM, base="/workflowmanager/lease",
        consumers=["WorkflowExecutor"],
        summary=r"grants exclusive, expiring use of shared resources such as tools",
        overview=r"""The \texttt{workflowLease} service is a FIFO lease manager for shared resources, typically tools
in a tool crib used by several production lines. A step with lease mode \texttt{reserve} reserves the resource; the
returned token is stored in its result and read by the tool-using steps, which \oref{renew} the lease immediately before
each use and fail without a valid lease; a \texttt{release} step releases it. A lease that is not renewed in time
expires, and the next queued reservation is granted, so a failed holder cannot block other sessions forever.""",
        prior_art=r"""Proposition~1 of~\cite{varga2026} (exclusive tool use) and the lease variant discussed there and in
the workflow kits. Proposition~1 is proved under the assumption that the tool manager grants one session at a time; for
the lease variant it additionally assumes that every tool step runs inside a lease renewed immediately before it starts
and that the lease is longer than the longest tool step (validator check L04).""",
        use=r"""The executor running a \texttt{reserve} step calls \oref{reserve}; when the answer is \texttt{QUEUED} it calls
\oref{poll} with the ticket until \texttt{GRANTED} (the call is pending, so the step stays running). Steps in mode
\texttt{use} call \oref{renew} with the token before the invocation. The \texttt{release} step calls \oref{release}.
When a session fails and \texttt{workflow.lease.release-on-failure} is set, the WorkflowManager releases its leases
(compensation extension).""",
        delimitations=r"""\begin{itemize}
\item Mutual exclusion is provided by this service, not by the workflow engine. Exclusive use on runs is checked in verification but not proved for the lease variant.
\item Tokens are capabilities: whoever holds a valid token can renew and release. Tokens travel only in $\xi$ through \texttt{workflowContext}.
\item Leases are local to one WorkflowManager. An external crib may implement the same contract.
\end{itemize}""",
        ops=[
            op("reserve", "POST", "/reserve", "LeaseRequest", "LeaseResponse",
               r"Queues a reservation and grants it when the resource is free (FIFO).", r"200 OK with \texttt{GRANTED} or \texttt{QUEUED}.",
               [("400", "Resource missing.")],
               req="""{ "resource": "nutrunner-1", "sessionId": 42, "durationSeconds": 60 }""",
               resp="""{ "status": "QUEUED", "resource": "nutrunner-1", "ticket": "9b1f..." }"""),
            op("poll", "POST", "/poll", "LeaseRequest", "LeaseResponse",
               r"Expires overdue leases, grants the ticket if it is first in line and the resource is free, and returns its state.",
               r"200 OK; \texttt{UNKNOWN} for an unknown ticket.", [],
               req="""{ "ticket": "9b1f..." }""",
               resp="""{ "status": "GRANTED", "resource": "nutrunner-1", "token": "c07e...",
  "ticket": "9b1f...", "leaseUntil": "2026-11-21T10:01:00Z" }"""),
            op("renew", "POST", "/renew", "LeaseRequest", "LeaseResponse",
               r"Extends a granted lease by its duration.", r"200 OK; a status other than \texttt{GRANTED} with message \texttt{No valid lease} when the token is unknown, expired or released.", [],
               req="""{ "token": "c07e..." }"""),
            op("release", "POST", "/release", "LeaseRequest", "LeaseResponse",
               r"Releases a granted lease; the next queued reservation can be granted.", r"200 OK with \texttt{RELEASED}; \texttt{UNKNOWN} for an unknown token.", [],
               req="""{ "token": "c07e..." }"""),
        ],
        structs=["LeaseRequest", "LeaseResponse"],
        authorization=r"Each operation is authorized separately.",
        )

# ====================================================================================== workflowManagement
service("workflowManagement", system=WFM, base="/workflowmanager/mgmt",
        consumers=["Sysop", "Management tool"],
        summary=r"gives operators the worklog, the known executors and the work items",
        overview=r"""The \texttt{workflowManagement} service is the operator view of a WorkflowManager: the audit worklog
of sessions and steps, the executors the WorkflowManager can dispatch to (with their declared capabilities), and the
work items with their states.""",
        prior_art=r"""The management services of the Arrowhead~5 support systems, e.g. the Choreographer's worklog.""",
        use=r"""An operator or a management tool queries the worklog of one session to see why it failed, lists the
executors to check which capabilities are available, and lists the work items of a session to see which executor ran
what.""",
        delimitations=r"""\begin{itemize}
\item Read-only; recipes are managed through \texttt{workflowRecipe}, sessions through \texttt{workflowInstance}.
\item Protected by the management policy of the system (\texttt{management.policy=sysop-only} in the secure deployment), not by ConsumerAuthorization.
\end{itemize}""",
        ops=[
            op("query-worklog", "GET", "/worklog", "WorklogFilter", "WorklogList",
               "Returns worklog entries, optionally of one session.", "200 OK.", [],
               params=[("sessionId", "query", "SessionId", "Optional session.")]),
            op("query-executors", "GET", "/executors", "", "ExecutorList",
               r"Returns the executor set $WE$ with $cap(w)$: the providers of \texttt{workflowExecution} in the ServiceRegistry plus statically configured executors.",
               "200 OK.", []),
            op("query-work-items", "GET", "/work-items", "WorklogFilter", "WorkItemList",
               "Returns work items, optionally of one session.", "200 OK.", [],
               params=[("sessionId", "query", "SessionId", "Optional session.")]),
        ],
        structs=["WorklogFilter", "WorklogList", "ExecutorList", "WorkItemList"],
        authorization=r"Sysop only (management policy); a ConsumerAuthorization grant is not sufficient.",
        )

# ====================================================================================== workflowExecution
service("workflowExecution", system=WFE, base="/workflowexecutor/execution",
        consumers=["WorkflowManager"],
        summary=r"runs work items on behalf of a WorkflowManager",
        overview=r"""The \texttt{workflowExecution} service is provided by every WorkflowExecutor. In push mode the
WorkflowManager selects an executor for an enabled step and hands it the work item with \oref{start}. The executor
runs the execution primitive of the model, $compile \rightarrow O \rightarrow consume$~\cite{varga2026}, with itself
as the consumer: it reserves or renews a lease when the step needs one, has the provider orchestrated for itself,
composes the input, invokes the provider and reports through \texttt{workflowWorkItem}. The service also tells the
WorkflowManager what the executor can do (\oref{service-info}).""",
        prior_art=r"""The executor contract of the Arrowhead Choreographer (\texttt{service-info}, \texttt{start},
\texttt{abort}), unchanged since 4.6; Derhamy's HATEOAS workflow executor~\cite{derhamy2018workflow}.""",
        use=r"""An executor registers \texttt{workflowExecution} in the ServiceRegistry with the metadata
\texttt{workflowCapabilities}, \texttt{workflowServices}, \texttt{workflowWorkstation} and \texttt{workflowMode}; the
WorkflowManager discovers it there. For each enabled step the WorkflowManager calls \oref{start} on an executor whose
capabilities satisfy $\eta(s)$ and whose services cover $\theta(s)$, preferring the least-loaded one. \oref{start}
returns at once; the result arrives later as a report. The WorkflowManager calls \oref{abort} when a session is
aborted or a step times out.""",
        delimitations=r"""\begin{itemize}
\item Only the configured WorkflowManager (\texttt{workflow.manager.system-name}) or a sysop may start or abort work items (403 otherwise).
\item The executor keeps no durable state; after a restart \oref{get-status} answers \texttt{UNKNOWN} and the WorkflowManager then treats the work item as lost (see \oref{get-status}).
\item Equipment is invoked through adapters; the reference implementation has the generic HTTP/HTTPS JSON adapter only.
\item An executor never consumes services outside its local cloud unless the step's policy allows it (\texttt{localCloudOnly = false}).
\end{itemize}""",
        ops=[
            op("service-info", "POST", "/service-info", "", "ExecutorInfo",
               r"Returns the executor's name, capabilities $cap(w)$, runnable services, workstation and mode.", "200 OK.", [],
               resp="""{ "name": "WfeA", "capabilities": ["heating"], "services": ["*"],
  "workstation": "cell-1", "mode": "push" }"""),
            op("start", "POST", "/start", "WorkItem", "",
               r"Accepts a work item and runs it asynchronously. The outcome is reported through \texttt{workflowWorkItem.report}.",
               "202 Accepted, empty body.",
               [("400", r"Work item incomplete (\texttt{workItemId} or \texttt{step} missing)."), ("403", "The caller is not the configured WorkflowManager.")]),
            op("abort", "POST", "/abort", "AbortRequest", "",
               "Aborts a running work item; the executor reports ABORTED.", "200 OK, empty body.",
               [("403", "The caller is not the configured WorkflowManager.")],
               req="""{ "workItemId": "5d2c0b1e-...", "sessionId": 42, "reason": "session aborted" }"""),
            op("get-status", "GET", "/status/{workItemId}", "WorkItemRef", "ExecutionState",
               r"Returns the executor's local state of a work item. The WorkflowManager uses it as liveness check: a held work item is lost when two checks in a row find it neither \texttt{ACCEPTED} nor \texttt{RUNNING}, or when the executor cannot be reached or is no longer registered for \texttt{workflow.executor.liveness.lost-after-seconds}. A lost item that is not bound goes to the next executor; a bound one is handled like \texttt{ERROR}.", "200 OK.", [],
               params=[("workItemId", "path", "WorkItemId", "Work item.")],
               resp="""{ "workItemId": "5d2c0b1e-...", "state": "RUNNING" }"""),
        ],
        structs=["ExecutorInfo", "WorkItem", "AbortRequest", "WorkItemRef", "ExecutionState"],
        authorization=r"Each operation is authorized separately; \texttt{start} and \texttt{abort} additionally require the caller to be the configured WorkflowManager or a sysop.",
        )

# ====================================================================================== workflowConfiguration
service("workflowConfiguration", system=WFC, base="/workflowconfigurator/api",
        consumers=["Browser console (operator)", "Engineering tool"],
        summary=r"lists configured local clouds, their microservices and executors, and validates and stores edited workflows",
        overview=r"""The \texttt{workflowConfiguration} service is what the graphical workflow editor of the
WorkflowConfigurator uses, offered as an Arrowhead service so that other engineering tools can use it as well. It
lists the local clouds a workflow can be designed for (the running cloud, LocalCloudConfigurator designs and the member
clouds of System of LocalClouds designs), the microservices offered in a cloud with their operations, and the
WorkflowExecutors with their capabilities. It forwards validation and storage of edited workflows to the
WorkflowManager, where recipes are stored and nowhere else.""",
        prior_art=r"""Garcia Represa's workflow selection and implementation phases~\cite{represa2022}; the
LocalCloudConfigurator and SoLocalCloudConfigurator configuration services of this workspace.""",
        use=r"""The editor first calls \oref{list-clouds} and lets the operator pick a cloud, then \oref{list-services} and
\oref{list-executors} to populate its palette. While drawing it calls \oref{validate-recipe}; \oref{store-recipe}
registers the workflow in the WorkflowManager when it validates. \oref{list-recipes} and \oref{get-recipe} open stored
workflows.""",
        delimitations=r"""\begin{itemize}
\item Every source is optional; an unavailable source is reported in \texttt{sources} and the others are still listed.
\item Services of another cloud are listed only when that cloud is the live one; its ServiceRegistry is not reachable from here.
\item Without a \texttt{serviceRegistryManagement} grant the configurator falls back to discovery, which under the RESTRICTED discovery policy hides services offered without \texttt{unrestrictedDiscovery}.
\item The configurator stores nothing itself; validation and storage are the WorkflowManager's.
\end{itemize}""",
        ops=[
            op("list-clouds", "GET", "/clouds", "", "CloudList",
               "Lists the live cloud, the LocalCloudConfigurator designs and the SoLC member clouds.", "200 OK.", []),
            op("list-services", "GET", "/clouds/{id}/services", "CloudRef", "ServiceList",
               r"Lists the microservices of a cloud from the ServiceRegistry, without \texttt{sysMonitor} and \texttt{generalManagement}.",
               "200 OK.", [E404, ("502/503", "The ServiceRegistry failed; the body carries \\texttt{error}, \\texttt{provider} and \\texttt{status}.")],
               params=[("id", "path", "String", r"Cloud identifier from \oref{list-clouds}.")],
               resp="""{ "cloud": "live", "name": "TestCloud", "live": true, "systems": [ ... ],
  "entries": [ { "provider": "WfEquipment", "serviceDefinition": "temperatureRead",
    "version": "1.0.0", "metadata": {},
    "interfaces": [ { "templateName": "generic_https", "policy": "CERT_AUTH" } ],
    "operations": ["read"] } ] }"""),
            op("list-executors", "GET", "/executors", "", "ExecutorCapabilityList",
               "Lists the WorkflowExecutors of the live cloud with their capabilities.", "200 OK.", []),
            op("list-recipes", "GET", "/recipes", "RecipeFilter", "RecipeList",
               r"Forwards \texttt{workflowRecipe.query} with \texttt{summary=true}: the entries carry no \texttt{recipe} document (a full list outgrew the HTTP client's 256~KiB response buffer); \oref{get-recipe} reads the chosen version.", "200 OK.", [],
               params=[("name", "query", "Name", "Optional recipe name.")]),
            op("get-recipe", "GET", "/recipes/{name}/{version}", "RecipeKey", "RecipeRecord",
               r"Forwards \texttt{workflowRecipe.get}.", "200 OK.", [E404],
               params=[("name", "path", "Name", "Recipe name."), ("version", "path", "Version", "Recipe version.")]),
            op("validate-recipe", "POST", "/recipes/validate", "Recipe", "ValidationReport",
               r"Forwards \texttt{workflowRecipe.validate}.", "200 OK.", [("400", "The body is not a recipe.")]),
            op("store-recipe", "POST", "/recipes", "Recipe", "StoreResult",
               r"Validates with the WorkflowManager and registers the workflow when the verdict is ACCEPT.",
               "201 Created for a new version; 200 OK for identical content.",
               [("409", "The name and version exist with different content."), ("422", r"Not stored: the verdict is not ACCEPT (\texttt{stored = false} with the report).")],
               resp="""{ "stored": true, "created": true, "report": { "verdict": "ACCEPT", ... },
  "recipe": { "id": 7, "name": "temperature-branching", "version": "1.0.0", ... } }"""),
        ],
        structs=["CloudList", "CloudRef", "ServiceList", "ExecutorCapabilityList", "RecipeFilter", "RecipeList", "RecipeRecord", "RecipeKey", "ValidationReport", "StoreResult"],
        authorization=r"Each operation is authorized separately at the Arrowhead connector. The browser console reaches the same operations on a separate loopback connector with its own password login (Section on the console).",
        )

# ====================================================================================== workflowProduct (consumed)
service("workflowProduct", system="Intelligent Product", base="(provider defined)",
        consumers=["WorkflowManager"],
        summary=r"lets a WorkflowManager retrieve a product's recipe fragment and store the results back",
        overview=r"""The \texttt{workflowProduct} service is \emph{consumed} by the WorkflowManager and \emph{provided}
by an Intelligent Product, its digital twin, or an adapter to the factory control system. It realises the principle
that data belongs at its source~\cite{derhamy2018}: the recipe and the as-built record belong to the product, the
WorkflowManager only works on a copy. This document specifies the contract a product must implement to be used with
\texttt{workflowInstance.start} and its \texttt{product} field.""",
        prior_art=r"""Derhamy's RetrieveProcessStep and StoreProcessStepResult interfaces of the Intelligent
Product~\cite{derhamy2018workflow}.""",
        use=r"""When a session is started with \texttt{product} set, the WorkflowManager calls \oref{retrieve-process-step}
with its workstation identifier, validates and registers the returned recipe like any other recipe, and starts the
session with the returned parameters and subject. At Z-Close it calls \oref{store-process-step-result} with the stored
results; failures to store are logged in the worklog and do not change the session.""",
        delimitations=r"""\begin{itemize}
\item Only the consumer side is implemented (\texttt{ProductGateway}); no product system exists in this workspace yet. The contract is a proposal (open decision 4 of the architecture, link to AAS/IEC~63278).
\item The product's acceptance or rejection of the result (\texttt{REJECTED}) is not yet acted upon.
\item Failed sessions are not stored back.
\end{itemize}""",
        ops=[
            op("retrieve-process-step", "(provider)", "(provider)", "ProcessStepRequest", "ProcessOrder",
               r"Returns the recipe fragment, parameters and subject for the requesting workstation.",
               r"200 OK with a \mref{ProcessOrder}; a response without \texttt{recipe} makes \texttt{workflowInstance.start} fail with 503.", [],
               req="""{ "workstationId": "cell-1" }""",
               resp="""{ "recipe": { "name": "tightening", "version": "1.0.0", ... },
  "parameters": { "torque": 45 }, "subject": "VIN-0042" }"""),
            op("store-process-step-result", "(provider)", "(provider)", "ProcessStepResult", "",
               "Stores the results of a completed session at the product.", "2xx; the body is ignored.", [],
               req="""{ "workstationId": "cell-1", "sessionId": 43, "recipe": "tightening@1.0.0",
  "status": "completed", "results": { "tighten": { "torque": 45.2, "ok": true } } }"""),
        ],
        structs=["ProcessStepRequest", "ProcessOrder", "ProcessStepResult"],
        authorization=r"The product authorizes the WorkflowManager for both operations.",
        )

# ====================================================================================== generalManagement
service("generalManagement", system="WorkflowManager, WorkflowExecutor, WorkflowConfigurator",
        base="/workflowmanager/general/mgmt, /workflowexecutor/general/mgmt, /workflowconfigurator/general/mgmt",
        consumers=["Sysop", "LocalCloudManager"],
        summary=r"gives operators the log and selected configuration of each workflow system",
        overview=r"""Every Arrowhead~5 system provides \texttt{generalManagement}. For the workflow systems it offers
the system's log entries (\oref{get-log}) and selected configuration values (\oref{get-config}). The operations are
those of the common library; this document records the base paths and the protection used by the workflow systems.""",
        prior_art=r"""The \texttt{generalManagement} service of the Arrowhead~5.x common library.""",
        use=r"""A sysop or the LocalCloudManager reads the error log of a workflow system or checks a configuration value
such as the dispatch default.""",
        delimitations=r"""\begin{itemize}
\item Sysop only (management policy).
\item Only configuration keys on the system's allow-list are returned.
\end{itemize}""",
        ops=[
            op("get-log", "POST", "/logs", "LogRequest", "LogEntryList",
               "Returns log entries matching the filter.", "200 OK.", [("400", "Invalid filter.")],
               req="""{ "pagination": { "page": 0, "size": 20, "direction": "DESC" }, "severity": "ERROR" }"""),
            op("get-config", "GET", "/get-config", "ConfigKeys", "KeyValues",
               "Returns the values of allowed configuration keys.", "200 OK.", [],
               params=[("keys", "query", "List<String>", "Repeated query parameter.")]),
        ],
        structs=["LogRequest", "LogEntryList", "ConfigKeys", "KeyValues"],
        authorization=r"Sysop only (management policy).",
        )

# ====================================================================================== SD-only extra sections

GUARD_GRAMMAR = r"""
\subsection{Guard expressions}
\label{sec:guards}
A step may put a condition on the transition to each successor (\texttt{guards}, extension x-guard). The language is
deliberately small and side-effect free, so a WorkflowManager can evaluate it from $\rho$ and $\xi$ alone and all
implementations can share the grammar:
\begin{lstlisting}
expr      := or
or        := and ( "||" and )*
and       := unary ( "&&" unary )*
unary     := "!" unary | compare
compare   := operand ( ( "==" | "!=" | "<" | "<=" | ">" | ">=" ) operand )?
operand   := "(" expr ")" | reference | number | string | "true" | "false" | "null"
reference := "${param." name path "}" | "${result." step path "}"   path := ( "." segment )*
string    := '...' | "..."   (a backslash escapes the next character)
\end{lstlisting}
A lone operand is true when it is Boolean true, a non-zero number, or a non-empty string, array or object. A missing
reference is null. Numbers compare numerically, strings lexicographically, and \texttt{==}/\texttt{!=} compare JSON
values. Ordering a number with a non-number is false.

A step is enabled when every predecessor is resolved (completed or skipped) and at least one transition into it was
taken; it is skipped (x-skip) when none was taken. Without guards this is exactly the rule S-Enable of the model.
"""


def validator_codes_table():
    from latex import table
    rows = [
        ("S00", "REVISE", "Name or version not valid."), ("S01", "REVISE", "Two actions have the same name."),
        ("S02", "REVISE", "Two steps have the same name."), ("S03", "REVISE", "The first action does not exist."),
        ("S04", "REVISE", "The next action does not exist."), ("S05", "REVISE", "An action cannot be reached, or actions form a loop."),
        ("S06", "REVISE", "Transition to a step in another action."), ("S07", "REVISE", "The steps form a loop."),
        ("S08", "REVISE", "The workflow or an action is empty."), ("S09", "REVISE", "A step has no service."),
        ("P01", "REVISE", "Two session parameters have the same name."),
        ("D01", "REVISE", "Dependency on an unknown step."), ("D02", "REVISE", "A dependency does not run first."),
        ("D03", "REVISE", "The input reads a result the step cannot see (outside $dep(s)$)."),
        ("D04", "REVISE", "The input needs a service result no dependency provides (\\texttt{requiresResults})."),
        ("D05", "CLARIFY", "The input reads an undeclared session parameter."),
        ("G01", "REVISE", "Condition on a transition that does not exist."), ("G02", "REVISE", "A condition cannot be parsed."),
        ("G03", "REVISE", "A condition reads a result that may not exist yet."), ("G04", "ACCEPT", "Information: the workflow uses conditions (x-guard)."),
        ("G05", "CLARIFY", "A condition reads an undeclared session parameter."),
        ("L01", "REVISE", "Lease of an undeclared resource."), ("L02", "REVISE", "No reserve step before the tool use."),
        ("L03", "REVISE", "A tool use does not depend on its reserve step."), ("L04", "CLARIFY", "Lease duration missing or shorter than a tool step."),
        ("L05", "REVISE", "No release step after the tool use."),
        ("M01", "REVISE", "Quantity above 1."), ("M02", "REVISE", "Executor choice without a capability."),
        ("C01", "CLARIFY", "The service is not found in the running cloud (WorkflowManager catalogue check)."),
        ("C02", "CLARIFY", "No executor can run the step (WorkflowManager catalogue check)."),
    ]
    t = table(["Code", "Outcome", "Meaning"], [(r"\texttt{%s}" % c, o, m) for c, o, m in rows], ["1.3cm", "1.9cm", "12.8cm"])
    return r"""
\subsection{Validator checks}
\label{sec:codes}
The validator applies the checks below and reports each violation as a \mref{Finding}. The S, D and M checks realise the
well-formedness conditions (W1)--(W4) of~\cite{varga2026}; the L checks the premise of Proposition~1 in its lease
variant; the G checks the guarded-transition extension; C01 and C02 run only in a WorkflowManager connected to a
ServiceRegistry. The decision rule is REJECT $>$ CLARIFY $>$ REVISE $>$ ACCEPT, and only ACCEPT can be registered.
""" + t


SERVICES["workflowRecipe"]["extra_sd"] = GUARD_GRAMMAR + "\n%%CODES%%\n"
SERVICES["workflowRecipe"]["structs"] = ["Recipe"] + SERVICES["workflowRecipe"]["structs"]
