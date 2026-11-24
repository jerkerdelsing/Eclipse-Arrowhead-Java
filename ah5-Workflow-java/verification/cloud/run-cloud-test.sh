#!/bin/sh
# Cloud test of WorkflowManager + four WorkflowExecutors + WorkflowConfigurator + a mutual-TLS plant in the running Java
# test cloud, one of the executors also driven by the cloud's Choreographer:
# setup.sh, then the systems in their own compose project on the cloud network, then drive.py, then teardown.sh
# (KEEP=1 leaves everything running; run teardown.sh yourself afterwards).
# Prerequisite: the jars are built (mvn -B install in ah5-Workflow-java, or the Maven Docker image).
set -eu
. "$(dirname -- "$0")/lib.sh"
for jar in "$HERE/../../workflow-manager/target/arrowhead-workflowmanager-5.4.0.jar" "$HERE/../../workflow-executor/target/arrowhead-workflowexecutor-5.4.0.jar" \
    "$HERE/../../workflow-configurator/target/arrowhead-workflowconfigurator-5.4.0.jar"; do
  [ -f "$jar" ] || { echo "missing $jar: build first" >&2; exit 1; }
done
"$HERE/setup.sh"
[ "${KEEP:-0}" = 1 ] || trap '"$HERE/teardown.sh"' EXIT
docker compose -f "$HERE/compose.yaml" up -d
i=0
until docker compose -f "$HERE/compose.yaml" logs workflowmanager 2>/dev/null | grep -q "Started WorkflowManagerMain" \
   && docker compose -f "$HERE/compose.yaml" logs workflowconfigurator 2>/dev/null | grep -q "Started WorkflowConfiguratorMain" \
   && [ "$(docker compose -f "$HERE/compose.yaml" logs wfe-agv wfe-a wfe-pull wfe-chor 2>/dev/null | grep -c "Started WorkflowExecutorMain")" -ge 4 ]; do
  i=$((i + 1))
  if [ "$i" -gt 60 ] || docker compose -f "$HERE/compose.yaml" ps -a --format '{{.Service}} {{.State}}' | grep -q ' exited'; then
    echo "systems did not start" >&2; docker compose -f "$HERE/compose.yaml" ps -a; docker compose -f "$HERE/compose.yaml" logs --tail 60 workflowmanager wfe-a >&2; exit 1
  fi
  sleep 3
done
SYSOP_PASSWORD=$(sysop_password); export SYSOP_PASSWORD
in_cloud /h/drive.py
# choreographerExecutor.unregister on shutdown
docker compose -f "$HERE/compose.yaml" stop wfe-chor >/dev/null
in_cloud /h/cloudctl.py choreographer-executors
