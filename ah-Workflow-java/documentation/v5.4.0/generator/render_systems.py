r"""System Description (SysD) and System Design Description (SysDD) of WorkflowManager, WorkflowExecutor and
WorkflowConfigurator. Facts are taken from the *SystemInfo, *Constants, *Defaults classes, deployment/*.properties,
the engine/runtime/service packages and ARCHITECTURE.md sections 10, 11 and 15."""

from latex import EDITION, preamble, front, ending, table, figure, block_diagram, tt, esc

SDDOC = lambda sysname, svc: r"\texttt{%s-v%s-%s-SD}, \texttt{\ldots-HTTP-IDD}" % (sysname, EDITION, svc)

CORE_CONSUMED_TEXT = r"""All three workflow systems use the core systems the same way: at start-up they log in at the Authentication
system (\texttt{identity}) and register their services in the ServiceRegistry (\texttt{serviceDiscovery.register});
on every incoming request they verify the caller's identity token (\texttt{identity.identity-verify}) and, with
\texttt{service.authorization.enabled}, the caller's grant (\texttt{authorization.verify} of ConsumerAuthorization);
at shutdown they revoke their services and log out."""

SECURITY_COMMON = r"""
\subsection{Security model}
\begin{itemize}
\item \textbf{Secure mode only in deployment.} The deployment configuration (\texttt{deployment/*.properties}) enables
TLS with client authentication (\texttt{server.ssl.client-auth=need}, TLS 1.3 and 1.2), outsourced authentication with
identity tokens bound to the client certificate, interface policy \texttt{CERT\_AUTH} and the CRL check. The packaged
defaults without TLS exist for development and unit tests (\texttt{workflow.standalone}) and must not be used in a
cloud.
\item \textbf{Certificates.} The system has an Arrowhead system certificate
\texttt{<System>.<cloud>.<organisation>.arrowhead.eu} issued by the local cloud CA, kept in a PKCS\#12 key store; the
trust store holds the cloud CA. Non-Arrowhead certificates are refused at the TLS layer.
\item \textbf{Provider-side authorization.} Every operation of the registered services (other than management) is
checked against ConsumerAuthorization (\texttt{authorization.verify}) for the calling system, the service definition
and the operation (route tables \texttt{ServiceOperations} and \texttt{ConfiguratorOperations}). A missing grant gives
403.
\item \textbf{Management.} \texttt{generalManagement} and \texttt{workflowManagement} are protected by
\texttt{management.policy=sysop-only}.
\item \textbf{Data protection.} All service traffic is encrypted by TLS. The WorkflowManager's database connection uses
TLS with server certificate verification (\texttt{sslMode=verify-full}). Secrets (store, identity and database
passwords, the console password hash) come from container secrets (\texttt{configtree:/run/secrets/}), not from the
image.
\end{itemize}
"""

SYSTEMS = {}

