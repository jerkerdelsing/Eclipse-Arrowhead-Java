"""The WorkflowManager's own observation types of the sysMonitor service: SD and AMQP IDD."""
from latex import DATE, EDITION, ending, front, listing, preamble, table, tt
from model import enum, struct
from render_services import closure, primitives_section, render_sd, roots_of, struct_section, type_tex

ISSUED = "2026-11-25"

struct("ExecutorLiveness", "Which executors are suspected of no longer running the work items they hold.",
       ("state", "LivenessState", "Summary of the last check."),
       ("heldWorkItems", "Integer", "Work items dispatched to, claimed by or bound to executors."),
       ("suspectedExecutors", "List<SuspectedExecutor>", "Executors that did not confirm a work item they hold."),
       ("uncheckedExecutors", "List<SystemName>", r"Executors that refuse \texttt{workflowExecution.get-status}; nothing is known about them."),
       ("lostWorkItems", "Integer", "Work items lost since the WorkflowManager started."),
       ("checkSeconds", "Duration", r"\texttt{workflow.executor.liveness.check-seconds}."),
       ("lostAfterSeconds", "Duration", r"\texttt{workflow.executor.liveness.lost-after-seconds}."))
struct("SuspectedExecutor", "An executor that did not confirm work items it holds.",
       ("executor", "SystemName", "The executor."), ("cause", "LossCause", "Why it is suspected."),
       ("since", "DateTime", "When its first work item was suspected."),
       ("workItemCount", "Integer", "Suspected work items of this executor."),
       ("workItems", "List<SuspectedWorkItem>", "The work items; at most 100 are listed over all executors."))
struct("SuspectedWorkItem", "A suspected work item.",
       ("workItemId", "WorkItemId", "The work item."), ("sessionId", "SessionId", "Its session."),
       ("action", "Name", "Its action."), ("step", "Name", "Its step."), ("attempt", "Integer", "Attempt number of the step."),
       ("bound", "Boolean", "Whether the executor reported BOUND, after which it uses the equipment."),
       ("lastSeen", "DateTime", "When the executor last confirmed or reported the work item."))
struct("WorkItemLost", "A work item that the WorkflowManager gave up because its executor is gone.",
       ("workItemId", "WorkItemId", "The work item."), ("sessionId", "SessionId", "Its session."),
       ("recipe", "String", r"Recipe as \texttt{name@version}."), ("action", "Name", "Its action."), ("step", "Name", "Its step."),
       ("attempt", "Integer", "Attempt number of the step."), ("executor", "SystemName", "The executor that held it."),
       ("cause", "LossCause", "Why it was given up."), ("detail", "String", "The cause in words."),
       ("bound", "Boolean", "Whether it was bound."),
       ("lastSeen", "DateTime", "When the executor last confirmed or reported it."),
       ("suspectedSince", "DateTime", "When the first check failed."), ("lostAt", "DateTime", "When it was given up."),
       ("detectionSeconds", "Duration", r"\texttt{lostAt} minus \texttt{lastSeen}: the longest time the loss can have gone unnoticed."),
       ("equipmentMayBeInUse", "Boolean", "True for a bound work item: an executor that is only cut off may still be running the step."),
       ("outcome", "LossOutcome", "What follows for the step."),
       ("workRepeated", "Boolean", "Whether a bound step is run again, so that its work may be done twice."))
enum("LivenessState", "Summary of the liveness check.",
     ("ok", "No executor is suspected."), ("suspect", "At least one executor is suspected."),
     ("unchecked", "None is suspected, but at least one refuses the question."), ("disabled", r"The check is off (\texttt{check-seconds} is 0)."))
enum("LossCause", "Why an executor is suspected.",
     ("notRunning", "It answers that it does not run the work item."), ("unreachable", "It does not answer."),
     ("unregistered", "It is no longer in the ServiceRegistry."))
enum("LossOutcome", "What follows for the step of a lost work item.",
     ("nextExecutor", "It was not bound: the next executor takes it, without an extension."),
     ("rebind", "x-rebind: another executor runs the step."), ("retry", "x-retry: the same executor runs it after the backoff."),
     ("sessionFailed", "S-FailInvoke: the retry policy allows no further attempt."))

OPS = [
    dict(name="executorLiveness", inp="", out="ExecutorLiveness", params=[], sd=r"""State. It is published in every publication cycle (\texttt{sysmonitor.publish.interval}) and at once when an
executor becomes suspected, is cleared, refuses the question, or a work item is lost. A subscriber therefore learns of a
possible loss at the first failed check, before the grace period \texttt{lostAfterSeconds} has passed, and can secure the
equipment. Quality is \texttt{good} in the states \texttt{ok} and \texttt{disabled}, otherwise \texttt{uncertain};
\texttt{heldWorkItems} is as of the last check."""),
    dict(name="workItemLost", inp="", out="WorkItemLost", params=[], sd=r"""Event, one per lost work item, with quality \texttt{uncertain}. It is kept, and published again with the same
observation id, until the broker has confirmed it, so a subscriber may receive it more than once. At most 1000
unpublished events are kept, in memory: a restart of the WorkflowManager before the broker confirms an event loses the
event (the worklog of \texttt{workflowManagement} still holds the loss)."""),
]

