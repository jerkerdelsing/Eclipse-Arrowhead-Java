"""LaTeX building blocks in the Eclipse Arrowhead documentation style (roadmap/Documentation_templates/LaTeX)."""
import re

EDITION = "5.4.0"
DATE = "2026-11-23"
AUTHOR = "Documentation maintenance"
CONTACT = "Repository maintainers"
STATUS = "REVIEW DRAFT"

# ------------------------------------------------------------------ text helpers

_ESC = {"\\": r"\textbackslash{}", "&": r"\&", "%": r"\%", "$": r"\$", "#": r"\#", "_": r"\_",
        "{": r"\{", "}": r"\}", "~": r"\textasciitilde{}", "^": r"\textasciicircum{}"}


def esc(s):
    """Escape plain text for LaTeX."""
    return "".join(_ESC.get(c, c) for c in str(s))


def tt(s):
    """Monospace identifier or path, breakable after '/', '.', '-' and '='."""
    out = esc(s)
    out = re.sub(r"([/.=-])", r"\1\\allowbreak{}", out)
    return r"\texttt{" + out + "}"


# ------------------------------------------------------------------ preambles (macros copied from the templates)

_COMMON = r"""\documentclass[a4paper]{arrowhead}

\usepackage[yyyymmdd]{datetime}
\usepackage{etoolbox}
\usepackage[T1]{fontenc}
\usepackage[utf8]{inputenc}
\usepackage{multirow}
\usepackage{longtable}
\usepackage{amssymb}
\usepackage{float}
\usepackage{tikz}
\usetikzlibrary{positioning,arrows.meta,fit,calc,shapes.misc,shapes.geometric}

\renewcommand{\dateseparator}{-}
\setlength{\emergencystretch}{3em}
\lstset{basicstyle=\ttfamily\footnotesize,breaklines=true,columns=fullflexible,frame=single,rulecolor=\color{gray!50},backgroundcolor=\color{gray!6}}
\tikzset{
  ahsys/.style={draw=ArrowheadBlue,very thick,fill=ArrowheadBlue!6,rounded corners=2pt,minimum width=3.2cm,minimum height=1.1cm,align=center,font=\small\bfseries},
  ahext/.style={draw=ArrowheadGrey,thick,dashed,fill=gray!5,rounded corners=2pt,minimum width=3.2cm,minimum height=1.0cm,align=center,font=\small},
  ahsrv/.style={draw=ArrowheadPurple,thick,fill=ArrowheadPurple!8,rounded corners=6pt,minimum height=0.55cm,inner xsep=4pt,align=center,font=\scriptsize\ttfamily},
  ahcsm/.style={draw=ArrowheadTeal,thick,fill=ArrowheadTeal!8,rounded corners=6pt,minimum height=0.55cm,inner xsep=4pt,align=center,font=\scriptsize\ttfamily},
  ahdb/.style={draw=ArrowheadOrange,thick,fill=ArrowheadOrange!8,cylinder,shape border rotate=90,aspect=0.25,minimum height=0.9cm,align=center,font=\scriptsize},
  ahflow/.style={-{Stealth[length=2.2mm]},thick,ArrowheadGrey},
  ahlabel/.style={font=\scriptsize,fill=white,inner sep=1pt,align=center},
}
"""

_SD_MACROS = r"""
%% Special references
\newcommand{\mref}[1]{{\textcolor{ArrowheadPurple}{\hyperref[sec:model:#1]{#1}}}}
\newcommand{\oref}[1]{{\textcolor{ArrowheadBlue}{\hyperref[sec:operations:#1]{#1}}}}
\newcommand{\pdef}[1]{{\textcolor{ArrowheadGrey}{#1\label{sec:model:primitives:#1}\label{sec:model:primitives:#1s}\label{sec:model:primitives:#1es}}}}
\newcommand{\pref}[1]{{\textcolor{ArrowheadGrey}{\hyperref[sec:model:primitives:#1]{#1}}}}

\newrobustcmd\osubsection[3]{
  \addtocounter{subsection}{1}
  \addcontentsline{toc}{subsection}{\protect\numberline{\thesubsection}operation \textcolor{ArrowheadBlue}{#1}}
  \subsection*{
    \thesubsection\quad
    operation
    \textcolor{ArrowheadBlue}{#1}
    (\notblank{#2}{\mref{#2}}{})
    \notblank{#3}{: \mref{#3}}{}
  }
  \label{sec:operations:#1}
}
\newrobustcmd\msubsection[2]{
  \addtocounter{subsection}{1}
  \addcontentsline{toc}{subsection}{\protect\numberline{\thesubsection}#1 \textcolor{ArrowheadPurple}{#2}}
  \subsection*{\thesubsection\quad#1 \textcolor{ArrowheadPurple}{#2}}
  \label{sec:model:#2} \label{sec:model:#2s} \label{sec:model:#2es}
}
"""

