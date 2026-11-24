#!/usr/bin/env python3
"""Writes the LaTeX sources of the workflow documentation set (edition 5.4.0) next to this folder.

Usage: python3 generator/generate.py [--only NAME-SUBSTRING]
The generated .tex files are the documents of record; regenerate after changing the service contracts, then run
./build-docs.sh. Hand edits to generated .tex files are overwritten by the next generation."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import render_services  # noqa: E402

from latex import AUTHOR, DATE, REVISED, soften  # noqa: E402

DATABASE = "Embedded database instead of a separate database server"
RETRY = "extension x-retry, extension names and retry states as implemented"
# Amendments of the second revision: document name part -> subject. A document that is not listed keeps its first edition.
AMENDMENTS = [
    ("Workflow-v5.4.0-SoSD.tex", DATABASE + "; data ownership."),
    ("Workflow-v5.4.0-SoSDD.tex", DATABASE + "; reaction to ERROR, ABORTED and unreachable executors; verification status."),
    ("WorkflowManager-v5.4.0-SysD.tex", DATABASE + "; " + RETRY + "; delimitations."),
    ("WorkflowManager-v5.4.0-SysDD.tex", DATABASE + "; " + RETRY + "; claim timeout, defaults and verification status."),
    ("WorkflowExecutor-v5.4.0-SysD.tex", "Data protection, log storage and behaviour after a restart."),
    ("WorkflowExecutor-v5.4.0-SysDD.tex", "Verification status."),
    ("WorkflowConfigurator-v5.4.0-SysD.tex", "Data protection; recipe summaries and the retry policy in the editor."),
    ("WorkflowConfigurator-v5.4.0-SysDD.tex", "Verification status."),
    ("workflowRecipe", "Retry policy as implemented."),
    ("workflowConfiguration", "Retry policy as implemented."),
    ("workflowProduct", "Retry policy as implemented."),
    ("workflowInstance", "Extension names; rule x-retry."),
    ("workflowWorkItem", "Claim timeout; reactions to the reports; waiting for a retry."),
    ("workflowExecution", "Retry policy as implemented; behaviour after a restart."),
    ("workflowManagement", "Work item states of the retry extension."),
]


def amend(name, text):
    subject = next((s for part, s in AMENDMENTS if part == name or ("-%s-" % part) in name), None)
    if subject is None:
        return text
    first = "& %s \\\\ \\hline\n\\end{tabularx}\n\n\\subsection{Quality Assurance}" % AUTHOR
    if text.count(first) != 1 or text.count("\\ArrowheadDate{%s}" % DATE) != 1:
        raise SystemExit("revision table of %s not found" % name)
    row = "& %s \\\\ \\hline\n2 & %s & \\arrowversion & %s & %s \\\\ \\hline\n\\end{tabularx}\n\n\\subsection{Quality Assurance}" % (AUTHOR, REVISED, subject, AUTHOR)
    return text.replace(first, row).replace("\\ArrowheadDate{%s}" % DATE, "\\ArrowheadDate{%s}" % REVISED)


OUT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def all_files():
    out = {}
    out.update(render_services.files())
    try:
        import render_systems
        out.update(render_systems.files())
    except ImportError:
        pass
    try:
        import render_sos
        out.update(render_sos.files())
    except ImportError:
        pass
    return {name: soften(amend(name, text)) for name, text in out.items()}


def main():
    only = sys.argv[2] if len(sys.argv) > 2 and sys.argv[1] == "--only" else None
    for name, text in sorted(all_files().items()):
        if only and only not in name:
            continue
        bad = [c for c in text if ord(c) > 127]
        if bad:
            raise SystemExit("%s contains non-ASCII characters: %r" % (name, sorted(set(bad))))
        with open(os.path.join(OUT, name), "w") as f:
            f.write(text)
        print(name)


if __name__ == "__main__":
    main()
