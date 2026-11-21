/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

/** Service definitions, operations and HTTP paths of the WorkflowManager and WorkflowExecutor (wire contract 1.0.0). */
public final class WorkflowServices {

	public static final String VERSION = "1.0.0";

	public static final String SYS_WORKFLOW_MANAGER = "WorkflowManager";
	public static final String SYS_WORKFLOW_EXECUTOR = "WorkflowExecutor";

	// ---------------------------------------------------------------- WorkflowManager
	public static final String WFM_BASE = "/workflowmanager";

	public static final String SD_RECIPE = "workflowRecipe";
	public static final String RECIPE_PATH = WFM_BASE + "/recipe";
	public static final String OP_VALIDATE = "validate";
	public static final String OP_REGISTER = "register";
	public static final String OP_QUERY = "query";
	public static final String OP_GET = "get";
	public static final String OP_RETIRE = "retire";
	public static final String OP_COMPILE = "compile";
	public static final String RECIPE_VALIDATE_PATH = "/validate";
	public static final String RECIPE_REGISTER_PATH = "/register";
	public static final String RECIPE_QUERY_PATH = "/recipes";
	public static final String RECIPE_ITEM_PATH = "/recipes/{name}/{version}";
	public static final String RECIPE_COMPILE_PATH = "/compile";

	public static final String SD_INSTANCE = "workflowInstance";
	public static final String INSTANCE_PATH = WFM_BASE + "/instance";
	public static final String OP_START = "start";
	public static final String OP_ABORT = "abort";
	public static final String OP_GET_STATUS = "get-status";
	public static final String OP_GET_TRACE = "get-trace";
	public static final String INSTANCE_START_PATH = "/start";
	public static final String INSTANCE_ABORT_PATH = "/abort/{id}";
	public static final String INSTANCE_QUERY_PATH = "/sessions";
	public static final String INSTANCE_ITEM_PATH = "/sessions/{id}";
	public static final String INSTANCE_TRACE_PATH = "/sessions/{id}/trace";

	public static final String SD_WORK_ITEM = "workflowWorkItem";
	public static final String WORK_ITEM_PATH = WFM_BASE + "/work-item";
	public static final String OP_CLAIM = "claim";
	public static final String OP_REPORT = "report";
	public static final String WORK_ITEM_CLAIM_PATH = "/claim";
	public static final String WORK_ITEM_REPORT_PATH = "/report";

	public static final String SD_CONTEXT = "workflowContext";
	public static final String CONTEXT_PATH = WFM_BASE + "/context";
	public static final String OP_GET_PARAMETERS = "get-parameters";
	public static final String OP_GET_RESULTS = "get-results";
	public static final String OP_PUT_RESULT = "put-result";
	public static final String CONTEXT_PARAMETERS_PATH = "/parameters/{workItemId}";
	public static final String CONTEXT_RESULTS_PATH = "/results/{workItemId}";
	public static final String CONTEXT_PUT_RESULT_PATH = "/result";

	public static final String SD_LEASE = "workflowLease";
	public static final String LEASE_PATH = WFM_BASE + "/lease";
	public static final String OP_RESERVE = "reserve";
	public static final String OP_POLL = "poll";
	public static final String OP_RENEW = "renew";
	public static final String OP_RELEASE = "release";
	public static final String LEASE_RESERVE_PATH = "/reserve";
	public static final String LEASE_POLL_PATH = "/poll";
	public static final String LEASE_RENEW_PATH = "/renew";
	public static final String LEASE_RELEASE_PATH = "/release";

	public static final String SD_WFM_MANAGEMENT = "workflowManagement";
	public static final String WFM_MANAGEMENT_PATH = WFM_BASE + "/mgmt";
	public static final String OP_QUERY_WORKLOG = "query-worklog";
	public static final String OP_QUERY_EXECUTORS = "query-executors";
	public static final String OP_QUERY_WORK_ITEMS = "query-work-items";
	public static final String MGMT_WORKLOG_PATH = "/worklog";
	public static final String MGMT_EXECUTORS_PATH = "/executors";
	public static final String MGMT_WORK_ITEMS_PATH = "/work-items";

	public static final String WFM_GENERAL_MANAGEMENT_PATH = WFM_BASE + "/general/mgmt";

	// ---------------------------------------------------------------- WorkflowExecutor
	public static final String WFE_BASE = "/workflowexecutor";

	public static final String SD_EXECUTION = "workflowExecution";
	public static final String EXECUTION_PATH = WFE_BASE + "/execution";
	public static final String OP_SERVICE_INFO = "service-info";
	public static final String EXECUTION_SERVICE_INFO_PATH = "/service-info";
	public static final String EXECUTION_START_PATH = "/start";
	public static final String EXECUTION_ABORT_PATH = "/abort";
	public static final String EXECUTION_STATUS_PATH = "/status/{workItemId}";

	/** Choreographer executor interface (v4.6/5.x): service-info, start and abort under this base path. Not a registered service. */
	public static final String CHOREOGRAPHER_COMPAT_PATH = WFE_BASE + "/choreographer";

	public static final String WFE_GENERAL_MANAGEMENT_PATH = WFE_BASE + "/general/mgmt";

	/** ServiceRegistry metadata of workflowExecution, used by the WorkflowManager to select executors. */
	public static final String METADATA_CAPABILITIES = "workflowCapabilities";
	public static final String METADATA_SERVICES = "workflowServices";
	public static final String METADATA_WORKSTATION = "workflowWorkstation";
	public static final String METADATA_MODE = "workflowMode";
	public static final String ANY_SERVICE = "*";

	/**
	 * Whether a declared service list covers a service definition. Arrowhead stores service definitions in camelCase
	 * ({@code temperatureRead}) while recipes and configuration may use kebab- or snake-case ({@code temperature-read}), so
	 * names are compared case-insensitively without separators. An empty list or {@link #ANY_SERVICE} covers every service.
	 */
	public static boolean covers(final java.util.Collection<String> services, final String serviceDefinition) {
		if (services == null || services.isEmpty() || services.contains(ANY_SERVICE)) {
			return true;
		}
		final String wanted = canonicalServiceName(serviceDefinition);
		return services.stream().anyMatch(s -> canonicalServiceName(s).equals(wanted));
	}

	/** Lower case without '-', '_' and spaces: {@code temperature-read}, {@code temperature_read} and {@code temperatureRead} coincide. */
	public static String canonicalServiceName(final String name) {
		return name == null ? "" : name.replaceAll("[-_\\s]", "").toLowerCase(java.util.Locale.ROOT);
	}

	// ---------------------------------------------------------------- Intelligent Product (consumed by the WorkflowManager)
	public static final String SD_PRODUCT = "workflowProduct";
	public static final String OP_RETRIEVE_PROCESS_STEP = "retrieve-process-step";
	public static final String OP_STORE_PROCESS_STEP_RESULT = "store-process-step-result";

	/** A step whose service is workflowLease is executed by the executor against the WorkflowManager lease service only. */
	public static final String INTERNAL_LEASE_SERVICE = SD_LEASE;

	private WorkflowServices() {
		throw new UnsupportedOperationException();
	}
}
