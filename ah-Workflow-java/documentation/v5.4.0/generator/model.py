"""Information model of the workflow services: primitives and structures, taken from the Java reference source
(workflow-model: eu.arrowhead.workflow.model / .wire; workflow-manager: api.dto; workflow-configurator: service)."""

# Primitive types: name -> (abstract description used in SDs, JSON representation used in IDDs)
PRIMS = {
    "Boolean": ("One out of \\texttt{true} or \\texttt{false}.", "JSON \\texttt{true} or \\texttt{false}."),
    "Integer": ("A whole number.", "JSON number without fraction."),
    "Number": ("A real number.", "JSON number."),
    "String": ("A chain of characters.", "JSON string (UTF-8)."),
    "Name": ("An identifier of a recipe, action, step, parameter or resource: a letter followed by at most 62 letters, digits, \\texttt{-} or \\texttt{\\_}.",
             "JSON string matching \\texttt{\\^{}[A-Za-z][A-Za-z0-9\\_-]\\{0,62\\}\\$}."),
    "Version": ("A recipe version in the form MAJOR.MINOR.PATCH.", "JSON string matching \\texttt{\\^{}\\textbackslash{}d+\\textbackslash{}.\\textbackslash{}d+\\textbackslash{}.\\textbackslash{}d+\\$}."),
    "SystemName": ("The name of an Arrowhead system as it appears in its certificate and in the ServiceRegistry.", "JSON string."),
    "ServiceDefinition": ("The name of an Arrowhead service definition. Names are compared case-insensitively and without \\texttt{-}, \\texttt{\\_} and spaces, so \\texttt{temperature-read} and \\texttt{temperatureRead} denote the same service.", "JSON string."),
    "SessionId": ("The identifier the WorkflowManager gives a session.", "JSON number (64-bit integer); a path segment when used in a path."),
    "WorkItemId": ("The identifier of one attempt to run one step of one session.", "JSON string holding a UUID; a path segment when used in a path."),
    "DateTime": ("Pinpoints a moment in time (UTC).", "JSON string in the RFC 3339 format~\\cite{rfc3339}, e.g. \\texttt{2026-11-23T10:15:30Z}."),
    "Duration": ("A length of time in seconds.", "JSON number of seconds."),
    "JsonValue": ("Any value: object, array, string, number, Boolean or null.", "Any JSON value~\\cite{rfc8259}."),
    "JsonObject": ("A set of named values.", "JSON object."),
    "Expression": ("A side-effect free condition over the session parameters and stored results (Section on guard expressions).", "JSON string in the guard grammar."),
    "Template": ("A value in which strings of the form \\texttt{\\$\\{param.$p$\\}} or \\texttt{\\$\\{result.$s$.path\\}} are replaced by a session parameter or by (a part of) a stored step result.", "Any JSON value; references are JSON strings."),
    "List": ("An ordered collection of items of the type given in angle brackets.", "JSON array."),
    "Map": ("A collection of key/value pairs of the types given in angle brackets.", "JSON object whose member names are the keys."),
}

# Structures: name -> dict(kind='struct'|'enum'|'alias', desc, fields=[(field, type, desc, mandatory)], values=[(v, desc)], of=type)
S = {}


def struct(name, desc, *fields):
    S[name] = {"kind": "struct", "desc": desc, "fields": [f if len(f) == 4 else (*f, True) for f in fields]}


def enum(name, desc, *values):
    S[name] = {"kind": "enum", "desc": desc, "values": list(values)}


def alias(name, of, desc):
    S[name] = {"kind": "alias", "desc": desc, "of": of}


# ---------------------------------------------------------------- recipe (workflow-model Recipe)
struct("Recipe", "A workflow recipe $W$~\\cite{varga2026}: named, versioned and immutable once registered. Actions run in sequence (\\texttt{firstAction}, then each action's \\texttt{next}); the steps of one action form a directed acyclic graph.",
       ("name", "Name", "Recipe name.", True),
       ("version", "Version", "Recipe version; a registered name and version never changes content.", True),
       ("description", "String", "Free text.", False),
       ("firstAction", "Name", "The action $\\alpha_0$ that is active when a session starts.", True),
       ("parameters", "List<Parameter>", "Declared session parameters $\\rho$.", False),
       ("resources", "List<Resource>", "Shared resources (tools) used under leases.", False),
       ("dispatch", "DispatchMode", "Default dispatch mode of the recipe; overridden per action or per session.", False),
       ("actions", "List<Action>", "The actions $A$ with their steps.", True))
