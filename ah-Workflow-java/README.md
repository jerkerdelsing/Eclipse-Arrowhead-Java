# ah-Workflow-java — WorkflowManager, WorkflowExecutor and WorkflowConfigurator

Three Eclipse Arrowhead 5.4.0 support systems that coexist with the Choreographer (architecture option A):

- **WorkflowManager** (port 8482): the bookkeeper. It keeps versioned, immutable recipes, validates them and runs workflow sessions with the execution rules of Varga, *Sensors* 2026, 26, 7025, §5.9. It also holds the session context (ρ, ξ) and the leases on shared resources, dispatches steps to executors (push) or publishes them for claiming (pull), reports to the Intelligent Product, and compiles recipes to Choreographer plans.
- **WorkflowExecutor** (port 8483): the step runner. It orchestrates each step's service with itself as the requester, composes the input κ_s, invokes the equipment through adapters, handles leases and reports the result. It also implements the Choreographer executor interface, so existing Choreographer plans can use it.
- **WorkflowConfigurator** (port 8484, browser console 8485): the graphical editor. It lists the configured local clouds (the running cloud, LocalCloudConfigurator designs, SoLocalCloudConfigurator member clouds) and their microservices, lets an engineer draw a workflow as a state machine (steps, transitions, conditions) or as a sequence diagram, and stores the result in the WorkflowManager (`workflowRecipe`). See [WorkflowConfigurator](#workflowconfigurator) below.

The design and its sources are in [ARCHITECTURE.md](ARCHITECTURE.md). §15 there lists what is implemented and what is not.

The Arrowhead-style LaTeX documentation (SysD and SysDD per system, SD and HTTP IDD per service, and the workflow SoSD and SoSDD) is in [documentation/v5.4.0](documentation/v5.4.0/README.md).

> **Interoperability:** these systems exist **in Java only**. No Go or Rust implementation exists yet, so they are **not interoperable** across the three primary implementations. To port them, implement `workflow-model` (recipe, validator codes, transition rules) and the wire contract below. The examples and the conformance numbers serve as test vectors.

## Layout

| Folder | Content |
| --- | --- |
| `workflow-model/` | Recipe model and codec, validator (`RecipeCheck` SPI), `WorkflowSession` (the eight rules, Theorem 1 count, snapshot), `InputComposer` (κ_s), Choreographer plan compiler, wire DTOs and service names. No Spring dependency |
| `workflow-manager/` | Spring Boot system; database scripts in `src/main/resources/database/` (schema `ah_workflow`) |
| `workflow-executor/` | Spring Boot system; embedded H2 database for the common-library log table only |
| `workflow-configurator/` | Spring Boot system with the browser editor in `src/main/resources/web/` (`model.js`: diagram ⇄ recipe, no DOM; `app.js`: views and inspector); Node tests of `model.js` in `src/test/js/` |
| `model/recipe.schema.json` | Canonical recipe format |
| `examples/` | `temperature-control` (11 transitions) and `shared-tool-tightening` (33 transitions), from the Sensors paper; `temperature-branching` (guarded transitions, extension x-guard) |
| `verification/smoke/` | End-to-end smoke test on an isolated Docker network (development profile) |
| `verification/cloud/` | Test in the running Java test cloud (secure profile: mutual TLS, identity tokens, ServiceRegistry, DSO, ConsumerAuthorization) |
| `deployment/` | Secure deployment properties of the three systems (`--spring.config.additional-location`) |
| `vendor/` | Private copy of the Arrowhead common library (same snapshot as `ah5-Choreographer-java-spring/vendor`) |

## Build and test

Java 21 and Maven 3.9 are needed. From this folder:

```bash
mvn -B install
```

Without a local Maven, use the Docker image:

```bash
docker run --rm -v "$PWD":/src -v "$HOME/.m2":/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B install
```

This produces `workflow-manager/target/arrowhead-workflowmanager-5.4.0.jar`, `workflow-executor/target/arrowhead-workflowexecutor-5.4.0.jar` and `workflow-configurator/target/arrowhead-workflowconfigurator-5.4.0.jar`, and runs 56 tests. The editor model has its own tests (Node 20 or later):

```bash
node --test workflow-configurator/src/test/js/model.test.mjs
```

End-to-end smoke test (needs Docker; uses the development security profile and static wiring, so it never touches a running cloud):

```bash
verification/smoke/run-smoke.sh
```

Test in the running Java test cloud (`../../Arrowhead-test-clouds/Arrowhead-test-cloud-java`, override with `CLOUD_DIR`):

```bash
verification/cloud/run-cloud-test.sh
```

It issues 30-day certificates of the cloud CA into `verification/cloud/.state/` and creates Authentication identities and ConsumerAuthorization policies for the test systems. It adds WorkflowManager, WorkflowConfigurator, the executors and ServiceRegistry to `authorization.verify`. It extends four shared policies, saving each stored policy first in `.state/backup`: `choreographerExecutor` (executor `WfeChor`), and `localCloudConfiguration`, `soLocalCloudConfiguration` and `serviceRegistryManagement` (read operations for the WorkflowConfigurator). The cloud's Choreographer then runs a compiled recipe on `WfeChor`, and the WorkflowConfigurator stores a guarded workflow that the WorkflowManager runs. The console password of the WorkflowConfigurator is in `.state/console-password`; with `KEEP=1` the editor is at <http://localhost:18485/>. The systems run in their own compose project `wf-cloud` on the cloud network, and no cloud container is changed. Afterwards `teardown.sh` removes the identities, policies and registry entries, restores `authorization.verify` to the cloud's declared list and the shared policies to the saved ones (a policy that did not exist is revoked), and removes the test plan and its sessions from the Choreographer (its worklog keeps the entries). With `KEEP=1` everything stays running until you run `verification/cloud/teardown.sh` yourself. The cloud calls run from a container on the cloud network, because Docker Desktop's host ports of the ServiceRegistry, Authentication and ConsumerAuthorization may not forward TLS.

## WorkflowConfigurator

The editor runs in a browser on the operator's machine. The console connector (plain HTTP, `console.port`, default 8485) listens on loopback, or in a container is published only to the host's loopback. It accepts only local `Host`/`Origin` names and needs a password login: HttpOnly SameSite=Strict session cookie, per-client lockout, and only an scrypt hash of the password in the process (`console.password-hash`, the LocalCloudManager console format). The same operations are the Arrowhead service `workflowConfiguration` on the mutual-TLS port 8484, for other engineering tools, with provider-side ConsumerAuthorization.

**Drawing.**
- Pick a local cloud at the top.
- The left panel lists the cloud's systems and their services with their operations. Drag a service into an action frame, or double-click it to append it after the selected step.
- Drag from a step's right handle to another step to add a transition (inside one action; the action list sets the order of actions).
- Click a transition to give it a condition, written directly or with the condition builder; "Otherwise" negates the sibling branch.
- The inspector edits the step: service, consumer (executor capability η(s) or the WorkflowManager), input template κ(s) with a reference picker, static parameters, timeout and retry.
- Moving around: drag the background or scroll (two fingers on a trackpad) to pan; pinch, or scroll with Ctrl, Alt or Cmd held, to zoom around the pointer; Fit shows the whole workflow. A click on a step selects it; it moves only after the pointer has moved a few pixels.
- Arrange lays the steps out in columns and widens the gap before a column so that the condition labels fit there. A label sits on its transition just in front of the target step; if that spot is taken, it moves up or down and keeps a dotted leader to its transition.
- The sequence view shows the same workflow as a sequence diagram: lifelines for the WorkflowManager, the executor capabilities and the providers; `par` fragments for parallel steps; `alt`/`opt` fragments for guarded steps. Dropping a service there appends it after the selected step.

**Checks and storing.**
- "Checks" lists problems found while drawing (marked "while drawing") and the WorkflowManager's findings after Validate, each with its validator code. Click a line to select the step, transition or action it is about; "How to fix?" explains the code and what to change in the editor. Save needs the verdict ACCEPT, so REVISE and CLARIFY findings must be resolved first; information findings such as G04 need nothing.
- Validate asks the WorkflowManager.
- Save validates and registers the recipe in the WorkflowManager. Recipes are immutable, so different content under an existing version is refused, and the editor offers the next patch version.
- Open loads any stored recipe. Positions and provider hints are kept per recipe in the browser only; the WorkflowManager stores the recipe itself.

**Conditions (extension x-guard).** A step may give each successor in `next` a condition (`guards`). The transition is taken when the step has completed and the condition holds over `${param.<name>}` and `${result.<step>.<path>}`. A step whose incoming transitions are all dead is skipped (x-skip), and so are the steps after it, unless another of their incoming transitions is taken (dead-path elimination). The grammar is in `workflow-model/.../GuardExpression.java`: `== != < <= > >= && || !`, parentheses, numbers, quoted strings, `true`, `false`, `null`. A condition may read only results of its step and of steps that precede it (validator G01–G05). Without conditions a session is a run of the Sensors model. A session that skips a step carries the extension `guard`, and Theorems 1–2 do not cover it. Choreographer plans have no conditions, so `compile` reports guarded steps as lossy.

**Sources of the cloud list.**
- `live`: systems and services from the ServiceRegistry (`serviceRegistryManagement` system-query and service-query).
- `lcc:<id>`: the designs in LocalCloudConfigurator (`localCloudConfiguration` catalog, list-configurations). Their services are the running ones of the design's systems.
- `solc:<design>:<cloud>`: the member clouds of the SoLocalCloudConfigurator designs (`soLocalCloudConfiguration` list-designs, get-design). Another cloud's ServiceRegistry is not reachable, so the editor names their service definitions itself.

An unavailable source is shown as a red dot and the others still work. The configurator reads the running cloud through `serviceRegistryManagement`, which needs a grant and the ServiceRegistry's `management.policy=authorization` (a `whitelist` policy ignores grants). When the ServiceRegistry refuses management queries, the configurator falls back to discovery. Under the RESTRICTED policy that shows only services offered with `unrestrictedDiscovery`, and the editor says so.

ConsumerAuthorization for the WorkflowConfigurator:
- `workflowRecipe` (validate, register, query, get) at the WorkflowManager;
- `localCloudConfiguration` catalog, list-configurations and get-configuration;
- `soLocalCloudConfiguration` list-designs and get-design;
- `serviceRegistryManagement` system-query and service-query. The ServiceRegistry must be in `authorization.verify` for its `management.policy=authorization` to consult ConsumerAuthorization;
- itself in `authorization.verify`;
- tools that use `workflowConfiguration` → its operations.

## Deployment prerequisites

- **Database.** Create the schema with `create_empty_db.sql`, `create_tables.sql` and `grant_privileges.sql` (MariaDB or MySQL 8.4). Hibernate validates the schema at start-up.
- **System names** are normalized to PascalCase by the common library (`wfe-agv` becomes `WfeAgv`). Use PascalCase names; with TLS the name is the certificate CN. Run one WorkflowExecutor per workstation or capability set, each with its own `spring.application.name`.
- **Secure configuration.** Start each system with `--spring.config.additional-location=file:deployment/<system>.properties`. The keystore `/certs/<System>.p12`, the truststore and the secrets `ah.store-password` and `ah.identity-password` (plus `ah.db-password` for the WorkflowManager) are files in `/run/secrets`. Each system needs an Authentication identity of its own name. `verification/cloud/` is a worked example.
- **ConsumerAuthorization:**
  - executors → `workflowWorkItem`, `workflowContext`, `workflowLease`, plus the step services and their dependencies;
  - WorkflowManager → `workflowExecution` and `workflowProduct`;
  - clients and products → `workflowInstance`;
  - engineering tools → `workflowRecipe`;
  - Choreographer → the executor's Choreographer interface. With compatibility enabled, the executor → `choreographerExecutor`;
  - WorkflowManager and every executor → ConsumerAuthorization `authorization.verify`. With `service.authorization.enabled=true` each provider asks ConsumerAuthorization about every service operation it receives.
- **Executor selection.** The WorkflowManager finds executors as providers of `workflowExecution` in the ServiceRegistry. The selection uses the metadata `workflowCapabilities` (cap(w)), `workflowServices` (runnable θ, `*` = any), `workflowWorkstation` and `workflowMode`. The executor registers these itself from its configuration. It also marks the offer `unrestrictedDiscovery`, so the WorkflowManager finds executors under the ServiceRegistry's RESTRICTED discovery policy without being in `service.discovery.direct.access`.

## Configuration

### WorkflowManager

| Property | Default | Meaning |
| --- | --- | --- |
| `server.port` | 8482 | |
| `workflow.dispatch.default` | `push` | `push`, `pull` or `event` (`event` behaves like `pull`; EventHandler is not wired) |
| `workflow.executors` | — | Extra static executors, `;`-separated: `Name\|scheme://host:port\|cap,cap\|service,service\|workstation` |
| `workflow.pull.claim-timeout` | 300 | Seconds an open work item may wait for a claim; then S-FailBind |
| `workflow.lease.default-duration` | 120 | Seconds, when the recipe declares none |
| `workflow.lease.release-on-failure` | false | Compensation extension: release a failed session's leases at once |
| `workflow.workstation.id` | — | Identity towards the Intelligent Product |
| `workflow.validator.catalogue-check` | true | Check executor availability (C02) and, with the next switch, θ(s) (C01) at validation |
| `workflow.validator.catalogue-check.services` | true (`false` in `deployment/`) | C01. Needs a complete lookup: WorkflowManager in `service.discovery.direct.access`, or the OPEN discovery policy |
| `service.authorization.enabled` | false (`true` in `deployment/`) | Provider-side ConsumerAuthorization check of every service operation |
| `workflow.standalone` | false | Development only: no ServiceRegistry |

### WorkflowConfigurator

| Property | Default | Meaning |
| --- | --- | --- |
| `server.port` | 8484 | Arrowhead connector (mutual TLS in `deployment/`) |
| `console.port`, `console.address` | 8485, 127.0.0.1 | Browser console; 0 disables it |
| `console.password-hash` | — | `scrypt:<salt>:<hash>` (N=16384, r=8, p=1); without it every login is refused. In `deployment/` from the secret `ah.console-password-hash` |
| `console.allowed-hosts` | localhost,127.0.0.1,[::1] | Accepted `Host`/`Origin` names |
| `console.session-seconds` | 28800 | Console session lifetime |
| `workflow.configurator.manager` | WorkflowManager | Where workflows are stored |
| `workflow.configurator.local-cloud-configurator`, `.solc-configurator`, `.solc.enabled` | LocalCloudConfigurator, SoLocalCloudConfigurator, true | Sources of configured local clouds |
| `workflow.configurator.hidden-services` | sysMonitor,generalManagement | Left out of the palette |

### WorkflowExecutor

| Property | Default | Meaning |
| --- | --- | --- |
| `spring.application.name` | WorkflowExecutor | System name (identity) |
| `server.port` | 8483 | |
| `workflow.executor.capabilities` | — | cap(w), comma-separated |
| `workflow.executor.services` | `*` | Service definitions this executor runs; kebab- and camelCase match (`temperature-read` = `temperatureRead`). Must be concrete (not `*`) for Choreographer registration |
| `workflow.executor.workstation` | — | |
| `workflow.executor.mode` | `push` | `pull` claims work items every `workflow.executor.poll-ms` |
| `workflow.manager.system-name` | WorkflowManager | Only this system (or a sysop) may call start/abort |
| `workflow.choreographer.enabled` | false | Register with the Choreographer as executor of the listed services |
| `workflow.executor.orchestrator` | DynamicServiceOrchestration | Orchestrator asked for step providers (a cloud may also offer `serviceOrchestration` from SimpleStore) |
| `workflow.executor.unrestricted-discovery` | true | Offer `workflowExecution` with `unrestrictedDiscovery` |
| `service.authorization.enabled` | false (`true` in `deployment/`) | Provider-side ConsumerAuthorization check |
| `workflow.manager.url`, `workflow.executor.static-providers`, `workflow.standalone` | — | Development only (no ServiceRegistry/DSO) |

## Services (wire contract 1.0.0)

The paths and operation names are in `workflow-model/.../wire/WorkflowServices.java`, and the payloads in the same package.

| System | Service | Operations |
| --- | --- | --- |
| WorkflowManager | `workflowRecipe` | `validate`, `register`, `query`, `get`, `retire`, `compile` under `/workflowmanager/recipe` |
| | `workflowInstance` | `start`, `abort` (owner or sysop), `query`, `get-status`, `get-trace` under `/workflowmanager/instance` |
| | `workflowWorkItem` | `claim` (pull; 204 when nothing fits), `report` (holder only) under `/workflowmanager/work-item` |
| | `workflowContext` | `get-parameters`, `get-results` (ξ restricted to dep(s); 409 when missing), `put-result` under `/workflowmanager/context` |
| | `workflowLease` | `reserve`, `poll`, `renew`, `release` under `/workflowmanager/lease` |
| | `workflowManagement` | `query-worklog`, `query-executors`, `query-work-items` under `/workflowmanager/mgmt` |
| WorkflowExecutor | `workflowExecution` | `service-info`, `start`, `abort`, `get-status` under `/workflowexecutor/execution` |
| | Choreographer interface (not registered) | `service-info`, `start`, `abort` under `/workflowexecutor/choreographer` |
| WorkflowConfigurator | `workflowConfiguration` | `list-clouds`, `list-services`, `list-executors`, `list-recipes`, `get-recipe`, `validate-recipe`, `store-recipe` under `/workflowconfigurator/api` |
| all | `generalManagement`, `sysMonitor` | as in the other 5.x systems |

Report codes from executor to manager:

| Code | Model rule |
| --- | --- |
| `BOUND` | S-Bind |
| `SUCCESS` | S-Done |
| `BIND_FAILED` | Next executor; S-FailBind when none is left |
| `FATAL_ERROR` | S-FailInvoke |
| `ERROR` | S-FailInvoke, or x-rebind when the step has a retry policy |
| `ABORTED` | Acknowledgement |

Equipment errors map as follows: HTTP 4xx is FATAL_ERROR; 5xx, 408, 423, 429 and connection failures are ERROR.