# ======================================================================================= WorkflowManager
SYSTEMS["WorkflowManager"] = dict(
    module="workflow-manager", port="8482", abbrev="WfM",
    summary=r"keeps the books of workflows: it holds recipes and sessions, validates recipes, advances sessions with the transition rules of the formal model, dispatches steps to WorkflowExecutors and reports to the product",
    overview=r"""The WorkflowManager (WfM) is the book-keeper of the Eclipse Arrowhead workflow support systems. It
stores workflow \emph{recipes} (which services are consumed, in which order and under which conditions), runs
\emph{sessions} of them for products, control periods or batches, and keeps the session state of the formal execution
model~\cite{varga2026}. It never invokes equipment itself: each enabled step is handed to a WorkflowExecutor, which has
the provider orchestrated for itself and reports back. The WorkflowManager is generic, one instance per work-centre
local cloud, configured only by its workstation identifier.""",
    prior_art=r"""Derhamy's Workflow Manager, a stateless book-keeper and blackboard that can be cloned per work
centre~\cite{derhamy2018,derhamy2018workflow}; Garcia Represa's workflow management system and the coexistence of
orchestration-style and choreography-style workflow management in one SoS~\cite{represa2022}; Varga's formal recipe
and session model with its transition rules and proven guarantees~\cite{varga2026}; and the Arrowhead Choreographer,
whose plans the WorkflowManager can compile recipes to.""",
    use=r"""\begin{enumerate}
\item An engineer designs a workflow in the WorkflowConfigurator, which validates and registers it through
\texttt{workflowRecipe}.
\item A product, line controller or upper WorkflowManager starts a session through \texttt{workflowInstance}, either
naming the recipe or naming a product system that provides the recipe through \texttt{workflowProduct}.
\item The WorkflowManager enables steps, selects executors by their declared capabilities (push) or publishes work
items to be claimed (pull), and records bindings, results and failures from the executors' reports.
\item When every action has completed (Z-Close) it stores the results back at the product; the owner can read the full
trace at any time.
\end{enumerate}""",
    functional=r"""\begin{itemize}
\item Recipe catalogue: validation (structure W1--W4, data flow, leases, guards, catalogue checks), immutable storage by
name and version, retirement, compilation to Choreographer plans.
\item Session engine: the session state $z = (M, M_A, \beta, \varepsilon, \xi, status)$ and the eight transition rules
A-Start, S-Enable, S-Bind, S-Done, A-Finish, Z-Close, S-FailBind and S-FailInvoke, with the Theorem~1 transition count.
\item Extensions, flagged per session: x-rebind (retry on ERROR or an unreachable executor), x-abort, x-timeout,
compensation (lease release on failure), x-guard/x-skip (guarded transitions with dead-path elimination).
\item Dispatch: push (executor selection by $\eta(s)$ and $\theta(s)$, least loaded, next executor after BIND\_FAILED),
pull (atomic claim, holder-only reports, claim timeout); \texttt{event} is accepted and handled as pull.
\item Session context ($\rho$, $\xi|dep(s)$) for executors; FIFO leases with renewal and expiry for shared resources.
\item Product integration (retrieve recipe at start, store results at Z-Close) and an audit worklog.
\end{itemize}""",
    data=r"""The WorkflowManager owns the database schema \texttt{ah\_workflow}: \texttt{recipe} (immutable canonical JSON per
version and its status), \texttt{workflow\_session} (the JSON snapshot of $z$ with its trace and an optimistic
\texttt{revision}), \texttt{work\_item} (one row per step attempt), \texttt{lease}, \texttt{worklog} and \texttt{logs}.
Storing $z$ as one snapshot keeps the stored state identical to the model object and lets sessions resume after a
restart.""",
    nonfunctional=r"""\begin{itemize}
\item \emph{Security:} see Section~\ref{sec:security}.
\item \emph{Safety and liveness:} for well-formed recipes without extensions every run takes at most $3|S| + 2|A|$
transitions and always progresses (Theorems~1--2 of~\cite{varga2026}); failure is absorbing. Exclusive tool use is
proved under assumption (A1) and holds for the lease variant only under an additional, validated configuration
assumption.
\item \emph{Latency:} one transition is a database update of the session snapshot; dispatch happens asynchronously on a
worker pool (\texttt{workflow.worker.threads}, default 8).
\item \emph{Energy and power saving:} no specific properties; the system is idle between requests except for the
claim/timeout watchdog.
\end{itemize}""",
    stateful=r"""Stateful. Recipes, sessions (state $z$, trace), work items, leases and the worklog are persisted, so a
WorkflowManager restart resumes running sessions. Executors keep no durable state.""",
    delimitations=r"""\begin{itemize}
\item No planning or scheduling (PlantDescriptionEngine or ERP), no quality policy authoring, no equipment services.
\item One local cloud: delegating actions to WorkflowManagers in other clouds through Gatekeeper/GateTunnel and the
disconnected working copy are designed but not implemented.
\item EventHandler notifications (\texttt{event} dispatch and workflow events) are not implemented.
\item Recovery beyond the listed extensions (compensation of arbitrary steps, \texttt{quantity} $>$ 1) is not provided;
the theorems do not cover sessions that use extensions.
\item The \texttt{workflowProduct} side is implemented as a consumer only; no product system exists yet.
\end{itemize}""",
    produced=[("workflowRecipe", "validate, register, query, get, retire, compile"),
              ("workflowInstance", "start, abort, query, get-status, get-trace"),
              ("workflowWorkItem", "claim, report"),
              ("workflowContext", "get-parameters, get-results, put-result"),
              ("workflowLease", "reserve, poll, renew, release"),
              ("workflowManagement", "query-worklog, query-executors, query-work-items"),
              ("generalManagement", "get-log, get-config")],
    consumed=[("serviceDiscovery", "ServiceRegistry", "register, revoke, lookup", r"Registration; discovery of executors (\texttt{workflowExecution} with its metadata) and the catalogue check C01."),
              ("identity", "Authentication", "login, logout, identity-verify", "Own identity token; verification of callers."),
              ("authorization", "ConsumerAuthorization", "verify", "Provider-side authorization of every service operation."),
              ("workflowExecution", "WorkflowExecutor", "start, abort", r"Push dispatch and abort of work items."),
              ("workflowProduct", "Intelligent Product", "retrieve-process-step, store-process-step-result", "Product-driven sessions."),
              ("sysMonitor (AMQP)", "SysMonitor broker", "publish", r"Health and metrics when \texttt{sysmonitor.enabled}.")],
    config=[("server.port", "8482", "Arrowhead connector port."),
            ("workflow.workstation.id", "(empty)", r"Workstation identifier sent to products."),
            ("workflow.dispatch.default", "push", r"Dispatch mode when neither the session nor the recipe gives one."),
            ("workflow.executors", "(empty)", r"Statically configured executors (development, or executors outside the ServiceRegistry)."),
            ("workflow.executor.discovery.cache-seconds", "10", "How long discovered executors are cached."),
            ("workflow.pull.claim-timeout", "300", "Seconds a claimed work item may stay unreported."),
            ("workflow.worker.threads", "8", "Dispatch worker threads."),
            ("workflow.lease.default-duration", "120", "Default lease duration in seconds."),
            ("workflow.lease.release-on-failure", "false", "Compensation: release a failed session's leases."),
            ("workflow.validator.catalogue-check", "true", "Run C02 (executor availability) against the ServiceRegistry."),
            ("workflow.validator.catalogue-check.services", "true", r"Run C01 (service registered). The secure deployment sets it to false, because under RESTRICTED discovery the lookup hides providers without \texttt{unrestrictedDiscovery}."),
            ("workflow.standalone", "false", "Development mode without core systems."),
            ("service.authorization.enabled", "false (deployment: true)", "Provider-side ConsumerAuthorization check."),
            ("management.policy", "(deployment: sysop-only)", "Protection of the management services."),
            ("sysmonitor.enabled", "false", "Publish sysMonitor telemetry over AMQP.")],
    components=[("WorkflowManagerSystemInfo", "Registration: system model, the seven services and their HTTP operations."),
                ("api.http.RecipeAPI, InstanceAPI, WorkItemAPI, ContextAPI, LeaseAPI, WorkflowManagementAPI, GeneralManagementAPI", "HTTP controllers of the services; DTOs in \\texttt{api.dto}; errors mapped by \\texttt{WorkflowExceptionAdvice} (409) and the common handler."),
                ("engine.WorkflowEngine", "Drives sessions: load the snapshot, apply rules (\\texttt{WorkflowSession} of \\texttt{workflow-model}), dispatch, handle claims and reports, watchdog for claim and step timeouts, resume after restart. One lock per session."),
                ("service.RecipeService, CatalogueCheck", "Catalogue: parse (unknown fields rejected), validate (\\texttt{RecipeValidator} plus the \\texttt{RecipeCheck} SPI with C01/C02), store canonical JSON, retire, compile."),
                ("service.ExecutorDirectory, HttpExecutorGateway", "Executor set $WE$ from the ServiceRegistry (\\texttt{workflowExecution} metadata) and static configuration; push calls to executors."),
                ("service.LeaseService", "FIFO leases with tokens, renewal, expiry and session release."),
                ("service.ProductGateway", "Consumer of \\texttt{workflowProduct}."),
                ("service.WorklogService, jpa.*", "Worklog and persistence (Spring Data JPA, Hibernate \\texttt{validate})."),
                ("security.ServiceAuthorizationFilter, ServiceAuthorizationService", "Provider-side ConsumerAuthorization check per operation."),
                ("workflow-model (library)", "Recipe, codec, graph, validator, session rules, guard expressions, input composer, plan compiler, wire records: pure Java and Jackson, no Spring.")],
    impl_functional=r"""A request that changes a session (start, report, abort, claim timeout) takes the session's lock, loads the
snapshot of $z$, applies one or more rules of \texttt{WorkflowSession}, saves the snapshot (optimistic revision) and
appends the transitions to the trace and worklog. Outbound calls (dispatch to executors, aborts, product calls) are
collected while the lock is held and executed after it is released, on the worker pool. Executor selection in push mode
filters $WE$ by $\eta(s)$ and $\theta(s)$ (service names compared case-insensitively without separators), excludes
executors already tried in the session, and picks the least loaded. A \texttt{BIND\_FAILED} report or an unreachable
executor moves the step to the next executor (x-rebind); when none is left S-FailBind fires. Guards are evaluated by
\texttt{GuardExpression} from $\rho$ and $\xi$ when a step completes; a step whose incoming transitions were all not
taken is skipped (x-skip).""",
)

