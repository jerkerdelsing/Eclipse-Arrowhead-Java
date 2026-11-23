"""Renders Service Description (SD) and Interface Design Description (IDD) documents."""
import re

from latex import (EDITION, preamble, front, ending, table, figure, listing, sd_diagram, tt, esc)
from model import PRIMS, S
from services import SERVICES, validator_codes_table

TOKEN = re.compile(r"[A-Za-z][A-Za-z0-9]*|[<>,]")


def type_tex(t):
    """Abstract type such as List<Map<Name,JsonValue>> with struct and primitive references."""
    out = []
    for tok in TOKEN.findall(t):
        if tok == "<":
            out.append("$<$")
        elif tok == ">":
            out.append("$>$")
        elif tok == ",":
            out.append(", ")
        elif tok in S:
            out.append(r"\mref{%s}" % tok)
        elif tok in PRIMS:
            out.append(r"\pref{%s}" % tok)
        else:
            raise ValueError("unknown type %s in %s" % (tok, t))
    return "".join(out)


def names_in(t):
    return [tok for tok in TOKEN.findall(t) if tok not in "<>,"]


def closure(roots):
    """All structures and primitives reachable from the roots, structures in alphabetical order."""
    structs, prims, todo = set(), set(), list(roots)
    while todo:
        n = todo.pop()
        if n in PRIMS:
            prims.add(n)
            continue
        if n in structs or n not in S:
            continue
        structs.add(n)
        d = S[n]
        if d["kind"] == "struct":
            for f in d["fields"]:
                todo.extend(names_in(f[1]))
        elif d["kind"] == "alias":
            todo.extend(names_in(d["of"]))
        else:
            prims.add("String")
    return sorted(structs), sorted(prims)


def roots_of(svc):
    roots = list(svc["structs"])
    for o in svc["ops"]:
        roots += [x for x in (o["inp"], o["out"]) if x]
        roots += [p[2] for p in o["params"]]
    return roots


def struct_section(name, idd):
    d = S[name]
    kind = {"struct": "struct", "enum": "enum", "alias": "alias"}[d["kind"]]
    out = [r"\msubsection{%s}{%s}" % (kind, name), d["desc"]]
    if d["kind"] == "struct":
        if idd:
            rows = [(r"\texttt{%s}" % esc(f), type_tex(t), "yes" if m else "no", desc) for f, t, desc, m in d["fields"]]
            out.append(table(["Field", "Type", "Mandatory", "Description"], rows, ["3.7cm", "4.2cm", "1.6cm", "6.3cm"]))
        else:
            rows = [(r"\texttt{%s}" % esc(f), type_tex(t), desc + ("" if m else " Optional.")) for f, t, desc, m in d["fields"]]
            out.append(table(["Field", "Type", "Description"], rows, ["3.9cm", "4.4cm", "7.5cm"]))
    elif d["kind"] == "enum":
        enc = r" Encoded as a JSON string." if idd else ""
        rows = [(r"\texttt{%s}" % v, desc) for v, desc in d["values"]]
        out.append(enc + table(["Value", "Meaning"], rows, ["3.9cm", "11.9cm"]))
    else:
        out.append(r" It is an alias of %s." % type_tex(d["of"]))
    return "\n".join(out)


def primitives_section(prims, idd):
    if idd:
        body = "\n".join(r"\subsubsection*{alias \pdef{%s}}%s" % (p, "\n" + PRIMS[p][1] + "\n") for p in prims)
        return r"""
\subsection{Primitives}
\label{sec:model:primitives}
The abstract primitive types of the SD are represented in JSON~\cite{rfc8259} as follows.
""" + body
    rows = [(r"\pdef{%s}" % p, PRIMS[p][0]) for p in prims]
    return r"""
\subsection{Primitives}
\label{sec:model:primitives}
Types and structures mentioned throughout this document that are assumed to be available to implementations of this
service. The concrete interpretation of each of these types must be provided by any IDD document claiming to implement
this service.
""" + table(["Type", "Description"], rows, ["3.2cm", "12.6cm"])


