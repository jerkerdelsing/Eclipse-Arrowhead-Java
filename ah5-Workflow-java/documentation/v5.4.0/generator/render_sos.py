r"""Workflow System of Systems: black-box SoSD and white-box SoSDD. Content from ARCHITECTURE.md (sections 3-15),
README.md and the source."""

from latex import EDITION, preamble, front, ending, table, figure, sequence, esc

TITLE = "Arrowhead Workflow SoS"


def blackbox():
    return r"""\begin{tikzpicture}
\node[draw=ArrowheadBlue,very thick,fill=ArrowheadBlue!5,rounded corners=4pt,minimum width=6.4cm,minimum height=6.0cm] (sos) at (0,0) {};
\node[font=\bfseries,ArrowheadBlue,align=center] at (0,2.4) {Workflow SoS\\[-1pt]{\scriptsize\normalfont $\ll$black box$\gg$}};
\node[font=\scriptsize,align=center,text width=5.6cm] at (0,1.2) {design, store, validate and run workflows of service exchanges in one local cloud};
\node[ahsrv,anchor=east] (s1) at (3.2,0.0) {workflowConfiguration};
\node[ahsrv,anchor=east] (s2) at (3.2,-0.8) {workflowRecipe};
\node[ahsrv,anchor=east] (s3) at (3.2,-1.6) {workflowInstance};
\node[ahsrv,anchor=east] (s4) at (3.2,-2.4) {workflowManagement};
\node[ahext] (eng) at (7.4,0.0) {Engineer / tool\\[-1pt]{\scriptsize browser console}};
\node[ahext] (prod) at (7.4,-1.6) {Product, line controller,\\ upper WorkflowManager};
\node[ahext] (op) at (7.4,-3.1) {Sysop};
\draw[ahflow] (eng.west) -- (s1.east);
\draw[ahflow] (eng.south west) -- (s2.east);
\draw[ahflow] (prod.west) -- (s3.east);
\draw[ahflow] (op.west) -- (s4.east);
\node[ahext,minimum width=4.2cm] (core) at (-8.1,2.2) {Core systems\\[-1pt]{\scriptsize ServiceRegistry, Authentication,}\\[-3pt]{\scriptsize ConsumerAuthorization, DSO}};
\node[ahext,minimum width=4.2cm] (cfg) at (-8.1,0.6) {LocalCloudConfigurator,\\ SoLocalCloudConfigurator};
\node[ahext,minimum width=4.2cm] (eq) at (-8.1,-1.0) {Equipment / application\\ systems (step services)};
\node[ahext,minimum width=4.2cm] (pr) at (-8.1,-2.6) {Intelligent Product\\[-1pt]{\scriptsize workflowProduct}};
\draw[ahflow] (sos.west |- core) -- (core.east) node[midway,above,font=\scriptsize,align=center] {discovery, identity,\\ authorization, orchestration};
\draw[ahflow] (sos.west |- cfg) -- (cfg.east) node[midway,above,font=\scriptsize] {designs};
\draw[ahflow] (sos.west |- eq) -- (eq.east) node[midway,above,font=\scriptsize] {invoke};
\draw[ahflow] (sos.west |- pr) -- (pr.east) node[midway,above,font=\scriptsize] {recipe, results};
\node[ahext] (ch) at (0,-4.6) {Choreographer\\[-1pt]{\scriptsize (compatibility)}};
\draw[ahflow,<->] (sos.south) -- (ch.north) node[midway,right,font=\scriptsize,align=left] {executor interface,\\ compiled plans};
\end{tikzpicture}"""


