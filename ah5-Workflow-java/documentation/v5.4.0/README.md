# Workflow documentation 5.4.0

Arrowhead-style LaTeX documentation of the workflow support systems in `ah5-Workflow-java`, written from the Java reference implementation on 2026-11-23 and revised on 2026-11-24 for the embedded WorkflowManager database and the retry extension and on 2026-11-25 for the sysMonitor observations of lost executors (status: review draft). The document class, bibliography style and logo come from `roadmap/Documentation_templates/LaTeX`, and every document follows the section structure of the matching template.

| Level | Documents |
| --- | --- |
| System of systems | `Workflow-v5.4.0-SoSD` (black box), `Workflow-v5.4.0-SoSDD` (white box: service map, recipe and execution model, interaction sequences, deployment, grants, status) |
| Systems | `WorkflowManager`, `WorkflowExecutor`, `WorkflowConfigurator`: `-SysD` and `-SysDD` each |
| Services of the WorkflowManager | `workflowRecipe`, `workflowInstance`, `workflowWorkItem`, `workflowContext`, `workflowLease`, `workflowManagement`: `-SD` and `-HTTP-IDD` each |
| Monitoring data of the WorkflowManager | `sysMonitor` (`executorLiveness`, `workItemLost`): `-SD` and `-AMQP-IDD` |
| Service of the WorkflowExecutor | `workflowExecution`: `-SD` and `-HTTP-IDD` |
| Service of the WorkflowConfigurator | `workflowConfiguration`: `-SD` and `-HTTP-IDD` |
| Shared and consumed contracts | `Workflow-v5.4.0-generalManagement` (all three systems) and `Workflow-v5.4.0-workflowProduct` (provided by an Intelligent Product, consumed by the WorkflowManager): `-SD` and `-HTTP-IDD` each |

That makes 30 documents. Of sysMonitor only the WorkflowManager's own observation types are documented here: `echo`, `power` and `status` come from the common-library publisher, which other systems' documentation already describes.

## Build

```sh
python3 generator/generate.py   # only when a service contract or text changed
./build-docs.sh                 # builds every PDF; fails on undefined references or citations
```

The `.tex` files are generated from `generator/` (`model.py` holds the data models, `render_monitor.py` the sysMonitor documents, `services.py` the operations, `render_systems.py` and `render_sos.py` the system and SoS texts). To change a document, edit the generator and regenerate: hand edits to a `.tex` file are lost at the next generation. The second and third rows of a revision history come from `AMENDMENTS` and `AMENDMENTS3` in `generate.py`; documents not listed there are unchanged since the first edition.

## Sources and gaps

- Paths, methods, operations and status codes come from `workflow-model` (`wire/WorkflowServices.java`, `wire/ServiceOperations.java`), the API controllers, `WorkflowEngine`, `RecipeService` and `LeaseService`, and the WorkflowConfigurator's `ConfigurationAPI` and `ConfiguratorOperations`. The data models come from the records in `workflow-model` and `workflow-manager/api/dto`.
- The formal model follows Varga, *Sensors* 2026, 26, 7025. The design rationale follows ../../ARCHITECTURE.md.
- **Go and Rust:** no implementation exists. Every IDD, SysDD and the SoSDD states this; the workflow systems are not yet interoperable across the three implementations.
- `workflowProduct` is a proposed contract. Only the WorkflowManager's consumer side exists.
- **Cloud test:** the 49/49 result is from 2026-11-21, before the database was embedded; it has not been repeated.
