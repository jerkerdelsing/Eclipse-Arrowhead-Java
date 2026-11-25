/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager;

public final class WorkflowManagerConstants {

	public static final String DATABASE_ENTITY_PACKAGE = "eu.arrowhead.workflowmanager.jpa.entity";
	public static final String DATABASE_REPOSITORY_PACKAGE = "eu.arrowhead.workflowmanager.jpa.repository";

	// ---------------------------------------------------------------- properties
	/** Development only: run without ServiceRegistry (static executors, no registration). */
	public static final String STANDALONE = "workflow.standalone";
	public static final String $STANDALONE_WD = "${" + STANDALONE + ":false}";
	/** Static executors, separated by ';': name|scheme://host:port|capability,capability|service,service|workstation (with or without ServiceRegistry). */
	public static final String STATIC_EXECUTORS = "workflow.executors";
	public static final String $STATIC_EXECUTORS_WD = "${" + STATIC_EXECUTORS + ":}";
	public static final String DISCOVERY_CACHE_SECONDS = "workflow.executor.discovery.cache-seconds";
	public static final String $DISCOVERY_CACHE_SECONDS_WD = "${" + DISCOVERY_CACHE_SECONDS + ":" + WorkflowManagerDefaults.DISCOVERY_CACHE_SECONDS_DEFAULT + "}";
	public static final String DEFAULT_DISPATCH = "workflow.dispatch.default";
	public static final String $DEFAULT_DISPATCH_WD = "${" + DEFAULT_DISPATCH + ":" + WorkflowManagerDefaults.DEFAULT_DISPATCH_DEFAULT + "}";
	public static final String CLAIM_TIMEOUT = "workflow.pull.claim-timeout";
	public static final String $CLAIM_TIMEOUT_WD = "${" + CLAIM_TIMEOUT + ":" + WorkflowManagerDefaults.CLAIM_TIMEOUT_DEFAULT + "}";
	/** How often executors are asked (workflowExecution.get-status) whether they still run the work items they hold; 0 = never. */
	public static final String LIVENESS_CHECK_SECONDS = "workflow.executor.liveness.check-seconds";
	public static final String $LIVENESS_CHECK_SECONDS_WD = "${" + LIVENESS_CHECK_SECONDS + ":" + WorkflowManagerDefaults.LIVENESS_CHECK_SECONDS_DEFAULT + "}";
	/** How long an executor may be unreachable before the work items it holds count as lost. */
	public static final String LIVENESS_LOST_AFTER_SECONDS = "workflow.executor.liveness.lost-after-seconds";
	public static final String $LIVENESS_LOST_AFTER_SECONDS_WD = "${" + LIVENESS_LOST_AFTER_SECONDS + ":" + WorkflowManagerDefaults.LIVENESS_LOST_AFTER_SECONDS_DEFAULT + "}";
	public static final String WORKER_THREADS = "workflow.worker.threads";
	public static final String $WORKER_THREADS_WD = "${" + WORKER_THREADS + ":" + WorkflowManagerDefaults.WORKER_THREADS_DEFAULT + "}";
	public static final String LEASE_DEFAULT_DURATION = "workflow.lease.default-duration";
	public static final String $LEASE_DEFAULT_DURATION_WD = "${" + LEASE_DEFAULT_DURATION + ":" + WorkflowManagerDefaults.LEASE_DEFAULT_DURATION_DEFAULT + "}";
	/** Compensation extension: release a failed session's leases at once instead of letting them expire (§6.5). */
	public static final String LEASE_RELEASE_ON_FAILURE = "workflow.lease.release-on-failure";
	public static final String $LEASE_RELEASE_ON_FAILURE_WD = "${" + LEASE_RELEASE_ON_FAILURE + ":false}";
	public static final String WORKSTATION_ID = "workflow.workstation.id";
	public static final String $WORKSTATION_ID_WD = "${" + WORKSTATION_ID + ":}";
	public static final String CATALOGUE_CHECK = "workflow.validator.catalogue-check";
	public static final String $CATALOGUE_CHECK_WD = "${" + CATALOGUE_CHECK + ":true}";
	/** C01 (θ(s) registered). Needs a complete lookup: WorkflowManager in the ServiceRegistry's service.discovery.direct.access, or
	 *  the OPEN discovery policy. Under RESTRICTED discovery the lookup hides providers without unrestrictedDiscovery. */
	public static final String CATALOGUE_CHECK_SERVICES = "workflow.validator.catalogue-check.services";
	public static final String $CATALOGUE_CHECK_SERVICES_WD = "${" + CATALOGUE_CHECK_SERVICES + ":true}";

	public static final String WORKER = "workflowWorker";

	private WorkflowManagerConstants() {
		throw new UnsupportedOperationException();
	}
}
