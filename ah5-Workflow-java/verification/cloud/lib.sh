# Shared settings of the cloud test (POSIX sh; source it). Nothing here writes into the cloud folder.
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLOUD_DIR=${CLOUD_DIR:-$(CDPATH= cd -- "$HERE/../../../../Arrowhead-test-clouds/Arrowhead-test-cloud-java" && pwd)}
CLOUD_NAME=${ARROWHEAD_CLOUD_NAME:-TestCloud}
ORGANIZATION=${ORGANIZATION:-Company}
CLOUD_NETWORK=${CLOUD_NETWORK:-arrowhead-v53-test-cloud-network}
STATE="$HERE/.state"
CLOUD_CERTS="$CLOUD_DIR/security/x509"
export CLOUD_DIR

# test systems: name|DNS name in the cloud network
SYSTEMS="WorkflowManager|workflowmanager WfeAgv|wfe-agv WfeA|wfe-a WfePull|wfe-pull WfeChor|wfe-chor WorkflowConfigurator|workflowconfigurator WfEquipment|wf-equipment WfClient|wf-client WfOperator|wf-operator WfIntruder|wf-intruder"
EXECUTORS='["WfeAgv","WfeA","WfePull","WfeChor"]'
# WfeChor also registers with the cloud's Choreographer (compatibility profile); the Choreographer drives it with a
# compiled plan, and the WorkflowManager may dispatch to it as well
CHOREOGRAPHER_EXECUTORS='["WfeChor"]'
# plant services of WfEquipment, camelCase as the ServiceRegistry stores them (the recipes name them in kebab-case)
EQUIPMENT_SERVICES="temperatureRead temperatureEvaluate heatingSwitch reserveTool getProgram transportTool configureTool tightenFasteners evaluateTightening recordTightening releaseTool"
export EQUIPMENT_SERVICES

sysop_password() { SECRETS_ENV_FILE="$CLOUD_DIR/.env" sh -c ". '$CLOUD_DIR/scripts/lib-secrets.sh'; secret ARROWHEAD_SYSOP_PASSWORD"; }
# the cloud's declared authorization.verify list: teardown restores exactly this
cloud_verifiers() { sed -n "s/^VERIFIERS='\(.*\)'$/\1/p" "$CLOUD_DIR/scripts/configure-orchestration-dependency-policies.sh"; }

# in_cloud <python args...>: runs a stdlib Python script on the cloud network (the host ports of ServiceRegistry,
# Authentication and ConsumerAuthorization may not forward TLS on Docker Desktop; the cloud network always works)
in_cloud() {
  mkdir -p "$STATE/backup"
  docker run --rm --network "$CLOUD_NETWORK" \
    -e SYSOP_PASSWORD -e VERIFIERS="$(cloud_verifiers)" -e SYSTEMS="$SYSTEMS" -e EXECUTORS="$EXECUTORS" -e CHOREOGRAPHER_EXECUTORS="$CHOREOGRAPHER_EXECUTORS" -e EQUIPMENT_SERVICES \
    -v "$HERE:/h:ro" -v "$STATE:/state:ro" -v "$STATE/backup:/backup" -v "$HERE/../../examples:/examples:ro" \
    -v "$CLOUD_CERTS/ca.crt:/cloud-certs/ca.crt:ro" -v "$CLOUD_CERTS/sysop.crt:/cloud-certs/sysop.crt:ro" -v "$CLOUD_CERTS/sysop.key:/cloud-certs/sysop.key:ro" \
    python:3.13-alpine python -I "$@"
}