# ======================================================================================= WorkflowExecutor
SYSTEMS["WorkflowExecutor"] = dict(
    module="workflow-executor", port="8483", abbrev="WfE",
    summary=r"runs the steps of workflow sessions: it has providers orchestrated for itself, invokes them through equipment adapters and reports the outcome to the WorkflowManager",
    overview=r"""The WorkflowExecutor (WfE) initiates the work. It receives work items from a WorkflowManager (push) or
claims them (pull), and runs each with itself as the consumer: lease handling, orchestration of the step's service
with the executor as requester, composition of the input from the session context, invocation of the provider through
an equipment adapter, and the report. Because the executor is the consumer, authorization, QoS filtering and provider
selection stay with the Arrowhead orchestrator and ConsumerAuthorization. An executor also implements the executor
interface of the Arrowhead Choreographer, so existing Choreographer plans can run on it.""",
    prior_art=r"""Derhamy's workflow executor, customised per workstation and talking to nothing outside its work
centre~\cite{derhamy2018workflow}; the Choreographer executor of Arrowhead 4.6 and 5.x; Varga's execution primitive
$compile \rightarrow O \rightarrow consume$ with the step's consumer as requester~\cite{varga2026}.""",
    use=r"""One or more executors are deployed per workstation. Each declares its capabilities (e.g. \texttt{heating},
\texttt{nutrunner}), the services it can run (\texttt{*} for any) and its workstation; these are registered as
metadata of \texttt{workflowExecution}. In push mode the WorkflowManager selects the executor; in pull mode the
executor polls \texttt{workflowWorkItem.claim}. With \texttt{workflow.choreographer.enabled} the executor also
registers with the Choreographer (\texttt{choreographerExecutor.register}) and runs Choreographer plan steps.""",
    functional=r"""\begin{itemize}
\item Run a work item: lease reserve/poll or renew, orchestration (pull, matchmaking, inter-cloud only when the step
allows it) at the configured orchestrator, input composition $\kappa_s(\rho, \xi|dep(s))$, invocation, lease release,
report (BOUND, BIND\_FAILED, SUCCESS, ERROR, FATAL\_ERROR, ABORTED).
\item Equipment adapter SPI with the generic HTTP/HTTPS JSON adapter (\texttt{generic\_http}, \texttt{generic\_https}).
\item Pull loop with configurable polling interval; push endpoint; abort and status of running work items.
\item Choreographer executor compatibility: \texttt{service-info}, \texttt{start}, \texttt{abort}, reporting with
\texttt{notify-step-done}; registration and unregistration with the Choreographer.
\end{itemize}""",
    data=r"""No workflow data is persisted. Work items in progress are kept in memory; the common-library log table is kept in an
embedded H2 database.""",
    nonfunctional=r"""\begin{itemize}
\item \emph{Security:} see Section~\ref{sec:security}. Providers see the executor's own certificate and identity.
\item \emph{Latency:} one orchestration and one invocation per step, plus two calls to the WorkflowManager (context and
report); work items run on a thread pool (\texttt{workflow.executor.threads}, default 8).
\item \emph{Energy and power saving:} in pull mode the executor polls (default every 500\,ms); push mode is idle
between work items.
\end{itemize}""",
    stateful=r"""Stateless with respect to workflows: the authoritative state is in the WorkflowManager. After a restart
unknown work items are reported as \texttt{UNKNOWN} by \texttt{get-status}, and the WorkflowManager's timeouts apply.""",
    delimitations=r"""\begin{itemize}
\item Only HTTP/HTTPS JSON equipment; MQTT, CoAP and OPC UA adapters are not implemented (the SPI exists).
\item TOKEN\_AUTH provider interfaces (orchestration tokens) are not used.
\item Never consumes services outside its local cloud unless the step's policy sets \texttt{localCloudOnly = false}.
\item Does not store recipes or results; does not talk to products.
\end{itemize}""",
    produced=[("workflowExecution", "service-info, start, abort, get-status"),
              ("generalManagement", "get-log, get-config"),
              ("Choreographer executor interface", r"service-info, start, abort (not registered; under \texttt{/workflowexecutor/choreographer})")],
    consumed=[("serviceDiscovery", "ServiceRegistry", "register, revoke", r"Registration of \texttt{workflowExecution} with capability metadata."),
              ("identity", "Authentication", "login, logout, identity-verify", "Own identity token; verification of callers."),
              ("authorization", "ConsumerAuthorization", "verify", "Provider-side authorization."),
              ("serviceOrchestration", "DynamicServiceOrchestration", "pull", r"Provider of each step, with the executor as requester."),
              ("workflowWorkItem", "WorkflowManager", "claim, report", "Pull and reports."),
              ("workflowContext", "WorkflowManager", "get-parameters, get-results, put-result", r"$\rho$, $\xi|dep(s)$, $\xi(s)$."),
              ("workflowLease", "WorkflowManager", "reserve, poll, renew, release", "Shared resources."),
              ("(step services)", "Equipment / application systems", "as orchestrated", "The work itself."),
              ("choreographerExecutor", "Choreographer", "register, unregister, notify-step-done", r"Only with \texttt{workflow.choreographer.enabled}.")],
    config=[("server.port", "8483", "Arrowhead connector port."),
            ("workflow.executor.capabilities", "(empty)", r"$cap(w)$, comma separated."),
            ("workflow.executor.services", "*", "Services the executor can run."),
            ("workflow.executor.workstation", "(empty)", "Workstation."),
            ("workflow.executor.mode", "push", r"\texttt{push} or \texttt{pull}."),
            ("workflow.executor.orchestrator", "DynamicServiceOrchestration", "The orchestrator asked for providers."),
            ("workflow.executor.unrestricted-discovery", "true", r"Register \texttt{workflowExecution} with \texttt{unrestrictedDiscovery}, so the WorkflowManager finds it under RESTRICTED discovery."),
            ("workflow.executor.poll-ms", "500", "Pull interval."),
            ("workflow.executor.threads", "8", "Work item threads."),
            ("workflow.executor.lease-poll-ms", "200", "Lease poll interval while queued."),
            ("workflow.manager.system-name", "WorkflowManager", "The only system allowed to start/abort work items."),
            ("workflow.manager.url", "(empty)", "Direct WorkflowManager URL for development; otherwise discovered."),
            ("workflow.executor.static-providers", "(empty)", "Static providers for development (no orchestration)."),
            ("workflow.choreographer.enabled", "false", "Choreographer executor compatibility."),
            ("workflow.choreographer.system-name", "Choreographer", "Choreographer allowed to call the compatibility interface."),
            ("workflow.choreographer.min-version / max-version", "1 / 1", "Versions reported in Choreographer \\texttt{service-info}.")],
    components=[("WorkflowExecutorSystemInfo", r"Registration of \texttt{workflowExecution} with metadata \texttt{workflowCapabilities}, \texttt{workflowServices}, \texttt{workflowWorkstation}, \texttt{workflowMode} and \texttt{unrestrictedDiscovery}."),
                ("api.http.ExecutionAPI, GeneralManagementAPI", "Service controllers; \\texttt{start}/\\texttt{abort} accept only the configured WorkflowManager or a sysop."),
                ("runtime.StepRunner", "The execution of one work item (lease, orchestrate, compose, invoke, release, report) on a thread pool; abort and status."),
                ("runtime.PullLoop", "Claims work items in pull mode."),
                ("runtime.ProviderResolver", "Pull orchestration with the executor as requester at the configured orchestrator; static providers for development."),
                ("runtime.ManagerClient", "Consumer of workflowWorkItem, workflowContext and workflowLease."),
                ("adapter.EquipmentAdapter, HttpJsonAdapter, Binding", "Adapter SPI and the generic HTTP(S) JSON adapter."),
                ("api.compat.ChoreographerCompatAPI, ChoreographerCompatRunner, init.ChoreographerRegistrar", "Choreographer executor interface; registration on start and unregistration on \\texttt{ContextClosedEvent}."),
                ("security.ServiceAuthorizationFilter", "Provider-side ConsumerAuthorization check.")],
    impl_functional=r"""\texttt{StepRunner} implements the steps of the execution primitive in order: (1) for a lease
\texttt{reserve} step, reserve and poll until granted (the pending call keeps the step running); (2) for a lease
\texttt{use} step, renew with the token from $\xi(s_{res})$ and fail without a valid lease; (3) orchestrate
$\theta(s)$ with the executor as requester and report \texttt{BOUND} with the binding or \texttt{BIND\_FAILED}; the run
continues only when the WorkflowManager accepted the binding; (4) read $\rho$ and $\xi|dep(s)$, compose the input with
\texttt{InputComposer} and invoke through the first adapter that supports the binding's interface; (5) release a
lease if the step is a \texttt{release} step; store $\xi(s)$ with \texttt{put-result} and report \texttt{SUCCESS}, or
report \texttt{ERROR}/\texttt{FATAL\_ERROR}. A step whose service is \texttt{workflowLease} runs against the
WorkflowManager's lease service only. Service names are compared case-insensitively without separators, because the
Choreographer normalises definitions to camelCase.""",
)

