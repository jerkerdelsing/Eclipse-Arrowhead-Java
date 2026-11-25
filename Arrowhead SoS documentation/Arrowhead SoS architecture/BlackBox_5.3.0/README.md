# Arrowhead GSoSD 5.3.0

The maintained LaTeX source is GSoSD-Core-and-Support-Systems-5.3.0.tex, with its compiled PDF beside it. The Network Architecture section added on 10 November 2026 uses Arrowhead-5.2.uml and the Network diagram of Arrowhead-5.2.notation in the supplied Papyrus EclipseArrowhead-5.0-JD project. Since 15 November 2026 those two files are in the workspace at `OLD/profile-library-sysml-replaced-20261115/SysML v1.6/Papyrus/EclipseArrowhead-5.0-JD/`; their SHA-256 values are in the [network-model update record](../NETWORK-MODEL-UPDATE-2026-11-10.md). They are not part of the Eclipse-Arrowhead-SysML repository, which holds the 5.0 and 5.3 models. It distinguishes library definitions from deployment refinements.

Build from this folder:

```sh
latexmk -pdf -interaction=nonstopmode -halt-on-error GSoSD-Core-and-Support-Systems-5.3.0.tex
```

Network section and figure sources are in sections/network-architecture.tex and figures/network-architecture-model.tex. The earlier 5.2.2 PDF remains historical material. Model traceability and the corresponding white-box deployment sections are recorded in ../NETWORK-MODEL-UPDATE-2026-11-10.md.
