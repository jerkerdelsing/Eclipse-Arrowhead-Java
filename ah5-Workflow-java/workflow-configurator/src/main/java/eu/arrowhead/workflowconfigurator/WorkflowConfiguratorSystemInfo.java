/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator;

import static eu.arrowhead.workflowconfigurator.WorkflowConfiguratorConstants.*;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.SystemInfo;
import eu.arrowhead.common.http.filter.authentication.AuthenticationPolicy;
import eu.arrowhead.common.http.model.HttpInterfaceModel;
import eu.arrowhead.common.http.model.HttpOperationModel;
import eu.arrowhead.common.model.ServiceModel;
import eu.arrowhead.common.model.SystemModel;

/**
 * Produces workflowConfiguration (the operations the graphical editor uses, also for other engineering tools) and
 * generalManagement. The browser console reaches the same operations on the loopback console connector.
 */
@Component(Constants.BEAN_NAME_SYSTEM_INFO)
public class WorkflowConfiguratorSystemInfo extends SystemInfo {

	private SystemModel systemModel;

	@Override
	public String getSystemName() {
		return SYS_NAME;
	}

	@Override
	public SystemModel getSystemModel() {
		if (systemModel == null) {
			SystemModel.Builder builder = new SystemModel.Builder().address(getAddress()).version(Constants.AH_FRAMEWORK_VERSION);
			if (AuthenticationPolicy.CERTIFICATE == getAuthenticationPolicy()) {
				builder = builder.metadata(Constants.METADATA_KEY_X509_PUBLIC_KEY, getPublicKey());
			}
			systemModel = builder.build();
		}
		return systemModel;
	}

	@Override
	public List<ServiceModel> getServices() {
		final ServiceModel configuration = new ServiceModel.Builder()
				.serviceDefinition(SD_CONFIGURATION)
				.version(VERSION)
				.serviceInterface(interfaceBuilder(API_PATH)
						.operation(OP_LIST_CLOUDS, op(HttpMethod.GET, CLOUDS_PATH))
						.operation(OP_LIST_SERVICES, op(HttpMethod.GET, CLOUD_SERVICES_PATH))
						.operation(OP_LIST_EXECUTORS, op(HttpMethod.GET, EXECUTORS_PATH))
						.operation(OP_LIST_RECIPES, op(HttpMethod.GET, RECIPES_PATH))
						.operation(OP_GET_RECIPE, op(HttpMethod.GET, RECIPE_ITEM_PATH))
						.operation(OP_VALIDATE_RECIPE, op(HttpMethod.POST, RECIPE_VALIDATE_PATH))
						.operation(OP_STORE_RECIPE, op(HttpMethod.POST, RECIPES_PATH))
						.build())
				.build();
		final ServiceModel generalManagement = new ServiceModel.Builder()
				.serviceDefinition(Constants.SERVICE_DEF_GENERAL_MANAGEMENT)
				.version(VERSION)
				.serviceInterface(interfaceBuilder(GENERAL_MANAGEMENT_PATH)
						.operation(Constants.SERVICE_OP_GET_LOG, op(HttpMethod.POST, Constants.HTTP_API_OP_LOGS_PATH))
						.operation(Constants.SERVICE_OP_GET_CONFIG, op(HttpMethod.GET, Constants.HTTP_API_OP_GET_CONFIG_PATH))
						.build())
				.build();
		return List.of(generalManagement, configuration);
	}

	@Override
	protected PublicConfigurationKeysAndDefaults getPublicConfigurationKeysAndDefaults() {
		return new PublicConfigurationKeysAndDefaults(
				Set.of(Constants.SERVER_ADDRESS,
						Constants.SERVER_PORT,
						Constants.DOMAIN_NAME,
						Constants.AUTHENTICATION_POLICY,
						Constants.ENABLE_MANAGEMENT_FILTER,
						Constants.MANAGEMENT_POLICY,
						Constants.NORMALIZATION_MODE,
						CONSOLE_PORT,
						MANAGER_SYSTEM,
						LCC_SYSTEM,
						SOLC_SYSTEM,
						SOLC_ENABLED,
						SERVICE_AUTHORIZATION_ENABLED),
				WorkflowConfiguratorDefaults.class);
	}

	private HttpInterfaceModel.Builder interfaceBuilder(final String basePath) {
		final String templateName = getSslProperties().isSslEnabled() ? Constants.GENERIC_HTTPS_INTERFACE_TEMPLATE_NAME : Constants.GENERIC_HTTP_INTERFACE_TEMPLATE_NAME;
		return new HttpInterfaceModel.Builder(templateName, getDomainAddress(), getServerPort()).basePath(basePath);
	}

	private static HttpOperationModel op(final HttpMethod method, final String path) {
		return new HttpOperationModel.Builder().method(method.name()).path(path).build();
	}
}
