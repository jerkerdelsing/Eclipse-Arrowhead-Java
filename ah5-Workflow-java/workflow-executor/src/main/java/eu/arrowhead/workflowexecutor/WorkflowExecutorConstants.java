/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor;

public final class WorkflowExecutorConstants {

	/** cap(w): capability descriptions this executor satisfies (η(s) ∈ cap(w)). */
	public static final String CAPABILITIES = "workflow.executor.capabilities";
	public static final String $CAPABILITIES_WD = "${" + CAPABILITIES + ":}";
	/** Service definitions θ(s) this executor can run; '*' for any. */
	public static final String SERVICES = "workflow.executor.services";
	public static final String $SERVICES_WD = "${" + SERVICES + ":*}";
	public static final String WORKSTATION = "workflow.executor.workstation";
	public static final String $WORKSTATION_WD = "${" + WORKSTATION + ":}";
	/** push (the WorkflowManager calls start) or pull (this executor claims work items). */
	public static final String MODE = "workflow.executor.mode";
	public static final String $MODE_WD = "${" + MODE + ":push}";
	/** Mark workflowExecution unrestrictedDiscovery, so a WorkflowManager outside service.discovery.direct.access finds it under the RESTRICTED discovery policy. */
	/** The orchestrator asked for step providers. A cloud may offer serviceOrchestration from several systems (e.g. SimpleStore),
	 *  and only DynamicServiceOrchestration matches by service requirement. Empty = whichever the ServiceRegistry returns. */
	public static final String ORCHESTRATOR = "workflow.executor.orchestrator";
	public static final String $ORCHESTRATOR_WD = "${" + ORCHESTRATOR + ":DynamicServiceOrchestration}";
	public static final String UNRESTRICTED_DISCOVERY = "workflow.executor.unrestricted-discovery";
	public static final String $UNRESTRICTED_DISCOVERY_WD = "${" + UNRESTRICTED_DISCOVERY + ":true}";
	public static final String POLL_MS = "workflow.executor.poll-ms";
	public static final String $POLL_MS_WD = "${" + POLL_MS + ":500}";
	public static final String THREADS = "workflow.executor.threads";
	public static final String $THREADS_WD = "${" + THREADS + ":8}";
	public static final String LEASE_POLL_MS = "workflow.executor.lease-poll-ms";
	public static final String $LEASE_POLL_MS_WD = "${" + LEASE_POLL_MS + ":200}";
	/** System name of the WorkflowManager allowed to call start/abort. */
	public static final String MANAGER_SYSTEM = "workflow.manager.system-name";
	public static final String $MANAGER_SYSTEM_WD = "${" + MANAGER_SYSTEM + ":WorkflowManager}";
	/** Development only: WorkflowManager base URL (scheme://host:port) instead of ServiceRegistry lookup. */
	public static final String MANAGER_URL = "workflow.manager.url";
	public static final String $MANAGER_URL_WD = "${" + MANAGER_URL + ":}";
	/** Development only: providers by service definition, ';'-separated: serviceDefinition|scheme://host:port/basePath|op=METHOD:/path,op=METHOD:/path */
	public static final String STATIC_PROVIDERS = "workflow.executor.static-providers";
	public static final String $STATIC_PROVIDERS_WD = "${" + STATIC_PROVIDERS + ":}";
	public static final String STANDALONE = "workflow.standalone";
	public static final String $STANDALONE_WD = "${" + STANDALONE + ":false}";

	/** Choreographer compatibility (option A): register with the Choreographer as executor of these services. */
	public static final String CHOREOGRAPHER_ENABLED = "workflow.choreographer.enabled";
	public static final String $CHOREOGRAPHER_ENABLED_WD = "${" + CHOREOGRAPHER_ENABLED + ":false}";
	public static final String CHOREOGRAPHER_SYSTEM = "workflow.choreographer.system-name";
	public static final String $CHOREOGRAPHER_SYSTEM_WD = "${" + CHOREOGRAPHER_SYSTEM + ":Choreographer}";
	public static final String CHOREOGRAPHER_MIN_VERSION = "workflow.choreographer.min-version";
	public static final String $CHOREOGRAPHER_MIN_VERSION_WD = "${" + CHOREOGRAPHER_MIN_VERSION + ":1}";
	public static final String CHOREOGRAPHER_MAX_VERSION = "workflow.choreographer.max-version";
	public static final String $CHOREOGRAPHER_MAX_VERSION_WD = "${" + CHOREOGRAPHER_MAX_VERSION + ":1}";

	public static final String WORKER = "executorWorker";

	private WorkflowExecutorConstants() {
		throw new UnsupportedOperationException();
	}
}