struct("Parameter", "A declared session parameter.",
       ("name", "Name", "Parameter name, referenced as \\texttt{\\$\\{param.name\\}}.", True),
       ("type", "String", "Informative type: \\texttt{string}, \\texttt{number}, \\texttt{boolean} or \\texttt{object}.", False),
       ("required", "Boolean", "Whether \\texttt{workflowInstance.start} refuses a session without it.", False))
struct("Resource", "A shared resource that steps reserve, use and release under a lease (Proposition~1 of~\\cite{varga2026}, lease variant).",
       ("name", "Name", "Resource name.", True),
       ("leaseDurationSeconds", "Duration", "Lease duration granted by \\texttt{workflowLease}; must exceed the longest tool-using step.", False),
       ("provider", "SystemName", "Lease provider; the WorkflowManager when absent.", False))
struct("Action", "An action $\\alpha$: a group of steps run in one work centre.",
       ("name", "Name", "Action name.", True),
       ("next", "Name", "The action that becomes active when this one finishes; absent for the last action.", False),
       ("workCentre", "String", "Informative work-centre identifier.", False),
       ("dispatch", "DispatchMode", "Dispatch mode of this action's steps.", False),
       ("steps", "List<Step>", "The steps $S_\\alpha$.", True))
struct("Step", "A step $s$: one service exchange.",
       ("name", "Name", "Step name, unique in the recipe.", True),
       ("service", "ServiceRequirement", "$\\theta(s)$: the service the step consumes.", True),
       ("next", "List<Name>", "Successors in the same action (the transitions $s \\rightarrow s'$).", False),
       ("dep", "List<Name>", "$dep(s)$: steps whose results the step reads. Each must run before $s$.", False),
       ("executor", "ExecutorRequirement", "$\\eta(s)$: which executor may run the step; absent means any executor declaring the service.", False),
       ("params", "Map<String,JsonValue>", "Static parameters passed unchanged to the provider.", False),
       ("input", "Template", "$\\kappa_s$: the request body sent to the provider, composed from $\\rho$ and $\\xi|dep(s)$.", False),
       ("policy", "Policy", "Execution policy (quantity, timeout, retry, lease, local cloud).", False),
       ("guards", "Map<Name,Expression>", "Conditions on the transitions to successors (extension x-guard). A successor without a guard is always taken.", False))
struct("ServiceRequirement", "Requirements on the provider of the step's service, passed to orchestration.",
       ("serviceDefinition", "ServiceDefinition", "The service to consume.", True),
       ("interfaces", "List<String>", "Accepted interface template names, e.g. \\texttt{generic\\_https}.", False),
       ("minVersion", "String", "Lowest accepted service version.", False),
       ("maxVersion", "String", "Highest accepted service version.", False),
       ("metadata", "Map<String,JsonValue>", "Metadata requirements on the service instance.", False),
       ("qos", "Map<String,JsonValue>", "QoS requirements forwarded to orchestration.", False),
       ("requiresResults", "List<Name>", "Steps whose results the provider needs; each must be in $dep(s)$ (check D04).", False))
struct("ExecutorRequirement", "$\\eta(s)$: constraints on the executor that runs a step.",
       ("capability", "String", "A capability the executor must declare in \\texttt{workflowCapabilities}.", False),
       ("workstation", "String", "The executor's workstation.", False),
       ("system", "SystemName", "A named executor system.", False))
struct("Policy", "Execution policy of a step. Every field other than \\texttt{lease} and \\texttt{localCloudOnly} is an extension outside the proven model.",
       ("quantity", "Integer", "Number of executions; only 1 is accepted (check M01).", False),
       ("timeoutSeconds", "Duration", "Extension x-timeout: the step fails when it runs longer.", False),
       ("nominalDurationSeconds", "Duration", "Expected duration, used to check lease durations (check L04).", False),
       ("retry", "Retry", "Extension x-rebind: behaviour on \\texttt{ERROR}.", False),
       ("lease", "LeasePolicy", "The step's role for a shared resource.", False),
       ("localCloudOnly", "Boolean", "Whether orchestration may return providers of other clouds (default true).", False))
struct("Retry", "Extension x-rebind.",
       ("maxAttempts", "Integer", "Maximum attempts of the step in one session.", False),
       ("onError", "OnError", "What an \\texttt{ERROR} report causes.", False))
struct("LeasePolicy", "The role of a step for a shared resource.",
       ("resource", "Name", "A declared resource.", True),
       ("mode", "LeaseMode", "\\texttt{reserve}, \\texttt{use} or \\texttt{release}.", True))
