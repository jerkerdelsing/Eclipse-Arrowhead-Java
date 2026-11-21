/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator;

/** Names, paths and property keys of the WorkflowConfigurator. */
public final class WorkflowConfiguratorConstants {

	public static final String SYS_NAME = "WorkflowConfigurator";
	public static final String VERSION = "1.0.0";

	// ---------------------------------------------------------------- produced service
	public static final String SD_CONFIGURATION = "workflowConfiguration";
	public static final String BASE_PATH = "/workflowconfigurator";
	public static final String API_PATH = BASE_PATH + "/api";
	public static final String GENERAL_MANAGEMENT_PATH = BASE_PATH + "/general/mgmt";
	public static final String OP_LIST_CLOUDS = "list-clouds";
	public static final String OP_LIST_SERVICES = "list-services";
	public static final String OP_LIST_EXECUTORS = "list-executors";
	public static final String OP_LIST_RECIPES = "list-recipes";
	public static final String OP_GET_RECIPE = "get-recipe";
	public static final String OP_VALIDATE_RECIPE = "validate-recipe";
	public static final String OP_STORE_RECIPE = "store-recipe";
	public static final String CLOUDS_PATH = "/clouds";
	public static final String CLOUD_SERVICES_PATH = "/clouds/{id}/services";
	public static final String EXECUTORS_PATH = "/executors";
	public static final String RECIPES_PATH = "/recipes";
	public static final String RECIPE_ITEM_PATH = "/recipes/{name}/{version}";
	public static final String RECIPE_VALIDATE_PATH = "/recipes/validate";

	// ---------------------------------------------------------------- consumed services
	public static final String SD_LOCAL_CLOUD_CONFIGURATION = "localCloudConfiguration";
	public static final String SD_SOLC_CONFIGURATION = "soLocalCloudConfiguration";

	// ---------------------------------------------------------------- console (browser, plain HTTP on loopback)
	public static final String CONSOLE_SESSION_PATH = API_PATH + "/session";
	public static final String CONSOLE_COOKIE = "wfc_session";
	public static final String HTTP_ATTR_CONSOLE_OPERATOR = "workflowconfigurator.console.operator";

	// ---------------------------------------------------------------- properties
	public static final String CONSOLE_PORT = "console.port";
	public static final String $CONSOLE_PORT_WD = "${" + CONSOLE_PORT + ":8485}";
	public static final String CONSOLE_ADDRESS = "console.address";
	public static final String $CONSOLE_ADDRESS_WD = "${" + CONSOLE_ADDRESS + ":127.0.0.1}";
	public static final String CONSOLE_PASSWORD_HASH = "console.password-hash";
	public static final String $CONSOLE_PASSWORD_HASH_WD = "${" + CONSOLE_PASSWORD_HASH + ":}";
	public static final String CONSOLE_ALLOWED_HOSTS = "console.allowed-hosts";
	public static final String $CONSOLE_ALLOWED_HOSTS_WD = "${" + CONSOLE_ALLOWED_HOSTS + ":localhost,127.0.0.1,[::1]}";
	public static final String CONSOLE_SESSION_SECONDS = "console.session-seconds";
	public static final String $CONSOLE_SESSION_SECONDS_WD = "${" + CONSOLE_SESSION_SECONDS + ":28800}";

	public static final String MANAGER_SYSTEM = "workflow.configurator.manager";
	public static final String $MANAGER_SYSTEM_WD = "${" + MANAGER_SYSTEM + ":WorkflowManager}";
	public static final String LCC_SYSTEM = "workflow.configurator.local-cloud-configurator";
	public static final String $LCC_SYSTEM_WD = "${" + LCC_SYSTEM + ":LocalCloudConfigurator}";
	public static final String SOLC_SYSTEM = "workflow.configurator.solc-configurator";
	public static final String $SOLC_SYSTEM_WD = "${" + SOLC_SYSTEM + ":SoLocalCloudConfigurator}";
	public static final String SOLC_ENABLED = "workflow.configurator.solc.enabled";
	public static final String $SOLC_ENABLED_WD = "${" + SOLC_ENABLED + ":true}";
	public static final String HIDDEN_SERVICES = "workflow.configurator.hidden-services";
	public static final String $HIDDEN_SERVICES_WD = "${" + HIDDEN_SERVICES + ":sysMonitor,generalManagement}";
	public static final String STANDALONE = "workflow.standalone";
	public static final String $STANDALONE_WD = "${" + STANDALONE + ":false}";
	public static final String SERVICE_AUTHORIZATION_ENABLED = "service.authorization.enabled";
	public static final String $SERVICE_AUTHORIZATION_ENABLED_WD = "${" + SERVICE_AUTHORIZATION_ENABLED + ":false}";

	private WorkflowConfiguratorConstants() {
		throw new UnsupportedOperationException();
	}
}