def whitebox():
    return r"""\begin{tikzpicture}
\node[draw=ArrowheadBlue,very thick,fill=ArrowheadBlue!3,rounded corners=4pt,minimum width=12.4cm,minimum height=9.6cm] (lc) at (0,-0.4) {};
\node[font=\bfseries\small,ArrowheadBlue,anchor=north west] at (-6.0,4.3) {Workstation local cloud};
\node[ahsys] (wfc) at (-3.8,2.6) {WorkflowConfigurator\\[-1pt]{\scriptsize\normalfont :8484, console :8485}};
\node[ahsys] (wfm) at (2.2,2.6) {WorkflowManager\\[-1pt]{\scriptsize\normalfont :8482}};
\node[ahdb] (db) at (4.9,2.6) {ah\_workflow\\(embedded)};
\node[ahext] (lcc) at (-3.8,0.4) {LocalCloudConfigurator,\\ SoLocalCloudConfigurator};
\node[ahsys] (wfe1) at (-0.8,-1.4) {WorkflowExecutor A\\[-1pt]{\scriptsize\normalfont :8483, push}};
\node[ahsys] (wfe2) at (4.0,-1.4) {WorkflowExecutor B\\[-1pt]{\scriptsize\normalfont pull}};
\node[ahext] (core) at (-3.9,-4.0) {ServiceRegistry, Authentication,\\ ConsumerAuthorization, DSO};
\node[ahext] (eq) at (2.0,-4.0) {Equipment\\[-1pt]{\scriptsize sensor, heater, nutrunner, \ldots}};
\node[ahext] (prod) at (9.0,2.6) {Intelligent Product};
\node[ahext] (cli) at (9.0,0.6) {Session owner};
\draw[ahflow] (wfc) -- (wfm) node[midway,above,font=\scriptsize] {workflowRecipe};
\draw[ahflow] (wfc) -- (lcc) node[midway,right,font=\scriptsize] {designs};
\draw[thick,ArrowheadOrange] (wfm) -- (db);
\draw[ahflow] ([xshift=-8mm]wfm.south) -- ([xshift=-4mm]wfe1.north) node[pos=0.45,left,font=\scriptsize,align=right] {workflowExecution\\ (push)};
\draw[ahflow] ([xshift=6mm]wfe1.north) -- ([xshift=-2mm]wfm.south) node[pos=0.55,right,font=\scriptsize,align=left] {workItem, context,\\ lease};
\draw[ahflow] (wfe2.north) -- ([xshift=8mm]wfm.south) node[pos=0.5,right,font=\scriptsize,align=left] {claim, report,\\ context, lease};
\draw[ahflow] (wfe1.south) -- (eq.north west) node[pos=0.5,right,font=\scriptsize] {invoke};
\draw[ahflow] (wfe2.south) -- (eq.north east) node[pos=0.5,right,font=\scriptsize] {invoke};
\draw[ahflow] (wfe1.south west) -- (core.north east) node[pos=0.5,left,font=\scriptsize,align=right] {orchestration\\ (requester = WfE)};
\draw[ahflow] (wfm.north) to[out=35,in=145] node[pos=0.5,above,font=\scriptsize] {workflowProduct} (prod.north);
\draw[ahflow] (cli.west) -- (wfm.south east) node[pos=0.4,below,font=\scriptsize] {workflowInstance};
\end{tikzpicture}"""


SCEN_DESIGN = sequence(
    [("U", "Engineer"), ("C", "WorkflowConfigurator"), ("R", "ServiceRegistry"), ("M", "WorkflowManager")],
    [("U", "C", "login (console)", ""), ("U", "C", "list-clouds, list-services", ""),
     ("C", "R", "system-query, service-query", ""), ("R", "C", "systems, services", "reply"),
     ("U", "U", "draw steps, conditions, inputs", "self"), ("U", "C", "validate-recipe", ""),
     ("C", "M", "workflowRecipe.validate", ""), ("M", "C", "ValidationReport", "reply"),
     ("U", "C", "store-recipe", ""), ("C", "M", "workflowRecipe.register", ""), ("M", "C", "201 RecipeRegistration", "reply")])

SCEN_PUSH = sequence(
    [("O", "Session owner"), ("M", "WorkflowManager"), ("E", "WorkflowExecutor"), ("D", "DSO / ConsumerAuth."), ("Q", "Equipment")],
    [("O", "M", "workflowInstance.start", ""), ("M", "M", "A-Start, S-Enable", "self"),
     ("M", "E", "workflowExecution.start(work item)", ""), ("E", "D", "orchestration (requester = WfE)", ""),
     ("D", "E", "provider endpoint", "reply"), ("E", "M", "report BOUND (S-Bind)", ""),
     ("E", "M", "get-parameters, get-results", ""), ("E", "Q", r"invoke with input $\kappa_s$", ""),
     ("Q", "E", "result", "reply"), ("E", "M", "put-result; report SUCCESS (S-Done)", ""),
     ("M", "M", "next steps ... A-Finish, Z-Close", "self"), ("O", "M", "get-status / get-trace", "")])

SCEN_PULL = sequence(
    [("M", "WorkflowManager"), ("E", "WorkflowExecutor (pull)"), ("L", "workflowLease"), ("Q", "Tool / equipment")],
    [("M", "M", "S-Enable: work item OPEN", "self"), ("E", "M", "claim(capabilities, services)", ""),
     ("M", "E", "WorkItem (CLAIMED)", "reply"), ("E", "L", "reserve (step mode reserve)", ""),
     ("L", "E", "QUEUED ticket ... poll ... GRANTED token", "reply"), ("E", "M", "put-result(token); SUCCESS", ""),
     ("E", "M", "claim next step (mode use)", ""), ("E", "L", "renew(token)", ""), ("E", "Q", "use tool", ""),
     ("E", "M", "report SUCCESS", ""), ("E", "L", "release(token) (mode release)", "")])

SCEN_PRODUCT = sequence(
    [("P", "Intelligent Product"), ("M", "WorkflowManager"), ("E", "WorkflowExecutors")],
    [("P", "M", "workflowInstance.start(product)", ""), ("M", "P", "workflowProduct.retrieve-process-step(workstationId)", ""),
     ("P", "M", "recipe, parameters, subject", "reply"), ("M", "M", "validate, register, open session", "self"),
     ("M", "E", "dispatch steps (push or pull)", ""), ("E", "M", "reports", "reply"),
     ("M", "M", "Z-Close", "self"), ("M", "P", "workflowProduct.store-process-step-result", "")], width=4.2)