enum("DispatchMode", "How a step reaches an executor.",
     ("push", "The WorkflowManager selects an executor and calls \\texttt{workflowExecution.start} (default)."),
     ("pull", "The WorkflowManager publishes the work item; an executor claims it with \\texttt{workflowWorkItem.claim}."),
     ("event", "Reserved for EventHandler notification; currently handled as \\texttt{pull}."))
enum("OnError", "Reaction to an \\texttt{ERROR} report.",
     ("rebind", "Exclude the executor for this session and bind the step again."),
     ("abort", "Fail the session."))
enum("LeaseMode", "Role of a step for a shared resource.",
     ("reserve", "Reserves the resource; its result carries the lease token."),
     ("use", "Uses the resource; the executor renews the lease immediately before the invocation."),
     ("release", "Releases the resource."))

# ---------------------------------------------------------------- validation
struct("ValidationReport", "The result of validating a recipe. The verdict is the worst outcome of the findings (REJECT $>$ CLARIFY $>$ REVISE $>$ ACCEPT).",
       ("recipe", "String", "\\texttt{name@version} of the validated recipe.", True),
       ("verdict", "Verdict", "Overall verdict; only \\texttt{ACCEPT} can be registered.", True),
       ("findings", "List<Finding>", "Individual findings; empty when the recipe has none.", True))
struct("Finding", "One validator finding.",
       ("code", "String", "Check code, e.g. \\texttt{D03}; the codes are listed in the \\texttt{workflowRecipe} SD.", True),
       ("outcome", "Verdict", "Outcome of this finding.", True),
       ("basis", "String", "The rule the check enforces, e.g. \\texttt{W1}, \\texttt{V9}, \\texttt{schema}.", True),
       ("location", "String", "Where the finding applies, e.g. \\texttt{step evaluate}.", True),
       ("message", "String", "Human-readable explanation.", True))
enum("Verdict", "Validator outcome.",
     ("ACCEPT", "The recipe can be registered (also used for informational findings)."),
     ("REVISE", "The recipe must be changed."),
     ("CLARIFY", "The recipe refers to something that cannot be confirmed (undeclared parameter, unknown service or executor)."),
     ("REJECT", "The recipe cannot be used at all."))

# ---------------------------------------------------------------- workflowRecipe
struct("RecipeRecord", "A registered recipe as stored by the WorkflowManager.",
       ("id", "Integer", "Database identifier.", True),
       ("name", "Name", "Recipe name.", True),
       ("version", "Version", "Recipe version.", True),
       ("status", "RecipeStatus", "\\texttt{ACTIVE} or \\texttt{RETIRED}.", True),
       ("registeredBy", "SystemName", "The system that registered the recipe.", True),
       ("recipe", "Recipe", "The canonical recipe document.", True),
       ("createdAt", "DateTime", "Registration time.", True))
enum("RecipeStatus", "Life-cycle state of a registered recipe.",
     ("ACTIVE", "Sessions may be started."),
     ("RETIRED", "Kept for traceability; new sessions are refused with 409."))
struct("RecipeList", "Registered recipes in registration order.",
       ("entries", "List<RecipeRecord>", "The recipes.", True),
       ("count", "Integer", "Number of entries.", True))
struct("RecipeRegistration", "Result of a registration.",
       ("recipe", "RecipeRecord", "The stored recipe; null when the recipe was not accepted.", False),
       ("validation", "ValidationReport", "The validation report.", True),
       ("created", "Boolean", "True when a new version was stored; false when identical content already existed.", True))
struct("RecipeKey", "Identifies one registered recipe version.",
       ("name", "Name", "Recipe name.", True),
       ("version", "Version", "Recipe version; for \\texttt{compile} the latest active version when absent.", True))
struct("RecipeFilter", "Optional filter of the recipe query.",
       ("name", "Name", "Only versions of this recipe.", False))
struct("ChoreographerPlan", "A recipe compiled to the Choreographer plan request (\\texttt{PlanRequestDTO}), for option A of the coexistence with the Choreographer.",
       ("name", "Name", "Plan name: the recipe name.", True),
       ("firstActionName", "Name", "First action.", True),
       ("actions", "List<PlanAction>", "Actions.", True),
       ("lossySteps", "List<String>", "Informational: steps whose input, guards, dependencies or policy the plan cannot express. Not part of the Choreographer request.", False))