SVC = dict(
    name="sysMonitor", system="WorkflowManager", version="1.0.0", consumers=["SysMonitor"], structs=[], ops=OPS,
    summary=r"publishes observations of the WorkflowManager, here its own observation types about lost and suspected executors",
    overview=r"""An executor can disappear while it holds work items. The WorkflowManager notices this only after some time, and a
bound step that is repeated may be done twice while the first executor still uses the equipment. The two observation
types of this document make both visible: \texttt{executorLiveness} and \texttt{workItemLost}. They are published next to
\texttt{echo}, \texttt{power} and \texttt{status}, which every Arrowhead system publishes and which the sysMonitor
documents of the common library describe.""",
    prior_art=r"""The \texttt{sysMonitor} service of Eclipse Arrowhead 5 and its AMQP interface (SysMonitor-AMQP-IDD-5.2.2).""",
    use=r"""The types are listed in the service registration (metadata \texttt{observationTypes} and the operations of the
interface), where SysMonitor finds them. A user of SysMonitor selects them in the subscription of the provider
WorkflowManager, on their own or together with the common types; \texttt{*} includes them. SysMonitor stores the
selected observations, offers them in its history and publishes them on MQTT with the observation type in the topic.""",
    delimitations=r"""The observations report; they release no equipment and stop no executor. Whether the equipment is free
must be established at the equipment itself. Silence is not a sign of health: the absence of \texttt{echo} from the
WorkflowManager means that its executors are not being watched. Detection takes up to one check interval, the time-out
of an unanswered call (about 30\,s for a host that does not answer) and, for an unreachable executor, the grace period.""",
    authorization=r"""The broker permits a publisher only its own routing keys, identified by its certificate. SysMonitor receives
the offer by orchestration and needs a ConsumerAuthorization grant for the WorkflowManager's \texttt{sysMonitor}.""",
    extra_sd="")


def sd():
    text = render_sd(SVC)
    return (text.replace("The concrete\ninterface (HTTP/TLS/JSON) is specified", "The concrete\ninterface (AMQP/TLS/JSON) is specified")
            .replace("IDD of the same name", "AMQP IDD of the same name"))