def provider_text(svc):
    if svc["name"] == "workflowProduct":
        return r"an Intelligent Product (consumed by the WorkflowManager)"
    if svc["name"] == "generalManagement":
        return r"each of the three workflow systems"
    return r"the %s system" % svc["system"]


# ---------------------------------------------------------------- SD

def render_sd(svc):
    name = svc["name"]
    structs, prims = closure(roots_of(svc))
    ops = svc["ops"]
    diag = figure(sd_diagram(svc["system"].split(",")[0], name, svc["consumers"]),
                  r"SysML block view of the \texttt{%s} service: provider and typical consumers." % name, "fig:SD", scale="0.92\\textwidth")
    ops_tex = []
    for o in ops:
        ops_tex.append(r"\osubsection{%s}{%s}{%s}" % (o["name"], o["inp"], o["out"]))
        ops_tex.append(o["sd"])
        if o["inp"] == "" and o["out"] == "":
            ops_tex.append(" The operation takes no input and returns no output.")
    op_rows = [(r"\oref{%s}" % o["name"], type_tex(o["inp"]) if o["inp"] else "--", type_tex(o["out"]) if o["out"] else "--") for o in ops]
    extra = svc["extra_sd"].replace("%%CODES%%", validator_codes_table()) if svc["extra_sd"] else ""
    doc = preamble("SD") + front(name, "Service Description", "SD",
                                 r"This document describes the \texttt{%s} service of the Eclipse Arrowhead workflow support systems, which %s. It is provided by %s, service version %s, documentation edition %s." % (name, svc["summary"], provider_text(svc), svc["version"], EDITION),
                                 service_id=name)
    doc += rf"""
\section{{Overview}}
\label{{sec:overview}}
{svc["overview"]}

The rest of this document is organized as follows.
In Section~\ref{{sec:operations}}, we describe the abstract message operations provided by the service.
In Section~\ref{{sec:model}}, we end the document by presenting the data types used by the mentioned operations.

\subsection{{Significant Prior Art}}
{svc["prior_art"]}

\subsection{{How This Service Is Meant to Be Used}}
{svc["use"]}

\subsection{{Important Delimitations and Dependencies}}
\label{{sec:delimitations}}
{svc["delimitations"]}

\paragraph{{Authorization.}} {svc["authorization"]}

\newpage
\section{{Service Interface}}
\label{{sec:operations}}
This section describes the interface of the \texttt{{{esc(name)}}} service. Each subsection names an abstract operation,
an input type and an output type, in that order. The input type is named inside parentheses, while the output type is
preceded by a colon. Input and output types are only denoted when accepted or returned, respectively, by the operation
in question. All abstract data types named in this section are defined in Section~\ref{{sec:model}}. The concrete
interface (HTTP/TLS/JSON) is specified in the IDD of the same name.
{diag}
The following interface operations are available.
{table(["Operation", "Input", "Output"], op_rows, ["4.4cm", "5.6cm", "5.8cm"])}
{chr(10).join(ops_tex)}
{extra}
\newpage
\section{{Information Model}}
\label{{sec:model}}
Here, all data objects that can be part of the \texttt{{{esc(name)}}} service are listed in alphabetic order. Each
subsection, which describes one type of object, begins with the \textit{{struct}} keyword (a collection of named fields,
each with its own data type), the \textit{{enum}} keyword (a closed set of values) or the \textit{{alias}} keyword. As a
complement to the explicitly defined types in this section, there is also a list of implicit primitive types in
Section~\ref{{sec:model:primitives}}.

{chr(10).join(struct_section(n, False) for n in structs)}
{primitives_section(prims, False)}
"""
    doc += ending("service description of %s" % esc(name))
    return doc


# ---------------------------------------------------------------- IDD

SECURITY_IDD = r"""
\section{Security}
\label{sec:security}
The interface is offered only over TLS~\cite{rfc8446} (TLS 1.3 or 1.2) with mutual authentication: the consumer must
present an Arrowhead certificate issued under the local cloud's certificate authority, and certificates on the
cloud's CRL are refused. The service is registered with interface template \texttt{generic\_https} and interface
policy \texttt{CERT\_AUTH}.

Every request must carry the consumer's identity token, obtained from the Authentication system and bound to the
consumer's certificate:
\begin{lstlisting}
Authorization: Bearer IDENTITY-TOKEN//<identity token>
\end{lstlisting}
A token issued to another system's certificate is refused (401). The provider then checks a ConsumerAuthorization
grant for the operation (\texttt{authorization.verify}, \texttt{service.authorization.enabled=true}); a missing grant is
refused with 403. %s
"""