struct("PlanAction", "\\texttt{ActionRequestDTO} of the Choreographer.",
       ("name", "Name", "Action name.", True),
       ("nextActionName", "Name", "Next action.", False),
       ("firstStepNames", "List<Name>", "Steps without predecessors.", True),
       ("steps", "List<PlanStep>", "Steps.", True))
struct("PlanStep", "\\texttt{StepRequestDTO} of the Choreographer.",
       ("name", "Name", "Step name.", True),
       ("quantity", "Integer", "Always 1.", False),
       ("serviceRequirement", "PlanServiceRequirement", "Service of the step.", True),
       ("localCloudOnly", "Boolean", "From the step policy.", False),
       ("staticParameters", "Map<String,String>", "The step's \\texttt{params} as strings.", False),
       ("nextStepNames", "List<Name>", "Successors.", False))
struct("PlanServiceRequirement", "Subset of the orchestration service requirement.",
       ("serviceDefinition", "ServiceDefinition", "Service.", True),
       ("versions", "List<String>", "Accepted versions.", False),
       ("interfaceTemplateNames", "List<String>", "Accepted interface templates.", False))

# ---------------------------------------------------------------- workflowInstance
struct("StartSessionRequest", "Starts one session (one execution of one recipe version for one subject).",
       ("recipe", "Name", "Recipe name; may be omitted when \\texttt{product} is given.", False),
       ("version", "Version", "Recipe version; the latest active version when absent.", False),
       ("subject", "String", "What the session works on: product unit, control period or batch.", False),
       ("parameters", "JsonObject", "Session parameters $\\rho$; required parameters must be present.", False),
       ("dispatch", "DispatchMode", "Overrides the recipe's dispatch mode.", False),
       ("product", "SystemName", "A product system providing \\texttt{workflowProduct}: the recipe and parameters are retrieved from it, and the results are stored back at Z-Close.", False))
struct("Session", "The state $z = (M, M_A, \\beta, \\varepsilon, \\xi, status)$ of a session, as seen from outside.",
       ("id", "SessionId", "Session identifier.", True),
       ("recipe", "Name", "Recipe name.", True),
       ("version", "Version", "Recipe version.", True),
       ("subject", "String", "Subject of the session.", False),
       ("owner", "SystemName", "The system that started the session; it may abort it.", True),
       ("status", "SessionStatus", "\\texttt{running}, \\texttt{completed} or \\texttt{failed}.", True),
       ("dispatch", "DispatchMode", "Effective dispatch mode.", True),
       ("failure", "String", "Why the session failed.", False),
       ("extensions", "List<String>", "Extensions used (\\texttt{x-rebind}, \\texttt{x-abort}, \\texttt{x-timeout}, \\texttt{x-skip}, \\ldots); empty when the run stays inside the proven model.", True),
       ("stepMarking", "Map<Name,String>", "$M$: \\texttt{inactive}, \\texttt{enabled}, \\texttt{running}, \\texttt{completed} or \\texttt{skipped} per step.", True),
       ("actionMarking", "Map<Name,String>", "$M_A$: \\texttt{inactive}, \\texttt{active} or \\texttt{completed} per action.", True),
       ("modelTransitions", "Integer", "Number of model transitions so far.", True),
       ("transitionBound", "Integer", "Theorem~1 bound $3|S| + 2|A|$.", True),
       ("product", "SystemName", "Product system, when the session came from one.", False),
       ("createdAt", "DateTime", "Start time.", True),
       ("updatedAt", "DateTime", "Time of the last transition.", True))
enum("SessionStatus", "Status of a session. \\texttt{completed} and \\texttt{failed} are final.",
     ("running", "Some rule may still apply."),
     ("completed", "Z-Close fired: every action completed."),
     ("failed", "S-FailBind, S-FailInvoke or an extension (abort, timeout) ended the session."))
struct("SessionList", "Sessions, newest first.",
       ("entries", "List<Session>", "The sessions.", True),
       ("count", "Integer", "Number of entries.", True))
struct("SessionRef", "Identifies a session; \\oref{abort} also takes an optional reason.",
       ("id", "SessionId", "Session identifier.", True),
       ("reason", "String", "Reason recorded with an abort.", False))
struct("SessionFilter", "Optional filter of \\oref{query}.",
       ("status", "SessionStatus", "Only sessions in this status.", False))