_IDD_MACROS = r"""
\newcommand{\fparam}[1]{\textit{\textcolor{ArrowheadBlue}{#1}}}
%% Special references
\newcommand{\mref}[1]{{\textcolor{ArrowheadPurple}{\hyperref[sec:model:#1]{#1}}}}
\newcommand{\oref}[1]{{\textcolor{ArrowheadBlue}{\hyperref[sec:operations:#1]{#1}}}}
\newcommand{\pdef}[1]{{\textcolor{ArrowheadGrey}{#1\label{sec:model:primitives:#1}\label{sec:model:primitives:#1s}}}}
\newcommand{\pref}[1]{{\textcolor{ArrowheadGrey}{\hyperref[sec:model:primitives:#1]{#1}}}}

\newrobustcmd\osubsection[5]{
  \addtocounter{subsection}{1}
  \addcontentsline{toc}{subsection}{\protect\numberline{\thesubsection}operation \textcolor{ArrowheadBlue}{#1}}
  \subsection*{
    \thesubsection\quad
    #2 \textcolor{ArrowheadPurple}{#3} \\
    \small
    \hspace*{0.075\textwidth}\begin{minipage}{0.1\textwidth}
      \vspace*{1mm}
      Operation: \\
      \notblank{#4}{Input: \\}{}
      \notblank{#5}{Output: \\}{}
    \end{minipage}
    \begin{minipage}{0.825\textwidth}
      \vspace*{1mm}
      \textcolor{ArrowheadBlue}{#1} \\
      \notblank{#4}{\mref{#4} \\}{}
      \notblank{#5}{\mref{#5} \\}{}
    \end{minipage}
  }
  \label{sec:operations:#1}
}
\newrobustcmd\msubsection[2]{
  \addtocounter{subsection}{1}
  \addcontentsline{toc}{subsection}{\protect\numberline{\thesubsection}#1 \textcolor{ArrowheadPurple}{#2}}
  \subsection*{\thesubsection\quad#1 \textcolor{ArrowheadPurple}{#2}}
  \label{sec:model:#2} \label{sec:model:#2s}
}
"""

_SYS_MACROS = r"""
%% Special references
\newcommand{\fref}[1]{{\textcolor{ArrowheadBlue}{\hyperref[sec:functions:#1]{#1}}}}
\newcommand{\mref}[1]{{\textcolor{ArrowheadPurple}{\hyperref[sec:model:#1]{#1}}}}
\newcommand{\sref}[1]{{\textcolor{ArrowheadPurple}{#1}}}
\newrobustcmd\msubsection[2]{
  \addtocounter{subsection}{1}
  \addcontentsline{toc}{subsection}{\protect\numberline{\thesubsection}#1 \textcolor{ArrowheadPurple}{#2}}
  \subsection*{\thesubsection\quad#1 \textcolor{ArrowheadPurple}{#2}}
  \label{sec:model:#2}
}
"""


def preamble(kind):
    macros = {"SD": _SD_MACROS, "IDD": _IDD_MACROS}.get(kind, _SYS_MACROS)
    return _COMMON + macros