# ======================================================================================= WorkflowConfigurator
SYSTEMS["WorkflowConfigurator"] = dict(
    module="workflow-configurator", port="8484", abbrev="WfC",
    summary=r"lets an engineer draw workflows with conditions between the microservices of configured local clouds, and validates and stores them in the WorkflowManager",
    overview=r"""The WorkflowConfigurator (WfC) is the design-time tool of the workflow systems. It offers a graphical
editor in a browser console in which an engineer selects a configured local cloud, sees the microservices and the
WorkflowExecutors' capabilities in that cloud, and draws a workflow: actions, steps, transitions with conditions,
dependencies, inputs and policies. The editor checks the drawing continuously, explains each finding with help on how
to fix it, and stores the workflow as a recipe in the WorkflowManager when it validates. The same operations are
offered as the Arrowhead service \texttt{workflowConfiguration} for other engineering tools.""",
    prior_art=r"""Garcia Represa's workflow selection and implementation phases~\cite{represa2022}; the intent and
validator pipeline of the Arrowhead workflow kits; the LocalCloudConfigurator and SoLocalCloudConfigurator of this
workspace, whose designs the configurator lists.""",
    use=r"""An operator opens the console (loopback, password login), picks the running cloud, a LocalCloudConfigurator
design or a member cloud of a System of LocalClouds design, and draws the workflow in the state-machine or sequence
view. Conditions are built with a condition builder (or typed in the guard grammar), inputs from templates referencing
parameters and results. \emph{Validate} asks the WorkflowManager; \emph{Save} registers the recipe; \emph{Open} loads
stored recipes. Import and export use the recipe JSON.""",
    functional=r"""\begin{itemize}
\item Cloud directory: the live cloud from the ServiceRegistry (\texttt{serviceRegistryManagement} system-query and
service-query, with a discovery fallback), LocalCloudConfigurator designs and SoLC member clouds; each source optional.
\item Service and executor listing with operations, interfaces and capabilities; \texttt{sysMonitor} and
\texttt{generalManagement} hidden.
\item Graphical editor (static web application): state-machine and sequence views, inspector, condition builder,
undo, arrange with label placement, import/export, local checks with the validator's codes and per-finding help.
\item Forwarding of validate, store, list and get to the WorkflowManager's \texttt{workflowRecipe}.
\end{itemize}""",
    data=r"""Nothing durable: recipes are stored only in the WorkflowManager. The log table is kept in an in-memory H2 database;
console sessions are kept in memory.""",
    nonfunctional=r"""\begin{itemize}
\item \emph{Security:} see Section~\ref{sec:security}; the console is a separate connector with its own authentication.
\item \emph{Latency:} each listing makes one to three calls to the ServiceRegistry or the configurators.
\item \emph{Energy and power saving:} idle between requests.
\end{itemize}""",
    stateful=r"""Stateless apart from console login sessions.""",
    delimitations=r"""\begin{itemize}
\item Services of a cloud other than the live one cannot be listed (its ServiceRegistry is not reachable); the editor lets
the engineer name service definitions.
\item The console is meant for loopback access; exposing it requires an explicit \texttt{console.address} and allowed
hosts.
\item The configurator does not start sessions; that is \texttt{workflowInstance} of the WorkflowManager.
\end{itemize}""",
    produced=[("workflowConfiguration", "list-clouds, list-services, list-executors, list-recipes, get-recipe, validate-recipe, store-recipe"),
              ("generalManagement", "get-log, get-config")],
    consumed=[("serviceDiscovery", "ServiceRegistry", "register, revoke, lookup", r"Registration; executor and fallback discovery."),
              ("serviceRegistryManagement", "ServiceRegistry", "system-query, service-query", r"Systems and services of the live cloud (needs a grant and the \texttt{authorization} management policy)."),
              ("identity", "Authentication", "login, logout, identity-verify", "Own identity; verification of callers."),
              ("authorization", "ConsumerAuthorization", "verify", "Provider-side authorization."),
              ("workflowRecipe", "WorkflowManager", "validate, register, query, get", "Validation and storage."),
              ("localCloudConfiguration", "LocalCloudConfigurator", "catalog, list-configurations", "Local-cloud designs."),
              ("soLocalCloudConfiguration", "SoLocalCloudConfigurator", "list-designs, get-design", r"SoLC member clouds (when \texttt{workflow.configurator.solc.enabled})."),
              ("sysMonitor (AMQP)", "SysMonitor broker", "publish", r"When \texttt{sysmonitor.enabled}.")],
    config=[("server.port", "8484", "Arrowhead connector port."),
            ("console.port", "8485", "Console connector port."),
            ("console.address", "127.0.0.1", r"Console bind address (the container deployment binds 0.0.0.0 and publishes to host loopback)."),
            ("console.password-hash", "(secret)", r"\texttt{scrypt:<salt>:<hash>} (N=16384, r=8, p=1); the console is closed without it."),
            ("console.allowed-hosts", "localhost,127.0.0.1,[::1]", r"Accepted \texttt{Host} headers."),
            ("console.session-seconds", "28800", "Console session lifetime."),
            ("workflow.configurator.manager", "WorkflowManager", "Recipe store."),
            ("workflow.configurator.local-cloud-configurator", "LocalCloudConfigurator", "Design source."),
            ("workflow.configurator.solc-configurator", "SoLocalCloudConfigurator", "SoLC design source."),
            ("workflow.configurator.solc.enabled", "true", "List SoLC member clouds."),
            ("workflow.configurator.hidden-services", "sysMonitor,generalManagement", "Services not offered in the palette."),
            ("workflow.standalone", "false", "Development mode without core systems."),
            ("service.authorization.enabled", "false (deployment: true)", "Provider-side ConsumerAuthorization check.")],
    components=[("WorkflowConfiguratorSystemInfo", "Registration of \\texttt{workflowConfiguration} and \\texttt{generalManagement}."),
                ("api.http.ConfigurationAPI", "The seven operations; upstream failures returned with the provider and status."),
                ("service.CloudDirectory", "Clouds, systems, services and executors from the ServiceRegistry and the configurators."),
                ("service.RecipeGateway, Upstream", "Calls to the WorkflowManager and other providers through the common library."),
                ("console.ConsoleConnectorConfig, ConsoleFilter, ConsoleAuth, ConsolePasswordHash, ConsoleController", "Second Tomcat connector for the console; password login (scrypt), \\texttt{wfc\\_session} cookie, Host/Origin checks, lockout after five failures; static editor pages only on this connector."),
                ("security.ConfiguratorSecurityConfig, ServiceAuthorizationFilter, ConfiguratorOperations", "HTTPS redirect limited to the Arrowhead connector; provider-side authorization of the seven operations."),
                ("resources/web: index.html, app.js, model.js, app.css", "The editor: rendering and interaction (app.js), recipe model, local checks, layout and help (model.js), tested with Node tests.")],
    impl_functional=r"""The console connector serves the editor and the same \texttt{/workflowconfigurator/api} operations to an
authenticated browser session; requests from the console are marked as operator requests and run the service logic
without an mTLS caller, while the Arrowhead connector returns 404 for console pages. The editor's model module mirrors
the WorkflowManager's checks for immediate feedback and uses the same codes, so every finding links to the same help
text; the WorkflowManager's verdict remains authoritative and saving requires ACCEPT. Arrange computes column gaps
from the widest condition label and places labels on their transition before the target step, moving them with a
leader line when the spot is taken.""",
)


