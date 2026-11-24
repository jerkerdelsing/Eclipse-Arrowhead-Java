/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
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
public class WorkflowExecutorSystemInfo extends SystemInfo {

	@Value(WorkflowExecutorConstants.$CAPABILITIES_WD)
	private List<String> capabilities;

	@Value(WorkflowExecutorConstants.$SERVICES_WD)
	private List<String> services;

	@Value(WorkflowExecutorConstants.$WORKSTATION_WD)
	private String workstation;

	@Value(WorkflowExecutorConstants.$MODE_WD)
	private String mode;

	@Value(WorkflowExecutorConstants.$UNRESTRICTED_DISCOVERY_WD)
	private boolean unrestrictedDiscovery;

	@Value("${spring.application.name:" + SYS_WORKFLOW_EXECUTOR + "}")
	private String systemName;

	private SystemModel systemModel;

	@Override
	public String getSystemName() {
		return systemName;
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

	/** workflowExecution carries cap(w), the runnable services and the workstation as metadata for executor selection. */
	@Override
	public List<ServiceModel> getServices() {
		final ServiceModel.Builder execution = new ServiceModel.Builder()
				.serviceDefinition(SD_EXECUTION)
				.version(VERSION)
				.metadata(METADATA_CAPABILITIES, capabilities())
				.metadata(METADATA_SERVICES, services())
				.metadata(METADATA_MODE, mode)
				.serviceInterface(interfaceBuilder(EXECUTION_PATH)
						.operation(OP_SERVICE_INFO, op(HttpMethod.POST, EXECUTION_SERVICE_INFO_PATH))
						.operation(OP_START, op(HttpMethod.POST, EXECUTION_START_PATH))
						.operation(OP_ABORT, op(HttpMethod.POST, EXECUTION_ABORT_PATH))
						.operation(OP_GET_STATUS, op(HttpMethod.GET, EXECUTION_STATUS_PATH))
						.build());
		if (workstation != null && !workstation.isBlank()) {
			execution.metadata(METADATA_WORKSTATION, workstation);
		}
		if (unrestrictedDiscovery) {
			execution.metadata(Constants.METADATA_KEY_UNRESTRICTED_DISCOVERY, true);
		}
		final ServiceModel generalManagement = new ServiceModel.Builder()
				.serviceDefinition(Constants.SERVICE_DEF_GENERAL_MANAGEMENT)
				.version(VERSION)
				.serviceInterface(interfaceBuilder(WFE_GENERAL_MANAGEMENT_PATH)
						.operation(Constants.SERVICE_OP_GET_LOG, op(HttpMethod.POST, Constants.HTTP_API_OP_LOGS_PATH))
						.operation(Constants.SERVICE_OP_GET_CONFIG, op(HttpMethod.GET, Constants.HTTP_API_OP_GET_CONFIG_PATH))
						.build())
				.build();
		return List.of(generalManagement, execution.build());
	}

	public List<String> capabilities() {
		return capabilities == null ? List.of() : capabilities.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	public List<String> services() {
		return services == null ? List.of() : services.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	public String workstation() {
		return workstation == null || workstation.isBlank() ? null : workstation;
	}

	public String mode() {
		return mode;
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
						WorkflowExecutorConstants.CAPABILITIES,
						WorkflowExecutorConstants.SERVICES,
						WorkflowExecutorConstants.WORKSTATION,
						WorkflowExecutorConstants.MODE,
						WorkflowExecutorConstants.UNRESTRICTED_DISCOVERY,
						WorkflowExecutorConstants.CHOREOGRAPHER_ENABLED),
				WorkflowExecutorDefaults.class);
	}

	private HttpInterfaceModel.Builder interfaceBuilder(final String basePath) {
		final String templateName = getSslProperties().isSslEnabled() ? Constants.GENERIC_HTTPS_INTERFACE_TEMPLATE_NAME : Constants.GENERIC_HTTP_INTERFACE_TEMPLATE_NAME;
		return new HttpInterfaceModel.Builder(templateName, getDomainAddress(), getServerPort()).basePath(basePath);
	}

	private static HttpOperationModel op(final HttpMethod method, final String path) {
		return new HttpOperationModel.Builder().method(method.name()).path(path).build();
	}
}
