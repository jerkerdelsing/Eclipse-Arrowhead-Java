#!/bin/sh
# End-to-end smoke test of WorkflowManager + three WorkflowExecutors + simulated plant on an isolated Docker network.
# Prerequisite: the jars are built (mvn -B install in ah-Workflow-java, or the Maven Docker image).
# Uses the development security profile and static wiring (workflow.standalone=true); never expose these containers.
set -eu
cd "$(dirname "$0")"
STATIC_PROVIDERS=""
for s in temperature-read temperature-evaluate heating-switch reserve-tool get-program transport-tool configure-tool \
         tighten-fasteners evaluate-tightening record-tightening release-tool; do
  STATIC_PROVIDERS="${STATIC_PROVIDERS}${s}|http://equipment:9000/${s}|run=POST:/run;"
done
export STATIC_PROVIDERS
docker compose up -d
trap 'docker compose down -v >/dev/null 2>&1' EXIT
i=0
until docker compose logs wfm 2>/dev/null | grep -q "Started WorkflowManagerMain" && docker compose logs wfe-pull 2>/dev/null | grep -q "Started WorkflowExecutorMain"; do
  i=$((i + 1)); [ "$i" -gt 60 ] && { echo "systems did not start" >&2; docker compose logs wfm | tail -40; exit 1; }
  sleep 3
done
python3 -I drive.py ../../examples