ERRORS_IDD = r"""
\section{Errors}
\label{sec:errors}
Every error response carries an \mref{ErrorMessage} body with content type \texttt{application/json}, for example:
\begin{lstlisting}
{ "errorMessage": "Work item 5d2c0b1e-... is held by another executor",
  "errorCode": 403, "exceptionType": "FORBIDDEN",
  "origin": "POST /workflowmanager/work-item/report" }
\end{lstlisting}
Status codes follow HTTP semantics~\cite{rfc9110}: 400 invalid parameter, 401 authentication, 403 forbidden, 404 not
found, 409 conflict with the current state, 500 internal error and 503 failure of an external system.
"""


def variants(svc):
    return r"""
\section{Implementations}
\label{sec:implementations}
\begin{itemize}
\item \textbf{Java (reference).} \texttt{Eclipse-Arrowhead-java/ah-Workflow-java}, %s. Paths, methods and service
operations are defined once in \texttt{workflow-model} (\texttt{wire/WorkflowServices.java}, \texttt{wire/ServiceOperations.java}) %s
\item \textbf{Go.} Not implemented: \texttt{Eclipse-Arrowhead-go} has no workflow systems. A Go implementation must
provide exactly the paths, methods, JSON field names and status codes of this IDD to be interoperable.
\item \textbf{Rust.} Not implemented: \texttt{Eclipse-Arrowhead-rust} has no workflow systems. The same requirement applies.
\end{itemize}
Until the Go and Rust implementations exist, the workflow systems are \emph{not} interoperable across the three
implementations; only the Java systems interoperate with Java, Go and Rust core systems through the common Arrowhead
interfaces (ServiceRegistry, Authentication, ConsumerAuthorization, DynamicServiceOrchestration).
""" % ({"WorkflowManager": "module \\texttt{workflow-manager}", "WorkflowExecutor": "module \\texttt{workflow-executor}",
        "WorkflowConfigurator": "module \\texttt{workflow-configurator}"}.get(svc["system"], "modules \\texttt{workflow-manager} (consumer side), \\texttt{workflow-executor} and \\texttt{workflow-configurator}"),
       "and checked by the provider-side authorization filter." if svc["name"] not in ("workflowConfiguration", "workflowProduct", "generalManagement") else
       "or in the system's own constants class.")