def produced_rows(sysname, produced):
    rows = []
    for s, ops in produced:
        if s.startswith("Choreographer"):
            ref = r"Choreographer executor IDD (Choreographer 5.x)"
        elif s == "generalManagement":
            ref = r"\texttt{Workflow-v%s-generalManagement-SD}, \texttt{-HTTP-IDD}" % EDITION
        else:
            ref = r"\texttt{%s-v%s-%s-SD}, \texttt{-HTTP-IDD}" % (sysname, EDITION, s)
        rows.append((r"\texttt{%s}" % esc(s), esc(ops), ref))
    return rows


def consumed_rows(consumed):
    out = []
    for s, prov, ops, why in consumed:
        name = r"\texttt{%s}" % esc(s) if not s.startswith("(") else esc(s)
        if s == "workflowProduct":
            why += r" Contract: \texttt{Workflow-v%s-workflowProduct-SD}." % EDITION
        out.append((name, esc(prov), esc(ops), why))
    return out


def diagram(sysname, d):
    prod = [s for s, _ in d["produced"] if not s.startswith("Choreographer")]
    cons = [(s, p) for s, p, _, _ in d["consumed"]]
    return block_diagram(sysname, prod, cons)


def render_sysd(sysname, d):
    fig = figure(diagram(sysname, d), r"SysML block view of the %s: produced services (right) and consumed services with their providers (left)." % sysname, "fig:block", scale="\\textwidth")
    doc = preamble("SysD") + front(sysname, "System Description", "SysD",
                                   r"This document describes the %s, an Eclipse Arrowhead support system that %s. Documentation edition %s; system version %s (framework), service versions 1.0.0." % (sysname, d["summary"], EDITION, EDITION))
    doc += rf"""
\section{{Overview}}
\label{{sec:overview}}
{d["overview"]}
{fig}
The rest of this document is organized as follows.
In Section~\ref{{sec:prior_art}}, we reference major prior art capabilities of the system.
In Section~\ref{{sec:use}}, we describe the intended usage of the system.
In Section~\ref{{sec:properties}}, we describe fundamental properties provided by the system.
In Section~\ref{{sec:delimitations}}, we describe delimitations of capabilities of the system.
In Section~\ref{{sec:services}}, we describe the abstract service functions consumed or produced by the system.
In Section~\ref{{sec:security}}, we describe the security capabilities of the system.

\newpage
\subsection{{Significant Prior Art}}
\label{{sec:prior_art}}
{d["prior_art"]}

\subsection{{How This System Is Meant to Be Used}}
\label{{sec:use}}
{d["use"]}

\subsection{{System functionalities and properties}}
\label{{sec:properties}}
\subsubsection{{Functional properties of the system}}
{d["functional"]}

\subsubsection{{Configuration of system properties}}
The system is configured with Spring properties; the main ones are listed in the SysDD. The workflow-specific behaviour
is configured under the prefix \texttt{{workflow.}}; the security profile (TLS, authentication, authorization) is the
common Arrowhead~5 profile.

\subsubsection{{Data stored by the system}}
{d["data"]}

\subsubsection{{Non functional properties}}
{d["nonfunctional"]}

\subsubsection{{Stateful or stateless}}
{d["stateful"]}

\subsection{{Important Delimitations}}
\label{{sec:delimitations}}
{d["delimitations"]}

\newpage
\section{{Services}}
\label{{sec:services}}
This section lists the produced and consumed services. Each produced service is specified by an SD and an HTTP/TLS/JSON
IDD in this documentation set.

\subsection{{Produced services}}
{table(["Service", "Operations", "Documents"], produced_rows(sysname, d["produced"]), ["3.8cm", "6.2cm", "5.8cm"])}

\subsection{{Consumed services}}
{CORE_CONSUMED_TEXT}
{table(["Service", "Provider", "Operations", "Purpose"], consumed_rows(d["consumed"]), ["3.4cm", "3.2cm", "3.6cm", "5.6cm"])}

\newpage
\section{{Security}}
\label{{sec:security}}
The {sysname} runs in Arrowhead secure mode in every cloud deployment; an insecure mode exists only for development and
unit tests. It handles only Arrowhead-compliant X.509 certificates of its local cloud; others are refused during the
TLS handshake.
{SECURITY_COMMON}
"""
    if sysname == "WorkflowConfigurator":
        doc += r"""
\subsection{Console}
The browser console is a second connector (default \texttt{127.0.0.1:8485}). It authenticates the operator with a
password whose scrypt hash is configured as a secret, keeps the session in an HttpOnly cookie \texttt{wfc\_session},
checks the \texttt{Host} and \texttt{Origin} headers and locks out after five failed logins. A console session is not
an Arrowhead identity; the console cannot be reached on the Arrowhead connector, and the Arrowhead service cannot be
reached without mTLS and a grant.
"""
    if sysname == "WorkflowManager":
        doc += r"""
\subsection{Object-level checks}
In addition to ConsumerAuthorization the WorkflowManager protects itself (first-person perspective): only the holder of
a work item may report on it or read and write its context; only the owner of a session or a sysop may abort it.
"""
    if sysname == "WorkflowExecutor":
        doc += r"""
\subsection{Object-level checks}
Only the configured WorkflowManager (or a sysop) may start or abort work items, and only the configured Choreographer
may use the Choreographer executor interface. Providers of step services see the executor's own certificate and must
grant the executor, not the WorkflowManager.
"""
    doc += ending("system description of the %s" % sysname)
    return doc