def idd():
    structs, prims = closure(roots_of(SVC))
    rows = [(r"\oref{%s}" % o["name"], "publish", r"\texttt{\{cloud\}.sysmonitor.\allowbreak{}WorkflowManager.\allowbreak{}\{instance\}.\allowbreak{}%s}" % o["name"], type_tex(o["out"])) for o in OPS]
    ops = "\n".join(r"\osubsection{%s}{publish}{%s}{}{%s}" % (o["name"], r"\{base\}." + o["name"], o["out"]) + "\n" + o["sd"] for o in OPS)
    envelope = table(["Field", "Value"], [
        (r"\texttt{schemaVersion}", r"\texttt{1.0}"), (r"\texttt{observationId}", r"\texttt{WorkflowManager-\{type\}-\{UUID\}}; repeated unchanged when an event is published again."),
        (r"\texttt{providerSystem}, \texttt{serviceInstance}, \texttt{observationType}", "As in the routing key; SysMonitor refuses a difference."),
        (r"\texttt{sourceTime}", "When the value was taken (UTC)."), (r"\texttt{value}", r"\mref{ExecutorLiveness} or \mref{WorkItemLost}."),
        (r"\texttt{unit}", r"\texttt{1} for \texttt{executorLiveness}, null for \texttt{workItemLost}."),
        (r"\texttt{quality}", r"\texttt{good} or \texttt{uncertain}."), (r"\texttt{sequence}, \texttt{metadata}", "As for the common observation types.")], ["5.6cm", "10.2cm"])
    example = listing('''
{ "schemaVersion": "1.0",
  "observationId": "WorkflowManager-workItemLost-ef1abb7c-4d77-433d-8a7d-0c4aa8a78041",
  "providerSystem": "WorkflowManager", "serviceInstance": "WorkflowManager",
  "observationType": "workItemLost", "sourceTime": "2026-11-25T08:23:29.607Z",
  "value": { "workItemId": "8867b471-47c0-4392-abd9-38f949345e2b", "sessionId": 1,
    "action": "a", "step": "work", "attempt": 1, "bound": true,
    "recipe": "rebind@1.0.0", "executor": "WfeA", "cause": "unreachable",
    "detail": "executor WfeA cannot be reached: ...",
    "lastSeen": "2026-11-25T08:22:42.495Z",
    "suspectedSince": "2026-11-25T08:23:17.472Z",
    "lostAt": "2026-11-25T08:23:29.607Z", "detectionSeconds": 47.112,
    "equipmentMayBeInUse": true, "outcome": "rebind", "workRepeated": true },
  "unit": null, "quality": "uncertain" }''', "A lost work item (shortened, from the test with a killed executor)")
    doc = preamble("IDD") + front("sysMonitor AMQP/TLS/JSON", "Interface Design Description", "IDD",
        r"This document describes the AMQP/TLS/JSON interface of the WorkflowManager's own observation types of the \texttt{sysMonitor} service, service version 1.0.0, documentation edition %s. It realises the \texttt{sysMonitor} SD of the WorkflowManager." % EDITION, service_id="sysMonitor")
    doc += rf"""
\section{{Overview}}
\label{{sec:overview}}
The WorkflowManager publishes its observations as persistent AMQP 0-9-1 messages with content type
\texttt{{application/json}}~\cite{{rfc8259}} to the topic exchange of the local cloud (\texttt{{sysmonitor.exchange}}) and waits
for the broker's confirmation. The contract of the envelope and of \texttt{{echo}}, \texttt{{power}} and \texttt{{status}} is
SysMonitor-AMQP-IDD-5.2.2; this document adds the types \texttt{{executorLiveness}} and \texttt{{workItemLost}}.

Section~\ref{{sec:operations}} specifies the publications, Section~\ref{{sec:model}} the data models,
Section~\ref{{sec:subscription}} the subscription, Section~\ref{{sec:security}} the security requirements and
Section~\ref{{sec:implementations}} the implementations.

\newpage
\section{{Service Operations}}
\label{{sec:operations}}
{table(["Type", "Method", "Routing key", "Value"], rows, ["3.2cm", "1.5cm", "7.6cm", "3.5cm"])}
In the headings \texttt{{\{{base\}}}} is \texttt{{\{{cloud\}}.sysmonitor.WorkflowManager.\{{instance\}}}}.
{ops}

\subsection{{Envelope}}
Every message carries the common envelope:
{envelope}
{example}

\section{{Subscription}}
\label{{sec:subscription}}
SysMonitor offers the types it finds in the registration. A subscription to both, at SysMonitor's management service:
{listing('PUT /sysmonitor/management/subscriptions/WorkflowManager' + chr(10) + '{ "observationTypes": ["executorLiveness", "workItemLost"], "enabled": true }')}
SysMonitor then publishes them on MQTT as \texttt{{arrowhead/\{{cloud\}}/sysmonitor/\allowbreak{{}}data/workItemLost/\allowbreak{{}}WorkflowManager}} and
\texttt{{.../executorLiveness/WorkflowManager}}. A message that arrives twice is stored once (\texttt{{observationId}}).

\newpage
\section{{Data Models}}
\label{{sec:model}}
{chr(10).join(struct_section(n, True) for n in structs)}
{primitives_section(prims, True)}

\section{{Security}}
\label{{sec:security}}
The connection uses TLS~\cite{{rfc8446}} with mutual authentication; the broker takes the user from the certificate (SASL
EXTERNAL). Its access control list must allow WorkflowManager to publish
\texttt{{\{{base\}}.\#}} (Section~\ref{{sec:operations}}), which covers the two types. The offer is registered with
interface template \texttt{{generic\_amqps}} and policy \texttt{{CERT\_AUTH}}. Nothing is published, and no event is kept,
while \texttt{{sysmonitor.enabled}} is false.

\section{{Implementations}}
\label{{sec:implementations}}
\begin{{itemize}}
\item \textbf{{Java (reference).}} Class \texttt{{ExecutorObservations}} of \texttt{{workflow-manager}}; the common library publishes
it (\texttt{{SysMonitorSource}}, \texttt{{SysMonitorPublisher}}).
\item \textbf{{Go and Rust.}} They have no workflow systems and so do not publish these types: the workflow systems are
\emph{{not}} interoperable across the three implementations. By inspection of their sources, the SysMonitor systems in Go and
Rust accept any observation type, so they can store and subscribe to these; this has not been tested with them.
\end{{itemize}}
"""
    return doc + ending("interface design description of the WorkflowManager's sysMonitor observation types over AMQP/TLS/JSON")


def files():
    out = {"WorkflowManager-v%s-sysMonitor-SD.tex" % EDITION: sd(), "WorkflowManager-v%s-sysMonitor-AMQP-IDD.tex" % EDITION: idd()}
    return {n: t.replace(DATE, ISSUED) for n, t in out.items()}