SCEN_CHOR = sequence(
    [("T", "Tool"), ("M", "WorkflowManager"), ("C", "Choreographer"), ("E", "WorkflowExecutor"), ("D", "DSO")],
    [("E", "C", "choreographerExecutor.register", ""), ("T", "M", "workflowRecipe.compile", ""),
     ("M", "T", "ChoreographerPlan (lossySteps)", "reply"), ("T", "C", "create plan, start session", ""),
     ("C", "E", "service-info", ""), ("C", "D", "orchestrate for the executor", ""), ("C", "E", "start step", ""),
     ("E", "C", "notify-step-done", ""), ("E", "C", "unregister (shutdown)", "")])


def sosd():
    data_rows = [("WorkflowManager", r"Recipes (immutable), sessions (state $z$ and trace), work items, leases, worklog: database \texttt{ah\_workflow}, embedded in the WorkflowManager and reachable only through its services."),
                 ("WorkflowExecutor", "No workflow data; work items in progress in memory."),
                 ("WorkflowConfigurator", "No workflow data; console sessions in memory.")]
    prod_rows = [(r"\texttt{workflowConfiguration}", "WorkflowConfigurator", "Engineers and engineering tools: clouds, services, executors; validate and store workflows."),
                 (r"\texttt{workflowRecipe}", "WorkflowManager", "Engineering tools, upper WorkflowManagers: recipe catalogue, compile to Choreographer plans."),
                 (r"\texttt{workflowInstance}", "WorkflowManager", "Products, line controllers, upper WorkflowManagers: start, observe and abort sessions."),
                 (r"\texttt{workflowManagement}", "WorkflowManager", "Sysop: worklog, executors, work items."),
                 (r"\texttt{generalManagement}", "all three", "Sysop: logs and configuration."),
                 (r"Choreographer executor interface", "WorkflowExecutor", "The Choreographer (compatibility, not registered).")]
    cons_rows = [(r"\texttt{serviceDiscovery}, \texttt{serviceRegistryManagement}", "ServiceRegistry", "Registration, executor discovery, cloud listing."),
                 (r"\texttt{identity}", "Authentication", "Identity tokens of the systems."),
                 (r"\texttt{authorization}", "ConsumerAuthorization", "Provider-side checks of every operation."),
                 (r"\texttt{serviceOrchestration}", "DynamicServiceOrchestration", "Provider of each step, with the executor as requester."),
                 (r"step services", "Equipment and application systems", "The work of each step."),
                 (r"\texttt{workflowProduct}", "Intelligent Product", "Recipe retrieval and result storage."),
                 (r"\texttt{localCloudConfiguration}, \texttt{soLocalCloudConfiguration}", "LocalCloudConfigurator, SoLocalCloudConfigurator", "Designed clouds for the editor."),
                 (r"\texttt{choreographerExecutor}", "Choreographer", "Registration and step notification (compatibility).")]
    doc = preamble("SoSD") + front(TITLE, "System of Systems Description (black box)", "SoSD",
                                   r"This document describes the Arrowhead Workflow System of Systems as a black box: the workflow capability it adds to an Eclipse Arrowhead local cloud, how it is meant to be used, its properties and delimitations, and the services at its boundary. The white-box design is given in the SoSDD. Documentation edition %s." % EDITION)
    doc += rf"""
\section{{Overview}}
\label{{sec:overview}}
Eclipse Arrowhead binds services at run time, but it cannot state the \emph{{order and conditions}} of service exchanges.
Putting that logic in each consumer breaks loose coupling; leaving it to a central MES brings back a bottleneck and a
single point of failure~\cite{{represa2022,derhamy2018}}. The Workflow SoS closes this gap. Seen from outside, it lets an
engineer \emph{{design}} a workflow over the microservices of a local cloud, lets products and controllers \emph{{run}} it
for a subject, and makes every run \emph{{traceable}} against a formal execution model~\cite{{varga2026}}, while service
binding, authorization and QoS stay with the Arrowhead core systems.
{figure(blackbox(), "The Workflow SoS as a black box: services at its boundary (right) and the systems it relies on (left and below).", "fig:blackbox", scale="\\textwidth")}
The rest of this document is organized as follows. Section~\ref{{sec:prior_art}} references prior art,
Section~\ref{{sec:use}} the intended use, Section~\ref{{sec:properties}} the properties, Section~\ref{{sec:delimitations}}
the delimitations, Section~\ref{{sec:services}} the services at the boundary and Section~\ref{{sec:security}} security.

\newpage
\subsection{{Significant Prior Art}}
\label{{sec:prior_art}}
\begin{{itemize}}
\item Derhamy~\cite{{derhamy2018,derhamy2018workflow}}: the split into Intelligent Product, Workflow Manager and Workflow
Executor; the Workflow Manager as a cloneable blackboard per work centre; the product as final arbiter; the design
principles loose coupling, searchable, run-time binding, autonomy, specialisation, data at its source and first-person
perspective.
\item Garcia Represa~\cite{{represa2022}}: the ordering gap in microservice SoS; workflow management as a separate system;
coexistence of orchestration-style and choreography-style workflow management; workflows are selected, implemented
and executed.
\item Varga~\cite{{varga2026}}: the formal recipe $W$, its well-formedness (W1)--(W4), the session state and transition
rules, and the guarantees Theorem~1 (at most $3|S| + 2|A|$ transitions), Theorem~2 (progress) and Proposition~1
(exclusive tool use).
\item The Arrowhead Choreographer (4.6 to 5.4): plans, sessions and the executor contract, with which the SoS
coexists~\cite{{delsing2017}}.
\end{{itemize}}

\subsection{{How This SoS Is Meant to Be Used}}
\label{{sec:use}}
\begin{{enumerate}}
\item \textbf{{Design.}} An engineer opens the WorkflowConfigurator console, picks a configured local cloud, draws a
workflow with conditions between its microservices, validates it and stores it.
\item \textbf{{Orchestrated run (push).}} A line controller starts a session; the SoS selects an executor for each
enabled step, which has the provider orchestrated and invokes it.
\item \textbf{{Blackboard run (pull).}} Workstation executors claim the steps they can do; suitable for plug-and-produce
stations and intermittent links.
\item \textbf{{Product-driven run.}} A product arriving at a workstation hands over its recipe fragment and receives the
results when the session closes.
\item \textbf{{Shared tools.}} Several lines share a tool under expiring leases, so a failed line does not block the others.
\item \textbf{{Legacy plans.}} Recipes compile to Choreographer plans, and executors serve the Choreographer's executor
interface, so existing Choreographer installations keep working.
\end{{enumerate}}

\subsection{{SoS functionalities and properties}}
\label{{sec:properties}}
\subsubsection{{Functional properties of the SoS}}
\begin{{itemize}}
\item Workflow design over the services of the live cloud, LocalCloudConfigurator designs and SoLC member clouds.
\item Recipe validation with 30 coded checks and a verdict (ACCEPT, REVISE, CLARIFY, REJECT); immutable versioned storage.
\item Execution with the eight transition rules of the formal model, push and pull dispatch, guarded transitions,
retries, timeouts, aborts and leases; full traces.
\item Coexistence with the Choreographer in both directions.
\end{{itemize}}
\subsubsection{{Configuration of SoS properties}}
Per deployment: the workstation identifier of the WorkflowManager, the capabilities, services, workstation and mode of
each executor, the dispatch default, timeouts and lease durations, and the ConsumerAuthorization grants between the
systems and towards the equipment services. Per workflow: the recipe.
\subsubsection{{Data stored by the individual microsystems}}
{table(["System", "Data"], data_rows, ["4.0cm", "11.8cm"])}
\subsubsection{{Non functional properties}}
\begin{{itemize}}
\item \emph{{Security:}} mutual TLS, identity tokens bound to certificates, ConsumerAuthorization on every operation,
object-level checks (Section~\ref{{sec:security}}).
\item \emph{{Safety and predictability:}} for well-formed recipes without extensions, bounded and progressing runs
(Theorems~1--2); failure is absorbing; every run is checked against the model.
\item \emph{{Loose coupling:}} recipes name service definitions and capabilities, never endpoints; providers are bound
per step and per session, so new equipment is used without changing the workflow (plug-and-produce).
\item \emph{{Autonomy:}} a workstation cloud with its core systems, one WorkflowManager, its executors and equipment can
run without a factory connection.
\end{{itemize}}
\subsubsection{{Stateful or stateless}}
Stateful in the WorkflowManager (recipes and sessions survive restarts); executors and the configurator are stateless.

\subsection{{Important Delimitations}}
\label{{sec:delimitations}}
\begin{{itemize}}
\item One local cloud: federation of WorkflowManagers across clouds and the disconnected working copy are designed but
not implemented.
\item No planning, scheduling or supply-chain functions; no equipment services.
\item EventHandler-based (\texttt{{event}}) dispatch and workflow events are not implemented.
\item Equipment adapters for HTTP/HTTPS JSON only.
\item Extensions (rebind, retry, timeout, abort, guards, compensation) are outside the proven model; sessions using them are
flagged.
\item Java only: the Go and Rust implementations do not exist, so the SoS is not yet interoperable across the three
implementations.
\end{{itemize}}

\newpage
\section{{Services}}
\label{{sec:services}}
Only the services below cross the boundary of the SoS. The services between its constituent systems
(\texttt{{workflowExecution}}, \texttt{{workflowWorkItem}}, \texttt{{workflowContext}}, \texttt{{workflowLease}}) are internal and
described in the SoSDD, although they are ordinary Arrowhead services that other systems could use.

\subsection{{Produced services}}
{table(["Service", "Provider", "Consumers and purpose"], prod_rows, ["4.6cm", "3.6cm", "7.6cm"])}

\subsection{{Consumed services}}
{table(["Service", "Provider", "Purpose"], cons_rows, ["5.6cm", "4.2cm", "6.0cm"])}

\newpage
\section{{Security}}
\label{{sec:security}}
\begin{{itemize}}
\item The SoS runs only in Arrowhead secure mode in a cloud: every system has a certificate of the local cloud CA, all
traffic uses TLS with client authentication, and every request carries an identity token bound to the caller's
certificate.
\item Non-compliant certificates and certificates on the CRL are refused.
\end{{itemize}}
\subsection{{Security model}}
\begin{{itemize}}
\item \textbf{{Protocol:}} HTTPS (TLS 1.3/1.2) with JSON; interface policy \texttt{{CERT\_AUTH}}.
\item \textbf{{Data protection:}} TLS on all links; each system's database is embedded in its own process, so no database link or port exists; secrets from container secrets.
\item \textbf{{System authentication:}} X.509 client certificates plus Authentication identity tokens.
\item \textbf{{Authorization of produced services:}} ConsumerAuthorization grant per service operation at the provider;
management services sysop only.
\item \textbf{{Authorization towards equipment:}} each step's provider is orchestrated with the \emph{{executor}} as
requester, so the equipment authorizes the executor, never the WorkflowManager or the session owner.
\item \textbf{{Object level:}} only the session owner may abort, only the work-item holder may report or use its context,
only the configured WorkflowManager may start work on an executor.
\item \textbf{{Human access:}} the WorkflowConfigurator console is a separate loopback connector with a password (scrypt
hash), session cookie, Host/Origin checks and lockout.
\end{{itemize}}
"""
    doc += ending("black-box description of the Workflow SoS")
    return doc