VARIANTS = r"""
\section{Implementation variants}
\label{sec:variants}
\begin{itemize}
\item \textbf{Java (reference implementation).} \texttt{Eclipse-Arrowhead-java/ah-Workflow-java/%s}, Java 21, Spring
Boot, built with Maven together with the shared \texttt{workflow-model} module and the vendored Arrowhead~5.4.0 common
library.
\item \textbf{Go.} Not implemented. No workflow system exists in \texttt{Eclipse-Arrowhead-go}.
\item \textbf{Rust.} Not implemented. No workflow system exists in \texttt{Eclipse-Arrowhead-rust}.
\end{itemize}
The workflow systems are therefore not yet interoperable across the three implementations. A Go or Rust port must
reproduce the IDDs of this set exactly and, for the WorkflowManager, the behaviour of \texttt{workflow-model}: the
recipe schema and decoder, the validator's findings and verdicts, the guard grammar and the transition rules. The
architecture prescribes conformance vectors (recipe in, verdict and transition trace out) to be run by all three
implementations; they are not written yet.
"""


def render_sysdd(sysname, d):
    comps = table(["Component", "Responsibility"], [("{\\footnotesize " + tt(c).replace(",", ",\\allowbreak{}") + "}", r) for c, r in d["components"]], ["6.2cm", "9.6cm"])
    cfg = table(["Property", "Default", "Meaning"], [(tt(k), esc(v), m) for k, v, m in d["config"]], ["5.6cm", "3.4cm", "6.8cm"])
    fig = figure(diagram(sysname, d), r"Block view of the %s with its produced and consumed services." % sysname, "fig:block", scale="\\textwidth")
    doc = preamble("SysDD") + front(sysname, "System Design Description", "SysDD",
                                    r"This document describes how the %s is implemented in the Java reference implementation: its internal components, the realisation of its functional and non-functional properties, its configuration and the implementation status of the other languages. Documentation edition %s." % (sysname, EDITION))
    doc += rf"""
\section{{Overview}}
\label{{sec:overview}}
This document describes the design of the {sysname}, which {d["summary"]}. The abstract system is described in the
SysD of the same name; its produced services are specified by SDs and IDDs in this documentation set.
{fig}

\newpage
\section{{Implementation}}
\label{{sec:implementation}}

\subsection{{Implementation language and tools}}
Java~21 and Spring Boot~4 (Spring Framework~7, Spring Security~7), Spring Data JPA with Hibernate, Jackson for JSON,
Springdoc for the OpenAPI description, built with Maven~3.9 (in the \texttt{{maven:3.9-eclipse-temurin-21}} image). The
system builds on the vendored Arrowhead~5.4.0 common library (\texttt{{vendor/common-utils}},
\texttt{{vendor/data-transfer-objects}}): system registration, identity, TLS, management filters and HTTP consumption
of other services. The source is module \texttt{{{d["module"]}}} of \texttt{{Eclipse-Arrowhead-java/ah-Workflow-java}}; the
container image runs the Spring Boot jar on Eclipse Temurin~21.

\subsection{{Internal structure}}
{comps}

\subsection{{Functional properties implementation}}
{d["impl_functional"]}

\subsection{{Non functional properties implementation}}
\subsubsection{{Security}}
Spring Security filter chains of the common library: TLS client authentication, identity-token verification against
the Authentication system (outsourced authentication), the management filter and the workflow systems'
\texttt{{ServiceAuthorizationFilter}}, which maps method and path to service definition and operation and asks
ConsumerAuthorization (\texttt{{authorization.verify}}). Object-level checks (holder, owner, configured manager) are made in
the service logic and give 403.
\subsubsection{{Power management}}
No specific power management. Thread pools are bounded; the WorkflowManager's watchdog and the executor's pull loop are
the only periodic activities.
\subsubsection{{Internal monitoring}}
Spring Boot health endpoint{" (\\texttt{WorkflowManagerHealthIndicator})" if sysname == "WorkflowManager" else " (\\texttt{WorkflowExecutorHealthIndicator})" if sysname == "WorkflowExecutor" else ""},
the common-library log table exposed through \texttt{{generalManagement.get-log}}, and optional sysMonitor telemetry over
AMQP (\texttt{{sysmonitor.enabled}}).{" The worklog records every session event." if sysname == "WorkflowManager" else ""}
\subsubsection{{Configuration}}
{cfg}

\newpage
\section{{Services}}
\label{{sec:services}}
\subsection{{Produced services}}
All produced services use interface template \texttt{{generic\_https}}, policy \texttt{{CERT\_AUTH}} and version 1.0.0 and are
registered with every operation's method and path.
{table(["Service", "Operations", "Documents"], produced_rows(sysname, d["produced"]), ["3.8cm", "6.2cm", "5.8cm"])}
\subsection{{Consumed services}}
{table(["Service", "Provider", "Operations", "Purpose"], consumed_rows(d["consumed"]), ["3.4cm", "3.2cm", "3.6cm", "5.6cm"])}
{VERIFICATION[sysname]}
{VARIANTS % d["module"]}
"""
    doc += ending("system design description of the %s" % sysname)
    return doc