struct("Trace", "The run of a session: the transitions and the data the rules produced.",
       ("sessionId", "SessionId", "Session identifier.", True),
       ("status", "SessionStatus", "Current status.", True),
       ("trace", "List<Transition>", "Transitions in order.", True),
       ("results", "Map<Name,JsonValue>", "$\\xi$: stored step results.", True),
       ("bindings", "Map<Name,JsonValue>", "$\\beta$: orchestration result per bound step.", True),
       ("executorAssignment", "Map<Name,SystemName>", "$\\varepsilon$: executor per step.", True),
       ("runner", "Map<Name,SystemName>", "The system that ran each step.", True),
       ("modelTransitions", "Integer", "Transitions of the proven rule set.", True),
       ("transitionBound", "Integer", "Theorem~1 bound.", True),
       ("withinModel", "Boolean", "True when no extension was used, so Theorems~1--2 apply.", True))
struct("Transition", "One transition of the session.",
       ("seq", "Integer", "Sequence number from 1.", True),
       ("rule", "Rule", "The rule that fired.", True),
       ("target", "Name", "The step or action it applied to.", False),
       ("detail", "String", "Additional information, e.g. the executor or failure.", False),
       ("at", "DateTime", "When it fired.", True))
enum("Rule", "Transition rules: the eight rules of~\\cite{varga2026} and the extensions (\\texttt{x-}).",
     ("A-Start", "An action becomes active after its predecessor completed."),
     ("S-Enable", "A step becomes enabled when its action is active and its predecessors completed."),
     ("S-Bind", "An enabled step is bound to a provider (and executor) and becomes running."),
     ("S-Done", "A running step completed; its result is stored."),
     ("A-Finish", "Every step of an action completed."),
     ("Z-Close", "Every action completed: the session is completed."),
     ("S-FailBind", "No executor/provider could be bound: the session fails."),
     ("S-FailInvoke", "The invocation failed: the session fails."),
     ("x-rebind", "Extension: the step is bound again after \\texttt{ERROR} or an unreachable executor."),
     ("x-abort", "Extension: the session was aborted."),
     ("x-timeout", "Extension: a step exceeded its timeout."),
     ("x-skip", "Extension: a step was skipped because no guarded transition to it was taken."))

# ---------------------------------------------------------------- workflowWorkItem / workflowExecution
struct("WorkItem", "One attempt to run one step: what an executor needs to run it.",
       ("workItemId", "WorkItemId", "Identifier of the attempt.", True),
       ("sessionId", "SessionId", "Session.", True),
       ("recipe", "String", "\\texttt{name@version} of the recipe.", True),
       ("action", "Name", "Action of the step.", True),
       ("step", "Name", "Step name.", True),
       ("attempt", "Integer", "Attempt number from 1.", True),
       ("service", "ServiceRequirement", "$\\theta(s)$.", True),
       ("executor", "ExecutorRequirement", "$\\eta(s)$.", False),
       ("params", "Map<String,JsonValue>", "Static parameters of the step.", False),
       ("input", "Template", "Input template $\\kappa_s$.", False),
       ("policy", "Policy", "Step policy.", False),
       ("leaseDurationSeconds", "Duration", "Lease duration of the step's resource.", False),
       ("parameters", "JsonObject", "Session parameters $\\rho$ (included for convenience; also available from \\texttt{workflowContext}).", False),
       ("dependencyResults", "Map<Name,JsonValue>", "$\\xi|dep(s)$ (also available from \\texttt{workflowContext}).", False))
struct("ClaimRequest", "What the claiming executor can run. All fields are optional; the executor's identity comes from its certificate.",
       ("capabilities", "List<String>", "Declared capabilities $cap(w)$.", False),
       ("services", "List<ServiceDefinition>", "Services the executor can run; empty or \\texttt{*} means any.", False),
       ("workstation", "String", "Workstation of the executor.", False))
struct("WorkItemReport", "A report on a held work item.",
       ("workItemId", "WorkItemId", "The work item.", True),
       ("status", "ReportStatus", "What happened.", True),
       ("binding", "JsonValue", "With \\texttt{BOUND}: the provider the executor was given by orchestration.", False),
       ("result", "JsonValue", "With \\texttt{SUCCESS}: $\\xi(s)$, the provider's response.", False),
       ("message", "String", "Explanation, mandatory in practice for failures.", False))
enum("ReportStatus", "Executor reports and the rules they cause.",
     ("BOUND", "Orchestration succeeded (completes S-Bind)."),
     ("BIND\\_FAILED", "Orchestration returned no provider: the next executor is tried, otherwise S-FailBind."),
     ("SUCCESS", "The provider answered (S-Done)."),
     ("ERROR", "Recoverable failure: x-rebind when the step has a retry policy, otherwise S-FailInvoke."),
     ("FATAL\\_ERROR", "Unrecoverable failure (S-FailInvoke)."),
     ("ABORTED", "The work item was aborted."))