def sosdd():
    systems = [("WorkflowManager", "8482", "Book-keeper: recipes, sessions, dispatch, context, leases", "SysD, SysDD"),
               ("WorkflowExecutor", "8483", "Runs steps as consumer; adapters; Choreographer executor", "SysD, SysDD"),
               ("WorkflowConfigurator", "8484 / 8485", "Design tool and console", "SysD, SysDD")]
    service_map = [
        ("workflowConfiguration", "WfC", "Engineer (console), tools", "list-clouds, list-services, list-executors, list-recipes, get-recipe, validate-recipe, store-recipe"),
        ("workflowRecipe", "WfM", "WfC, tools", "validate, register, query, get, retire, compile"),
        ("workflowInstance", "WfM", "Owner, product", "start, abort, query, get-status, get-trace"),
        ("workflowWorkItem", "WfM", "WfE", "claim, report"),
        ("workflowContext", "WfM", "WfE", "get-parameters, get-results, put-result"),
        ("workflowLease", "WfM", "WfE", "reserve, poll, renew, release"),
        ("workflowManagement", "WfM", "Sysop", "query-worklog, query-executors, query-work-items"),
        ("workflowExecution", "WfE", "WfM", "service-info, start, abort, get-status"),
        ("workflowProduct", "Product", "WfM", "retrieve-process-step, store-process-step-result"),
        ("generalManagement", "all", "Sysop", "get-log, get-config"),
    ]
    rules = [("A-Start", r"$M_A(\alpha)$ inactive, predecessor action completed", r"$M_A(\alpha) :=$ active", "WfM"),
             ("S-Enable", r"action active, $M(s)$ inactive, all $p \in pre(s)$ completed", r"$M(s) :=$ enabled", "WfM; work item published or dispatched"),
             ("S-Bind", r"$M(s)$ enabled, an executor assignment and orchestration $\pi \neq \bot$", r"$M(s) :=$ running, $\beta(s) := \pi$", r"WfM chooses $\varepsilon(s)$; WfE orchestrates and reports BOUND"),
             ("S-Done", r"$M(s)$ running, invocation returns $d' \neq fail$", r"$M(s) :=$ completed, $\xi(s) := d'$", "WfE invokes, stores and reports SUCCESS"),
             ("A-Finish", r"action active, all its steps completed", r"$M_A(\alpha) :=$ completed", "WfM"),
             ("Z-Close", "all actions completed", r"$status :=$ completed", "WfM; results to product"),
             ("S-FailBind", "no executor/provider can be bound", r"$status :=$ failed", "WfM (BIND\\_FAILED or unreachable on all candidates, claim timeout)"),
             ("S-FailInvoke", "invocation fails", r"$status :=$ failed", "WfE reports FATAL\\_ERROR (or ERROR without retry)")]
    codes = [("SUCCESS", "S-Done", "Continue."), ("FATAL\\_ERROR", "S-FailInvoke", "Session failed (absorbing); S-FailBind when the step was not bound yet."),
             ("ERROR", "extension x-rebind or x-retry", "Under a retry policy: exclude the executor and bind again (\\texttt{rebind}), or dispatch to the same executor after a backoff (\\texttt{retry}); otherwise as FATAL\\_ERROR."),
             ("ABORTED", "--", "Acknowledges an abort (extension x-abort has already failed the session)."), ("BOUND", "completes S-Bind", "Executor may invoke."),
             ("BIND\\_FAILED", "S-Bind not possible", "Next executor, otherwise S-FailBind.")]
    ext = [("x-rebind", "Another executor after ERROR; the one that failed is excluded", "Implemented"),
           ("x-abort", "External abort", "Implemented"), ("x-timeout", "Step timeout", "Implemented"),
           ("compensation", "Release a failed session's leases", "Implemented (configurable)"),
           ("x-retry", "Same-executor retry after a backoff on ERROR (2026-11-24)", "Implemented"),
           ("x-guard / x-skip", "Conditions on transitions, dead-path elimination", "Implemented"),
           ("qnty $>$ 1", "Repeated execution", "Not implemented (refused by M01)"),
           ("federation", "Delegation of actions across clouds", "Not implemented")]
    grants = [("Session owner, product", "WorkflowManager", "workflowInstance: start, query, get-status, get-trace, abort"),
              ("WorkflowConfigurator", "WorkflowManager", "workflowRecipe: validate, register, query, get"),
              ("WorkflowConfigurator", "ServiceRegistry", "serviceRegistryManagement: system-query, service-query (SR management policy \\texttt{authorization})"),
              ("WorkflowExecutor", "WorkflowManager", "workflowWorkItem, workflowContext, workflowLease: all operations"),
              ("WorkflowManager", "WorkflowExecutor", "workflowExecution: service-info, start, abort, get-status"),
              ("WorkflowExecutor", "Equipment", "each step service (and the services in $dep(s)$ needed by the provider)"),
              ("WorkflowManager", "Product", "workflowProduct: both operations"),
              ("Engineering tool", "WorkflowConfigurator", "workflowConfiguration: as needed")]
    status = [("Recipe model, codec, schema, validator (30 codes)", "Implemented", "--", "--"),
              ("Session rules, Theorem~1 count, snapshot and resume", "Implemented", "--", "--"),
              ("Push and pull dispatch, context, leases", "Implemented", "--", "--"),
              ("Event dispatch, workflow events", "Not implemented", "--", "--"),
              ("Product integration", "Consumer side only", "--", "--"),
              ("Choreographer compatibility (both directions)", "Implemented, verified with Choreographer 5.4.0", "--", "--"),
              ("WorkflowConfigurator and editor", "Implemented", "--", "--"),
              ("Federation, disconnected working copy", "Not implemented", "--", "--"),
              ("Conformance vectors for cross-language parity", "Not written", "--", "--")]
    deploy_rows = [("WorkflowManager", "1 per work-centre local cloud", "8482", "embedded H2 database \\texttt{ah\\_workflow} in a private volume \\texttt{/data}", "1 GiB"),
                   ("WorkflowExecutor", "1 or more per workstation", "8483", "none (in-memory H2 log)", "768 MiB"),
                   ("WorkflowConfigurator", "1 per cloud (optional)", "8484, console 8485 (loopback)", "none (in-memory H2 log)", "--")]
    fig_wb = figure(whitebox(), "White-box view: the constituent systems in a workstation local cloud and the services between them.", "fig:whitebox", scale="\\textwidth")
    doc = preamble("SoSDD") + front(TITLE, "System of Systems Design Description (white box)", "SoSDD",
                                    r"This document describes the design of the Arrowhead Workflow System of Systems: its constituent systems, the services between them, the recipe and execution model they implement, the interaction sequences, deployment, security realisation, implementation status and verification. The black-box view is given in the SoSD. Documentation edition %s." % EDITION)
    doc += rf"""
\section{{Overview}}
\label{{sec:overview}}
The Workflow SoS consists of three Arrowhead support systems that run in a local cloud next to its core systems:
\begin{{itemize}}
\item the \textbf{{WorkflowManager}} (WfM), which keeps the books: recipes, sessions and their state;
\item one or more \textbf{{WorkflowExecutors}} (WfE), which initiate the work: they bind and invoke the providers of steps;
\item the \textbf{{WorkflowConfigurator}} (WfC), with which workflows are designed and stored.
\end{{itemize}}
The design follows the separation of concerns \emph{{Plant Description plans, the Workflow Manager sequences, Orchestration
realises}}~\cite{{derhamy2018}}: the WorkflowManager never invokes equipment, the executors never store recipes, and service
binding, authorization and QoS remain with the core systems.
{fig_wb}

\subsection{{Constituent systems}}
{table(["System", "Port", "Role", "Documents"], systems, ["3.6cm", "2.2cm", "6.6cm", "3.4cm"])}

\newpage
\section{{Service architecture}}
\label{{sec:services}}
{table(["Service", "Provider", "Consumers", "Operations"], [(r"\texttt{%s}" % a, b, c, esc(d)) for a, b, c, d in service_map], ["3.6cm", "1.6cm", "3.4cm", "7.2cm"])}
All services are HTTP/TLS/JSON services, version 1.0.0, interface template \texttt{{generic\_https}}, policy
\texttt{{CERT\_AUTH}}; each operation is registered with its method and path and authorized separately. The executors are
discovered by the WorkflowManager as providers of \texttt{{workflowExecution}} in the ServiceRegistry with the metadata
\texttt{{workflowCapabilities}}, \texttt{{workflowServices}}, \texttt{{workflowWorkstation}} and \texttt{{workflowMode}}, registered
with \texttt{{unrestrictedDiscovery}} so that RESTRICTED discovery does not hide them.

\section{{Workflow model}}
\label{{sec:model}}
\subsection{{Recipe}}
A recipe realises the formal tuple $W = (P, A, S, \theta, \iota, \mu, q, pre, \eta, qnty, dep, \kappa)$ of~\cite{{varga2026}}:
actions $A$ with \texttt{{firstAction}} and \texttt{{next}}, steps $S$ per action, the service requirement $\theta(s)$
(\texttt{{service}}), successors (\texttt{{next}}, giving $pre$), the executor requirement $\eta(s)$ (\texttt{{executor}}), the
quantity (\texttt{{policy.quantity}}, only 1), the data dependencies $dep(s)$ (\texttt{{dep}}) and the input composition
$\kappa_s$ (\texttt{{input}}, a template over $\rho$ and $\xi|dep(s)$). Extensions add \texttt{{guards}} per successor,
retry and timeout policies, and resources with lease modes reserve/use/release. Recipes are JSON documents
(\texttt{{model/recipe.schema.json}}), decoded strictly (unknown fields rejected) and stored canonically and immutably per
name and version. The data model is specified in the \texttt{{workflowRecipe}} SD and IDD.

\subsection{{Validation}}
The validator (\texttt{{workflow-model}}, \texttt{{RecipeValidator}}, plus the WorkflowManager's \texttt{{CatalogueCheck}} through
the \texttt{{RecipeCheck}} SPI) reports coded findings: structure S00--S09 and P01 (W1--W2), data flow D01--D05 (W3--W4,
Lemma~2), guards G01--G05, leases L01--L05 (premise of Proposition~1), model limits M01--M02, and catalogue C01--C02
against the running cloud. The verdict is the worst outcome (REJECT $>$ CLARIFY $>$ REVISE $>$ ACCEPT); only ACCEPT is
registered. The WorkflowConfigurator mirrors the local checks with the same codes for immediate feedback.

\section{{Execution semantics}}
\label{{sec:semantics}}
\subsection{{Session state}}
A session is one execution of one recipe version for one subject. Its state is
$z = (M, M_A, \beta, \varepsilon, \xi, status)$ with step marking $M: S \rightarrow \{{inactive, enabled, running,
completed\}}$ (plus \emph{{skipped}} under guards), action marking $M_A$, bindings $\beta$, executor assignment
$\varepsilon$, stored results $\xi$ and $status \in \{{running, completed, failed\}}$. The initial state has every step
inactive and $\alpha_0$ active. The consumer of a step $c_z(s)$ is the assigned executor; orchestration is always
requested with it as requester, which makes the workflow layer a conservative extension of Arrowhead.

\subsection{{Transition rules}}
{table(["Rule", "Premise", "Effect", "Realisation"], rules, ["2.0cm", "5.4cm", "3.6cm", "4.8cm"])}
For well-formed recipes without extensions a run has at most $3|S| + 2|A|$ transitions (exactly that many on success),
for example 11 for the three-step temperature control, 33 for the nine-step forklift recipe, and 9 when the
temperature switch fails. The WorkflowManager reports the count and the bound for every session.

\subsection{{Result codes}}
{table(["Report", "Model", "Reaction of the WorkflowManager"], codes, ["2.8cm", "3.6cm", "9.4cm"])}

\subsection{{Extensions beyond the proven model}}
{table(["Extension", "Purpose", "Status"], ext, ["3.0cm", "8.2cm", "4.6cm"])}
Sessions that use an extension list it in \texttt{{extensions}} and are marked \texttt{{withinModel = false}} in the trace;
conformance checks apply only to the others.

\subsection{{Shared resources}}
Proposition~1 (exclusive tool use) needs reserve $\prec$ every tool-using step $\prec$ release, which the validator checks
(L02, L03, L05), and a tool manager granting one session at a time. The lease variant of \texttt{{workflowLease}} grants in
FIFO order and expires unrenewed leases; tool-using steps renew with the token from $\xi(s_{{res}})$ immediately before
use. Exclusive use then additionally assumes that each lease outlasts the longest tool step (L04); this is checked on runs
but not proved.

\newpage
\section{{Interaction sequences}}
\label{{sec:sequences}}
\subsection{{Design and store a workflow}}
{figure(SCEN_DESIGN, "Designing and storing a workflow with the WorkflowConfigurator.", "fig:seq:design", scale="\\textwidth", here=True)}
\subsection{{Orchestrated run (push)}}
{figure(SCEN_PUSH, "A session in push mode: the WorkflowManager selects the executor, the executor binds and invokes the provider.", "fig:seq:push", scale="\\textwidth", here=True)}
\subsection{{Blackboard run with a shared tool (pull)}}
{figure(SCEN_PULL, "A pull-mode executor claims steps and uses a shared tool under a lease.", "fig:seq:pull", scale="\\textwidth", here=True)}
\subsection{{Product-driven run}}
{figure(SCEN_PRODUCT, "The product provides the recipe fragment and receives the results (Intelligent Product pattern).", "fig:seq:product", scale="0.9\\textwidth", here=True)}
\subsection{{Coexistence with the Choreographer}}
{figure(SCEN_CHOR, "A compiled recipe runs as a Choreographer plan on a WorkflowExecutor.", "fig:seq:chor", scale="\\textwidth", here=True)}
The compiled plan has no data flow: inputs, guards and dependencies are listed as \texttt{{lossySteps}}. The executor
compares service names case-insensitively without separators, because the Choreographer normalises definitions to
camelCase.

\section{{Deployment}}
\label{{sec:deployment}}
{table(["System", "Instances", "Ports", "Persistence", "Memory"], deploy_rows, ["3.2cm", "3.6cm", "3.0cm", "4.4cm", "1.6cm"])}
Each system runs as a container built from the primary Java source, with its certificate, trust store and secrets
mounted. A Workflow SoS needs a local cloud with ServiceRegistry, Authentication, ConsumerAuthorization and
DynamicServiceOrchestration; the WorkflowConfigurator additionally uses the LocalCloudConfigurator and the
SoLocalCloudConfigurator when present. The ServiceRegistry must run its management policy \texttt{{authorization}} for the
configurator's \texttt{{serviceRegistryManagement}} grant to take effect.

\section{{Security realisation}}
\label{{sec:security}}
The grants a deployment needs in ConsumerAuthorization:
{table(["Consumer", "Provider", "Service and operations"], grants, ["3.6cm", "3.2cm", "9.0cm"])}
In addition each system makes object-level checks: work-item holder (report, context), session owner (abort),
configured WorkflowManager (executor start/abort) and configured Choreographer (compatibility interface). Management
services are sysop only. The configurator console is reachable only on its loopback connector.

\section{{Implementation status and cross-language parity}}
\label{{sec:status}}
{table(["Area", "Java (reference)", "Go", "Rust"], status, ["6.6cm", "5.0cm", "2.1cm", "2.1cm"])}
The Go and Rust trees contain no workflow systems. Interoperability requires them to implement the IDDs of this set and
the behaviour of \texttt{{workflow-model}} (decoder, validator findings and verdicts, guard grammar, transition rules); the
conformance vectors (recipe in, verdict and transition trace out) that should make this checkable are not written yet.
Until then the SoS interoperates with Go and Rust \emph{{core}} systems only through the standard Arrowhead services.

\section{{Verification}}
\label{{sec:verification}}
\begin{{itemize}}
\item Unit tests: 35 model, 15 WorkflowManager, 7 WorkflowExecutor and 7 WorkflowConfigurator tests; 12 Node tests of the
editor model (all passed on 2026-11-24, with the embedded database).
\item Smoke test in an isolated Docker network with the embedded database: 18/18 checks on 2026-11-24, including three concurrent forklift sessions without
overlapping tool use and lease expiry after a blocked AGV.
\item Cloud test against the Java test cloud (RESTRICTED discovery, identity tokens, ConsumerAuthorization):
49/49 checks on 2026-11-21, before the database was embedded and not repeated since, covering registration, discovery, 401/403 cases, binding with the executors' certificates, transition
counts, pull mode, abort, compile, the configurator and guarded recipes; Choreographer compatibility 34/34 checks
against Choreographer 5.4.0.
\end{{itemize}}

\section{{Open issues}}
\begin{{enumerate}}
\item Whether the WorkflowManager later supersedes the Choreographer (option B) or both keep coexisting (option A, implemented).
\item Whether \texttt{{workflowLease}} stays in the WorkflowManager or moves to a dedicated resource system.
\item Standardising an \texttt{{equipmentJob}} capability interface for generic executors.
\item The \texttt{{workflowProduct}} contract and its link to AAS/digital-twin models; it needs an owner.
\item Formalising the extensions (recovery, timeout, quantity $>$ 1) and federation.
\item Go and Rust implementations with shared conformance vectors.
\end{{enumerate}}
"""
    doc += ending("white-box design description of the Workflow SoS")
    return doc


def files():
    return {"Workflow-v%s-SoSD.tex" % EDITION: sosd(), "Workflow-v%s-SoSDD.tex" % EDITION: sosdd()}
