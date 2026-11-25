# Java support-system inventory

Reviewed against the primary source on 12 November 2026. There are **20 Java-Spring support-system folders: 19 current systems and one retained legacy system** (SoSConfigurator). The five core systems and shared common library are separate. Each support folder owns its build and deployment definition; none needs a combined JavaSupportRuntime.

Not counted above (added 19 November 2026, not yet released or deployed): [`ah5-Workflow-java/`](ah5-Workflow-java/README.md), a multi-module folder with the WorkflowManager, WorkflowExecutor and WorkflowConfigurator support systems and their shared model library. It coexists with the Choreographer and exists in Java only.

The [deployment guide](DEPLOYMENT-GUIDE.md) is the current folder, artifact and port inventory. The [root README](README.md#support-systems) describes each system's role. SoLocalCloudConfigurator replaces SoSConfigurator for the current SoLC workflow.

Historical test counts are not current acceptance evidence. The [previous inventory](_superseeded/JAVA-SUPPORT-FOLDERS-before-2026-11-12.md) and [October verification reports](_superseeded/README.md) retain their dated scope; their underlying logs remain under `verification/`. Use the [release notes](releases/5.3.0.md#7-documentation-and-evidence) for the release evidence boundaries and the [documentation overview](documentation/Documentation.md) for maintained editions and open integration follow-up.