def front(title, type_long, type_short, abstract, service_id=None, figure=None):
    sid = r"\ArrowheadServiceID{%s}" % service_id if service_id else ""
    sid_line = r"\Large{Service ID: \textit{``\arrowid''}}" if service_id else ""
    fig = figure or ""
    return rf"""
\begin{{document}}

%% Arrowhead Document Properties
\ArrowheadTitle{{{title}}}
{sid}
\ArrowheadType{{{type_long}}}
\ArrowheadTypeShort{{{type_short}}}
\ArrowheadVersion{{{EDITION}}}
\ArrowheadDate{{{DATE}}}
\ArrowheadAuthor{{{AUTHOR}}}
\ArrowheadStatus{{{STATUS}}}
\ArrowheadContact{{{CONTACT}}}
\ArrowheadFooter{{\href{{www.arrowhead.eu}}{{www.arrowhead.eu}}}}
\ArrowheadSetup
%%

%% Front Page
\begin{{center}}
  \vspace*{{1cm}}
  \huge{{\arrowtitle}}

  \vspace*{{0.2cm}}
  \LARGE{{\arrowtype}}
  \vspace*{{1cm}}

  {sid_line}
  \vspace*{{\fill}}

  {fig}

  \vspace*{{1cm}}
  \vspace*{{\fill}}

  % Front Page Abstract
  \begin{{abstract}}
    {abstract}
  \end{{abstract}}

  \vspace*{{1cm}}
\end{{center}}

\newpage
%%

%% Table of Contents
\tableofcontents
\newpage
%%
"""


def ending(subject):
    """References, revision history and quality assurance, as in the templates."""
    return rf"""
\newpage
\bibliographystyle{{IEEEtran}}
\bibliography{{bibliography}}

\newpage

\section{{Revision History}}
\subsection{{Amendments}}

\noindent\begin{{tabularx}}{{\textwidth}}{{| p{{1cm}} | p{{2.2cm}} | p{{1.6cm}} | X | p{{3.6cm}} |}} \hline
\rowcolor{{gray!33}} No. & Date & Version & Subject of Amendments & Author \\ \hline
1 & {DATE} & \arrowversion & Initial edition: {subject}, written from the Java reference implementation (ah5-Workflow-java). & {AUTHOR} \\ \hline
\end{{tabularx}}

\subsection{{Quality Assurance}}

\noindent\begin{{tabularx}}{{\textwidth}}{{| p{{1cm}} | p{{2.2cm}} | p{{1.6cm}} | X |}} \hline
\rowcolor{{gray!33}} No. & Date & Version & Approved by \\ \hline
1 & & \arrowversion & Pending review \\ \hline
\end{{tabularx}}

\end{{document}}
"""


def table(header, rows, widths, caption=None, label=None):
    """A long table in the template style (grey header row, ruled cells). widths: list of p{..} or X-like sizes as strings."""
    cols = "|" + "|".join("p{%s}" % w for w in widths) + "|"
    head = r"\rowcolor{gray!33} " + " & ".join(header) + r" \\ \hline"
    body = "\n".join(" & ".join(r) + r" \\ \hline" for r in rows)
    cap = ""
    if caption:
        cap = r"\caption{%s}%s\\" % (caption, r"\label{%s}" % label if label else "")
    return rf"""
{{\small
\begin{{longtable}}{{{cols}}}
{cap}
\hline
{head}
\endhead
{body}
\end{{longtable}}
}}
"""


def figure(tikz, caption, label, scale=None, here=False):
    sc = r"\resizebox{%s}{!}{" % scale if scale else "{"
    pos = "H" if here else "ht!"
    return rf"""
\begin{{figure}}[{pos}]
  \centering
  {sc}
{tikz}
  }}
  \caption{{{caption}}}
  \label{{{label}}}
\end{{figure}}
"""


def listing(text, caption=None):
    cap = "[caption={%s}]" % caption if caption else ""
    return "\n\\begin{lstlisting}%s\n%s\n\\end{lstlisting}\n" % (cap, text.strip("\n"))


# ------------------------------------------------------------------ diagrams