struct("WorkItemRef", "Identifies a work item.",
       ("workItemId", "WorkItemId", "The work item.", True))
struct("ExecutorInfo", "What an executor declares about itself.",
       ("name", "SystemName", "Executor system name.", True),
       ("capabilities", "List<String>", "Capabilities $cap(w)$.", True),
       ("services", "List<ServiceDefinition>", "Services it can run; \\texttt{*} means any.", True),
       ("workstation", "String", "Workstation.", False),
       ("mode", "DispatchMode", "\\texttt{push} or \\texttt{pull}.", True))
struct("AbortRequest", "Aborts a work item on an executor.",
       ("workItemId", "WorkItemId", "The work item.", True),
       ("sessionId", "SessionId", "Its session.", True),
       ("reason", "String", "Why.", False))
struct("ExecutionState", "State of a work item on an executor.",
       ("workItemId", "WorkItemId", "The work item.", True),
       ("state", "ExecutorState", "Local state.", True),
       ("message", "String", "Last message.", False))
enum("ExecutorState", "Local state of a work item on an executor. The authoritative state is held by the WorkflowManager.",
     ("ACCEPTED", "Received, not yet started."),
     ("RUNNING", "Being run."),
     ("DONE", "Run finished and reported (whatever the report was)."),
     ("UNKNOWN", "The executor does not know the work item, e.g. after a restart."))

# ---------------------------------------------------------------- workflowContext
alias("Parameters", "JsonObject", "The session parameters $\\rho$.")
alias("StepResults", "Map<Name,JsonValue>", "$\\xi|dep(s)$: the stored results of the steps in $dep(s)$, by step name.")
struct("PutResultRequest", "Stores $\\xi(s)$ for a held work item.",
       ("workItemId", "WorkItemId", "The work item.", True),
       ("result", "JsonValue", "The step's result.", True))

# ---------------------------------------------------------------- workflowLease
struct("LeaseRequest", "Request of the lease operations. Which fields are used depends on the operation.",
       ("resource", "Name", "\\oref{reserve}: the resource.", False),
       ("sessionId", "SessionId", "\\oref{reserve}: the session that will hold the lease (released on failure).", False),
       ("durationSeconds", "Duration", "\\oref{reserve}: lease duration; the configured default when absent or not positive.", False),
       ("ticket", "String", "\\oref{poll}: the ticket of a queued reservation.", False),
       ("token", "String", "\\oref{renew} and \\oref{release}: the lease token.", False))
struct("LeaseResponse", "State of a reservation.",
       ("status", "LeaseStatus", "State.", True),
       ("resource", "Name", "Resource.", False),
       ("token", "String", "The lease token while \\texttt{GRANTED}; it is the capability to use the resource.", False),
       ("ticket", "String", "Ticket for \\oref{poll}.", False),
       ("leaseUntil", "DateTime", "Expiry of a granted lease.", False),
       ("message", "String", "Explanation, e.g. \\texttt{No valid lease}.", False))
enum("LeaseStatus", "State of a reservation.",
     ("GRANTED", "The holder may use the resource until \\texttt{leaseUntil}."),
     ("QUEUED", "Waiting; granted in FIFO order."),
     ("RELEASED", "Released by the holder, or by compensation when the session failed."),
     ("EXPIRED", "Not renewed in time; the next queued reservation is granted."),
     ("UNKNOWN", "Unknown ticket or token."))

# ---------------------------------------------------------------- workflowManagement
struct("WorklogFilter", "Optional filter.",
       ("sessionId", "SessionId", "Only entries/items of this session.", False))
struct("WorklogList", "Worklog entries.",
       ("entries", "List<WorklogEntry>", "Entries.", True),
       ("count", "Integer", "Number of entries.", True))
struct("WorklogEntry", "One audit entry of the WorkflowManager.",
       ("id", "Integer", "Identifier.", True),
       ("createdAt", "DateTime", "Time.", True),
       ("sessionId", "SessionId", "Session, if any.", False),
       ("recipe", "String", "Recipe, if any.", False),
       ("step", "Name", "Step, if any.", False),
       ("message", "String", "Event.", True),
       ("detail", "String", "Detail.", False))
struct("ExecutorList", "Executors known to the WorkflowManager.",
       ("entries", "List<ExecutorEndpoint>", "Executors.", True),
       ("count", "Integer", "Number of entries.", True))