VERIFICATION = {
    "WorkflowManager": r"""
\section{Verification}
\label{sec:verification}
Unit tests: 25 \texttt{workflow-model} tests (rules, validator, guards, codec, compiler) and 9 WorkflowManager tests
(Spring, H2). The smoke test (\texttt{verification/smoke}, MySQL with the shipped schema, one WorkflowManager, three
executors, simulated plant) passes 18/18 checks, including three concurrent forklift sessions without overlapping tool
use and lease expiry after a blocked AGV. The cloud test (\texttt{verification/cloud/run-cloud-test.sh}) against the
Java test cloud with RESTRICTED discovery, identity tokens and ConsumerAuthorization passes 49/49 checks: registration,
executor discovery, 401/403 cases, DSO binding with the executors' certificates, the transition counts 11, 33 and 9,
pull mode, owner-only abort, compile, guarded recipes and sysop-only management.
""",
    "WorkflowExecutor": r"""
\section{Verification}
\label{sec:verification}
Seven unit tests with fake equipment, and the smoke and cloud tests of the WorkflowManager, in which three executors
run push and pull sessions and the plant sees the executors' own certificates. Choreographer compatibility was
verified against the cloud's Choreographer 5.4.0: a compiled recipe ran as a Choreographer plan on an executor that
registered itself, reported with \texttt{notify-step-done} and unregistered on shutdown (34/34 checks).
""",
    "WorkflowConfigurator": r"""
\section{Verification}
\label{sec:verification}
Seven Spring tests and 12 Node tests of the editor model (\texttt{node --test
workflow-configurator/src/test/js/model.test.mjs}), among them a test that every validator code has help text. In the
cloud test the configurator lists the running cloud, three LocalCloudConfigurator designs and five SoLC member clouds,
refuses a condition reading a later step (422, G03), stores a guarded recipe (201, then 200 for identical content and
409 for different content) and enforces ConsumerAuthorization at the Arrowhead connector. The editor was also driven in
a browser to draw, validate, save and reopen a branching workflow, which the WorkflowManager then ran correctly.
""",
}


def files():
    out = {}
    for name, d in SYSTEMS.items():
        out["%s-v%s-SysD.tex" % (name, EDITION)] = render_sysd(name, d)
        out["%s-v%s-SysDD.tex" % (name, EDITION)] = render_sysdd(name, d)
    return out
