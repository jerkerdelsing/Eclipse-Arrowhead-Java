/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

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

@Component(Constants.BEAN_NAME_SYSTEM_INFO)
public class WorkflowManagerSystemInfo extends SystemInfo {

	private SystemModel systemModel;

	@Override
	public String getSystemName() {
		return SYS_WORKFLOW_MANAGER;
	}

	@Override
	public SystemModel getSystemModel() {
		if (systemModel == null) {
			SystemModel.Builder builder = new SystemModel.Builder()
					.address(getAddress())
					.version(Constants.AH_FRAMEWORK_VERSION);
			if (AuthenticationPolicy.CERTIFICATE == getAuthenticationPolicy()) {
				builder = builder.metadata(Constants.METADATA_KEY_X509_PUBLIC_KEY, getPublicKey());
			}
			systemModel = builder.build();
		}
		return systemModel;
	}

	@Override
	public List<ServiceModel> getServices() {
		final ServiceModel recipe = service(SD_RECIPE, interfaceBuilder(RECIPE_PATH)
				.operation(OP_VALIDATE, op(HttpMethod.POST, RECIPE_VALIDATE_PATH))
				.operation(OP_REGISTER, op(HttpMethod.POST, RECIPE_REGISTER_PATH))
				.operation(OP_QUERY, op(HttpMethod.GET, RECIPE_QUERY_PATH))
				.operation(OP_GET, op(HttpMethod.GET, RECIPE_ITEM_PATH))
				.operation(OP_RETIRE, op(HttpMethod.DELETE, RECIPE_ITEM_PATH))
				.operation(OP_COMPILE, op(HttpMethod.POST, RECIPE_COMPILE_PATH)));
		final ServiceModel instance = service(SD_INSTANCE, interfaceBuilder(INSTANCE_PATH)
				.operation(OP_START, op(HttpMethod.POST, INSTANCE_START_PATH))
				.operation(OP_ABORT, op(HttpMethod.DELETE, INSTANCE_ABORT_PATH))
				.operation(OP_QUERY, op(HttpMethod.GET, INSTANCE_QUERY_PATH))
				.operation(OP_GET_STATUS, op(HttpMethod.GET, INSTANCE_ITEM_PATH))
				.operation(OP_GET_TRACE, op(HttpMethod.GET, INSTANCE_TRACE_PATH)));
		final ServiceModel workItem = service(SD_WORK_ITEM, interfaceBuilder(WORK_ITEM_PATH)
				.operation(OP_CLAIM, op(HttpMethod.POST, WORK_ITEM_CLAIM_PATH))
				.operation(OP_REPORT, op(HttpMethod.POST, WORK_ITEM_REPORT_PATH)));
		final ServiceModel context = service(SD_CONTEXT, interfaceBuilder(CONTEXT_PATH)
				.operation(OP_GET_PARAMETERS, op(HttpMethod.GET, CONTEXT_PARAMETERS_PATH))
				.operation(OP_GET_RESULTS, op(HttpMethod.GET, CONTEXT_RESULTS_PATH))
				.operation(OP_PUT_RESULT, op(HttpMethod.POST, CONTEXT_PUT_RESULT_PATH)));
		final ServiceModel lease = service(SD_LEASE, interfaceBuilder(LEASE_PATH)
				.operation(OP_RESERVE, op(HttpMethod.POST, LEASE_RESERVE_PATH))
				.operation(OP_POLL, op(HttpMethod.POST, LEASE_POLL_PATH))
				.operation(OP_RENEW, op(HttpMethod.POST, LEASE_RENEW_PATH))
				.operation(OP_RELEASE, op(HttpMethod.POST, LEASE_RELEASE_PATH)));
		final ServiceModel management = service(SD_WFM_MANAGEMENT, interfaceBuilder(WFM_MANAGEMENT_PATH)
				.operation(OP_QUERY_WORKLOG, op(HttpMethod.GET, MGMT_WORKLOG_PATH))
				.operation(OP_QUERY_EXECUTORS, op(HttpMethod.GET, MGMT_EXECUTORS_PATH))
				.operation(OP_QUERY_WORK_ITEMS, op(HttpMethod.GET, MGMT_WORK_ITEMS_PATH)));
		final ServiceModel generalManagement = service(Constants.SERVICE_DEF_GENERAL_MANAGEMENT, interfaceBuilder(WFM_GENERAL_MANAGEMENT_PATH)
				.operation(Constants.SERVICE_OP_GET_LOG, op(HttpMethod.POST, Constants.HTTP_API_OP_LOGS_PATH))
				.operation(Constants.SERVICE_OP_GET_CONFIG, op(HttpMethod.GET, Constants.HTTP_API_OP_GET_CONFIG_PATH)));

		// management services first: speeds up the management filter
		return List.of(generalManagement, management, recipe, instance, workItem, context, lease);
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
						Constants.MAX_PAGE_SIZE,
						Constants.NORMALIZATION_MODE,
						WorkflowManagerConstants.DEFAULT_DISPATCH,
						WorkflowManagerConstants.CLAIM_TIMEOUT,
						WorkflowManagerConstants.WORKER_THREADS,
						WorkflowManagerConstants.LEASE_DEFAULT_DURATION,
						WorkflowManagerConstants.LEASE_RELEASE_ON_FAILURE,
						WorkflowManagerConstants.WORKSTATION_ID),
				WorkflowManagerDefaults.class);
	}

	private static ServiceModel service(final String definition, final HttpInterfaceModel.Builder builder) {
		return new ServiceModel.Builder().serviceDefinition(definition).version(VERSION).serviceInterface(builder.build()).build();
	}

	private HttpInterfaceModel.Builder interfaceBuilder(final String basePath) {
		final String templateName = getSslProperties().isSslEnabled() ? Constants.GENERIC_HTTPS_INTERFACE_TEMPLATE_NAME : Constants.GENERIC_HTTP_INTERFACE_TEMPLATE_NAME;
		return new HttpInterfaceModel.Builder(templateName, getDomainAddress(), getServerPort()).basePath(basePath);
	}

	private static HttpOperationModel op(final HttpMethod method, final String path) {
		return new HttpOperationModel.Builder().method(method.name()).path(path).build();
	}
}