struct("ExecutorEndpoint", "An executor $w \\in WE$ with $cap(w)$.",
       ("name", "SystemName", "Executor.", True),
       ("scheme", "String", "\\texttt{https} or \\texttt{http}.", True),
       ("address", "String", "Address.", True),
       ("port", "Integer", "Port.", True),
       ("basePath", "String", "Base path of \\texttt{workflowExecution}.", True),
       ("capabilities", "List<String>", "Capabilities.", True),
       ("services", "List<ServiceDefinition>", "Runnable services.", True),
       ("workstation", "String", "Workstation.", False),
       ("mode", "DispatchMode", "Mode.", True),
       ("source", "String", "\\texttt{registry} or \\texttt{static}.", True))
struct("WorkItemList", "Work items.",
       ("entries", "List<WorkItemRecord>", "Work items.", True),
       ("count", "Integer", "Number of entries.", True))
struct("WorkItemRecord", "Book-keeping of one work item.",
       ("workItemId", "WorkItemId", "Identifier.", True),
       ("sessionId", "SessionId", "Session.", True),
       ("action", "Name", "Action.", True),
       ("step", "Name", "Step.", True),
       ("executor", "SystemName", "Assigned or claiming executor.", False),
       ("attempt", "Integer", "Attempt.", True),
       ("state", "WorkItemState", "State.", True),
       ("message", "String", "Last message.", False),
       ("createdAt", "DateTime", "Created.", True),
       ("updatedAt", "DateTime", "Updated.", True))
enum("WorkItemState", "State of a work item at the WorkflowManager.",
     ("OPEN", "Published for claiming (pull)."),
     ("DISPATCHED", "Sent to an executor (push)."),
     ("CLAIMED", "Claimed by an executor (pull)."),
     ("BOUND", "The executor reported a provider."),
     ("DONE", "Completed."),
     ("FAILED", "Failed."),
     ("ABORTED", "Aborted."))

# ---------------------------------------------------------------- workflowConfiguration
struct("CloudList", "The local clouds the editor can design workflows for.",
       ("entries", "List<CloudEntry>", "Clouds.", True),
       ("sources", "List<SourceState>", "Availability of each source; an unavailable source does not hide the others.", True),
       ("note", "String", "Explanation when a fallback was used.", False))
struct("CloudEntry", "One configured local cloud.",
       ("id", "String", "\\texttt{live}, \\texttt{lcc:<design>} or \\texttt{solc:<design>:<cloud>}.", True),
       ("name", "String", "Display name.", True),
       ("cloud", "String", "Local cloud name.", True),
       ("source", "SystemName", "ServiceRegistry, LocalCloudConfigurator or SoLocalCloudConfigurator.", True),
       ("live", "Boolean", "Whether this cloud's ServiceRegistry is the one reachable from here.", True),
       ("description", "String", "Description.", True),
       ("systems", "List<CloudSystem>", "Systems of the cloud.", True),
       ("revision", "Integer", "Design revision (LocalCloudConfigurator designs).", False),
       ("updatedAt", "DateTime", "Design update time.", False))
struct("CloudSystem", "A system of a cloud.",
       ("name", "SystemName", "System.", True),
       ("running", "Boolean", "Whether it is registered in the live cloud.", True),
       ("kind", "String", "Component kind in a design.", False))
struct("SourceState", "Availability of a source.",
       ("system", "SystemName", "Source system.", True),
       ("operation", "String", "Operation used.", True),
       ("available", "Boolean", "Whether it answered.", True),
       ("status", "Integer", "HTTP status when unavailable.", False),
       ("error", "String", "Error when unavailable.", False))
struct("CloudRef", "Identifies a cloud of \\oref{list-clouds}.",
       ("id", "String", "Cloud identifier.", True))
struct("ServiceList", "The microservices of a cloud.",
       ("cloud", "String", "Cloud identifier.", True),
       ("name", "String", "Cloud name.", True),
       ("live", "Boolean", "Live cloud.", True),
       ("systems", "List<CloudSystem>", "Systems.", True),
       ("entries", "List<ServiceEntry>", "Service instances.", True),
       ("note", "String", "Why the list may be incomplete.", False))
struct("ServiceEntry", "A service instance offered in the cloud.",
       ("provider", "SystemName", "Provider.", True),
       ("serviceDefinition", "ServiceDefinition", "Service.", True),
       ("version", "String", "Service version.", True),
       ("metadata", "JsonObject", "Service metadata.", True),
       ("interfaces", "List<InterfaceEntry>", "Interfaces.", True),
       ("operations", "List<String>", "Operation names.", True))