def block_diagram(system, produced, consumed, note=None):
    """SysML-style block of one system: produced services on the right, consumed services (with providers) on the left."""
    lines = [r"\begin{tikzpicture}[node distance=4mm]"]
    n = max(len(produced), len(consumed), 1)
    height = max(1.4, 0.75 * n)
    lines.append(r"\node[ahsys,minimum height=%.2fcm,minimum width=3.6cm] (sys) at (0,0) {%s\\[-1pt]{\normalfont\scriptsize $\ll$system$\gg$}};" % (height, system))
    for i, s in enumerate(produced):
        y = (n - 1) / 2 * 0.75 - i * 0.75
        lines.append(r"\node[ahsrv,anchor=west] (p%d) at (3.4,%.2f) {%s};" % (i, y, esc(s)))
        lines.append(r"\draw[thick,ArrowheadPurple] (1.8,%.2f) -- (p%d.west);" % (y, i))
        lines.append(r"\fill[ArrowheadPurple] (1.8,%.2f) circle (1.4pt);" % y)
    for i, (s, prov) in enumerate(consumed):
        y = (n - 1) / 2 * 0.75 - i * 0.75
        label = esc(s) + (r"\\[-2pt]{\tiny\rmfamily %s}" % esc(prov) if prov else "")
        lines.append(r"\node[ahcsm,anchor=east] (c%d) at (-3.4,%.2f) {%s};" % (i, y, label))
        lines.append(r"\draw[thick,ArrowheadTeal,-{Stealth[length=1.6mm]}] (c%d.east) -- (-1.8,%.2f);" % (i, y))
    top = (n - 1) / 2 * 0.75 + 0.9
    if produced:
        lines.append(r"\node[font=\scriptsize\bfseries,ArrowheadPurple] at (5.0,%.2f) {produced};" % top)
    if consumed:
        lines.append(r"\node[font=\scriptsize\bfseries,ArrowheadTeal] at (-5.0,%.2f) {consumed};" % top)
    if note:
        lines.append(r"\node[font=\scriptsize,align=center] at (0,%.2f) {%s};" % (-(n - 1) / 2 * 0.75 - 1.1, note))
    lines.append(r"\end{tikzpicture}")
    return "\n".join(lines)


def sd_diagram(provider, service, consumers):
    """Service description block: consumers on the left, the service port of the provider on the right."""
    lines = [r"\begin{tikzpicture}"]
    lines.append(r"\node[ahsys] (prov) at (4.6,0) {%s\\[-1pt]{\normalfont\scriptsize $\ll$provider$\gg$}};" % provider)
    lines.append(r"\node[ahsrv] (srv) at (1.6,0) {%s};" % esc(service))
    lines.append(r"\draw[thick,ArrowheadPurple] (srv.east) -- (prov.west);")
    n = len(consumers)
    for i, c in enumerate(consumers):
        y = (n - 1) / 2 * 1.2 - i * 1.2
        lines.append(r"\node[ahext] (c%d) at (-3.0,%.2f) {%s\\[-1pt]{\scriptsize $\ll$consumer$\gg$}};" % (i, y, c))
        lines.append(r"\draw[ahflow] (c%d.east) -- (srv.west);" % i)
    lines.append(r"\end{tikzpicture}")
    return "\n".join(lines)


def sequence(participants, messages, width=3.6, step=0.72):
    """UML-like sequence diagram. participants: [(key, label)]; messages: [(src, dst, label, style)], style in
    '', 'reply', 'self', 'note'."""
    xs = {k: i * width for i, (k, _) in enumerate(participants)}
    total = (len(messages) + 1) * step + 0.4
    lines = [r"\begin{tikzpicture}"]
    for k, label in participants:
        lines.append(r"\node[ahsys,minimum width=2.5cm,minimum height=0.8cm,font=\scriptsize\bfseries] (h%s) at (%.2f,0) {%s};" % (k, xs[k], label))
        lines.append(r"\draw[densely dashed,gray] (h%s.south) -- (%.2f,%.2f);" % (k, xs[k], -total))
    y = -0.9
    for i, (src, dst, label, style) in enumerate(messages, 1):
        lab = r"{\tiny\bfseries %d}\ %s" % (i, label)
        if style == "self":
            x = xs[src]
            lines.append(r"\draw[ahflow] (%.2f,%.2f) -- ++(0.35,0) -- ++(0,-0.25) -- ++(-0.35,0);" % (x, y))
            lines.append(r"\node[font=\scriptsize,anchor=west] at (%.2f,%.2f) {%s};" % (x + 0.4, y - 0.12, lab))
        elif style == "note":
            lines.append(r"\node[font=\scriptsize\itshape,fill=ArrowheadYellow!25,draw=ArrowheadYellow,anchor=west] at (%.2f,%.2f) {%s};" % (xs[src] - 0.2, y - 0.08, label))
        else:
            dash = ",densely dashed" if style == "reply" else ""
            lines.append(r"\draw[ahflow%s] (%.2f,%.2f) -- (%.2f,%.2f) node[midway,above=0pt,font=\scriptsize,inner sep=1.5pt] {%s};" % (dash, xs[src], y, xs[dst], y, lab))
        y -= step
    lines.append(r"\end{tikzpicture}")
    return "\n".join(lines)