def render_idd(svc):
    name = svc["name"]
    structs, prims = closure(roots_of(svc) + ["ErrorMessage"])
    product = name == "workflowProduct"
    rows = []
    for o in svc["ops"]:
        path = o["path"] if product else svc["base"] + o["path"] if name != "generalManagement" else "\\{base\\}" + o["path"]
        rows.append((r"\oref{%s}" % o["name"], esc(o["method"]), tt(path) if "{base}" not in path else r"\texttt{\{base\}}" + tt(o["path"]),
                     type_tex(o["inp"]) if o["inp"] else "--", type_tex(o["out"]) if o["out"] else "--"))
    ops_tex = []
    for o in svc["ops"]:
        if product:
            shown = "(provider-defined path)"
        elif name == "generalManagement":
            shown = "{base}" + o["path"]
        else:
            shown = svc["base"] + o["path"]
        ops_tex.append(r"\osubsection{%s}{%s}{%s}{%s}{%s}" % (o["name"], esc(o["method"]), esc(shown), o["inp"], o["out"]))
        ops_tex.append(o["sd"])
        if o["params"]:
            prow = [(r"\fparam{%s}" % esc(p[0]), p[1], type_tex(p[2]), p[3]) for p in o["params"]]
            ops_tex.append(table(["Parameter", "In", "Type", "Description"], prow, ["3.0cm", "1.4cm", "3.2cm", "8.2cm"]))
        resp = [("Success", o["ok"])] + [(c, w) for c, w in o["errors"]]
        ops_tex.append(table(["Status", "When"], resp, ["2.0cm", "13.8cm"]))
        if o["req"]:
            ops_tex.append(listing(o["req"], "Example request body"))
        if o["resp"]:
            ops_tex.append(listing(o["resp"], "Example response body"))
        if o.get("note"):
            ops_tex.append(o["note"])
    if product:
        iface = r"""The WorkflowManager consumes the service through the Arrowhead common library: it asks for the
operation by service definition and operation name and uses the HTTP method and path the product registered in the
ServiceRegistry. A product implementing this IDD therefore chooses its own base path, but must register the operations
\texttt{retrieve-process-step} and \texttt{store-process-step-result} with methods that accept a JSON body
(\texttt{POST} is recommended), interface template \texttt{generic\_https} and policy \texttt{CERT\_AUTH}."""
    elif name == "generalManagement":
        iface = r"""The base path \texttt{\{base\}} depends on the system: \texttt{/workflowmanager/general/mgmt} (port 8482),
\texttt{/workflowexecutor/general/mgmt} (port 8483) and \texttt{/workflowconfigurator/general/mgmt} (port 8484)."""
    else:
        port = {"WorkflowManager": "8482", "WorkflowExecutor": "8483", "WorkflowConfigurator": "8484"}[svc["system"]]
        iface = r"""The service is offered by the %s on its Arrowhead connector (default port %s) with base path %s.
The ServiceRegistry entry lists every operation with its HTTP method and path relative to the base path.""" % (svc["system"], port, tt(svc["base"]))
    sec = SECURITY_IDD % svc["authorization"]
    doc = preamble("IDD") + front(name + " HTTP/TLS/JSON", "Interface Design Description", "IDD",
                                  r"This document describes the HTTP/TLS/JSON interface of the \texttt{%s} service of the Eclipse Arrowhead workflow support systems, service version %s, documentation edition %s. It realises the abstract operations of the \texttt{%s} SD." % (name, svc["version"], EDITION, name),
                                  service_id=name)
    doc += rf"""
\section{{Overview}}
\label{{sec:overview}}
This document describes how the \texttt{{{esc(name)}}} service, which {svc["summary"]}, is realised with HTTP~\cite{{rfc9110}}
over TLS~\cite{{rfc8446}} and JSON~\cite{{rfc8259}} payloads. Request and response bodies use content type
\texttt{{application/json}}; JSON field names are case-sensitive and fields not listed in this document must not be sent
(the recipe decoder rejects unknown fields). Optional fields may be omitted; absent and \texttt{{null}} are equivalent.

{iface}

The rest of this document is organized as follows. Section~\ref{{sec:operations}} specifies each operation,
Section~\ref{{sec:model}} the JSON data models, Section~\ref{{sec:security}} the security requirements,
Section~\ref{{sec:errors}} the error representation and Section~\ref{{sec:implementations}} the implementations.

\newpage
\section{{Service Operations}}
\label{{sec:operations}}
{table(["Operation", "Method", "Path", "Input", "Output"], rows, ["3.0cm", "1.5cm", "5.3cm", "3.0cm", "3.0cm"])}
{chr(10).join(ops_tex)}

\newpage
\section{{Data Models}}
\label{{sec:model}}
The JSON objects exchanged by the operations, in alphabetic order. Field types refer to other data models or to the
primitives of Section~\ref{{sec:model:primitives}}.

{chr(10).join(struct_section(n, True) for n in structs)}
{primitives_section(prims, True)}

{sec}
{ERRORS_IDD}
{variants(svc)}
"""
    doc += ending("interface design description of %s over HTTP/TLS/JSON" % esc(name))
    return doc


def files():
    out = {}
    for name, svc in SERVICES.items():
        prefix = {"WorkflowManager": "WorkflowManager", "WorkflowExecutor": "WorkflowExecutor",
                  "WorkflowConfigurator": "WorkflowConfigurator"}.get(svc["system"], "Workflow")
        out["%s-v%s-%s-SD.tex" % (prefix, EDITION, name)] = render_sd(svc)
        out["%s-v%s-%s-HTTP-IDD.tex" % (prefix, EDITION, name)] = render_idd(svc)
    return out