struct("InterfaceEntry", "An interface of a service instance.",
       ("templateName", "String", "Interface template.", True),
       ("policy", "String", "Interface policy, e.g. \\texttt{CERT\\_AUTH}.", True))
struct("ExecutorCapabilityList", "Executors registered in the live cloud.",
       ("entries", "List<ExecutorCapability>", "Executors.", True))
struct("ExecutorCapability", "An executor's declared capabilities.",
       ("name", "SystemName", "Executor.", True),
       ("capabilities", "List<String>", "Capabilities.", True),
       ("services", "List<ServiceDefinition>", "Runnable services.", True),
       ("mode", "DispatchMode", "Mode.", True),
       ("workstation", "String", "Workstation.", False))
struct("StoreResult", "Result of storing an edited workflow.",
       ("stored", "Boolean", "True when the WorkflowManager registered it.", True),
       ("report", "ValidationReport", "The validation report.", True),
       ("created", "Boolean", "True for a new version; false for identical content.", False),
       ("recipe", "RecipeRecord", "The stored recipe.", False))

# ---------------------------------------------------------------- workflowProduct
struct("ProcessStepRequest", "Asks the product for its recipe fragment for one workstation.",
       ("workstationId", "String", "The \\texttt{workflow.workstation.id} of the requesting WorkflowManager (empty when not configured).", True))
struct("ProcessOrder", "The product's order for this workstation.",
       ("recipe", "Recipe", "The recipe fragment; validated and registered like any recipe.", True),
       ("parameters", "JsonObject", "Session parameters $\\rho$.", False),
       ("subject", "String", "Product identity (subject of the session).", False))
struct("ProcessStepResult", "What the WorkflowManager stores back to the product at Z-Close (data at its source).",
       ("workstationId", "String", "The workstation of the WorkflowManager.", True),
       ("sessionId", "SessionId", "Session.", True),
       ("recipe", "String", "\\texttt{name@version}.", True),
       ("status", "SessionStatus", "Final status; only \\texttt{completed} sessions are stored back.", True),
       ("results", "Map<Name,JsonValue>", "$\\xi$: the stored step results.", True))

# ---------------------------------------------------------------- generalManagement (common library)
struct("LogRequest", "Filter of \\oref{get-log}; every field is optional.",
       ("pagination", "Page", "Paging.", False),
       ("from", "DateTime", "Entries at or after.", False),
       ("to", "DateTime", "Entries at or before.", False),
       ("severity", "String", "Minimum severity (\\texttt{TRACE}\\ldots\\texttt{ERROR}).", False),
       ("logger", "String", "Logger name filter.", False))
struct("Page", "Paging.",
       ("page", "Integer", "Page number from 0.", False),
       ("size", "Integer", "Page size.", False),
       ("direction", "String", "\\texttt{ASC} or \\texttt{DESC}.", False),
       ("sortField", "String", "Sort field.", False))
struct("LogEntryList", "Log entries.",
       ("entries", "List<LogEntry>", "Entries.", True),
       ("count", "Integer", "Total number of matching entries.", True))
struct("LogEntry", "One log entry.",
       ("logId", "String", "Identifier.", True),
       ("entryDate", "DateTime", "Time.", True),
       ("logger", "String", "Logger.", True),
       ("severity", "String", "Severity.", True),
       ("message", "String", "Message.", True),
       ("exception", "String", "Exception, if any.", False))
struct("ConfigKeys", "Configuration keys to read.",
       ("keys", "List<String>", "Property names; only keys on the system's allow-list are returned.", False))
struct("KeyValues", "Configuration values.",
       ("map", "Map<String,String>", "Key/value pairs.", True))

# ---------------------------------------------------------------- errors (common library)
struct("ErrorMessage", "Error response of every operation (common library \\texttt{ErrorMessageDTO}).",
       ("errorMessage", "String", "Explanation.", True),
       ("errorCode", "Integer", "HTTP status code.", True),
       ("exceptionType", "String", "\\texttt{INVALID\\_PARAMETER}, \\texttt{AUTH}, \\texttt{FORBIDDEN}, \\texttt{DATA\\_NOT\\_FOUND}, \\texttt{EXTERNAL\\_SERVER\\_ERROR}, \\ldots", True),
       ("origin", "String", "The operation that failed, e.g. \\texttt{POST /workflowmanager/work-item/report}.", False))
