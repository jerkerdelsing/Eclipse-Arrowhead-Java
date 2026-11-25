#!/bin/sh
# Undoes the cloud test: stops and removes the test containers and the database volume, removes the identities,
# sessions, policies and ServiceRegistry entries of the test systems and restores authorization.verify to the cloud's
# declared list. Certificates and passwords stay in .state/ (30-day test certificates of the cloud CA); remove the folder
# to drop them, or revoke them with the cloud's scripts/revoke-certificate.sh first.
set -eu
. "$(dirname -- "$0")/lib.sh"
docker compose -f "$HERE/compose.yaml" down -v --remove-orphans
SYSOP_PASSWORD=$(sysop_password); export SYSOP_PASSWORD
in_cloud /h/cloudctl.py teardown
