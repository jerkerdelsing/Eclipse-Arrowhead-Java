# Deploying SoLocalCloudConfigurator

`compose.yaml` runs the system, which keeps its data in its own embedded database (`ah_solocalcloudconfigurator` in the
volume `/data`, schema applied at start). Like the other support systems it joins the cloud network and
needs, from the cloud:

- certificate `SoLocalCloudConfigurator.<Cloud>.<Org>.arrowhead.eu` (SAN `solocalcloudconfigurator`) as
  `SoLocalCloudConfigurator.p12`, the cloud trust store and the cloud CRL directory;
- an Authentication identity `SoLocalCloudConfigurator` (password secret `identity-password`), store and database secrets;
- ConsumerAuthorization policies (applied as Sysop):
  - provider SoLocalCloudConfigurator, service `soLocalCloudConfiguration`: default Sysop, every operation of the SoLC
    view scoped to LocalCloudManager (test cloud: `scripts/configure-solocalcloudconfigurator-policy.sh`);
  - provider LocalCloudConfigurator, service `localCloudConfiguration`: `catalog`, `list-configurations`,
    `get-configuration` also for SoLocalCloudConfigurator, nothing else (`configure-localcloudconfigurator-policy.sh`);
  - SoLocalCloudConfigurator may call `authorization`/`verify` and its orchestration dependencies (`VERIFIERS` of
    `configure-orchestration-dependency-policies.sh`);
- in LocalCloudManager: `SO_LOCAL_CLOUD_CONFIGURATION_ENABLED=true` (shows the SoLC view);
- `SOLC_SOURCE_COMMIT`: the commit of this repository that packages pin (build-on-target and the host assembler), and
  `SOLC_SOURCE_REPOSITORY` where hosts fetch it.

Port 8496 is published on the host loopback interface only. The container has no Docker socket, no capabilities, a
read-only root filesystem and runs as UID 10001.

The v5.3.0 test cloud (`Arrowhead-test-clouds/Arrowhead-test-cloud-java/generator`) deploys it with `generate.py`,
`prepare.py`, `build.py` and `deploy.sh`; `scripts/smoke-security.sh` checks its grants (section "SoLC view").

## Organisation policy

`catalogs/solc-policy.json` (mounted read-only in the image at `/app/catalogs`; override with `solc.catalog.file`) names
the organisation, the systems that are never inter-cloud consumers (Sysop, core, operator, inter-cloud and configurator
systems), the components a federated member needs (Gatekeeper, GateTunnel) and, per LocalCloudConfigurator catalog
component, the inter-cloud consumers it may offer and the services it may export. Management services can never be
exported. Changing it changes what the SoLC view offers and what plans allow.

## Deployment packages

See the package's own README and `docs/` (sources in `kit/docs/`). Packages are built in the SoLC view or with
`POST /api/packages {designId, expectedRevision}`; they are completed on a machine with Docker:

```sh
kit/bin/assemble-package.sh solc-<name>-r<rev>.tar.gz --source <Eclipse-Arrowhead-java checkout> --arch amd64,arm64
```

`kit/cloud` is regenerated from the reference cloud with `tools/make-kit.py`, then checked with
`kit/bin/selftest.sh --module`.

## Data

The system keeps its data in its own embedded database (H2 in `/data`, the project-owned volume `state`). No database container, network or port exists, and no other container may mount that volume: the data is reachable only through the system's services. `DB_PASSWORD_FILE` opens the database file; do not change it after the first start. The schema (`src/main/resources/database/schema.sql`) is applied by the system itself. Back up the volume while the system is stopped.
