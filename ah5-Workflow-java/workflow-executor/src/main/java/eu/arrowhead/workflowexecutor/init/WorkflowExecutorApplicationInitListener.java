/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.init;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

import javax.naming.ConfigurationException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.exception.InternalServerError;
import eu.arrowhead.common.init.ApplicationInitListener;
import eu.arrowhead.common.model.ServiceModel;
import eu.arrowhead.dto.ServiceInstanceCreateRequestDTO;
import eu.arrowhead.dto.ServiceInstanceInterfaceRequestDTO;
import eu.arrowhead.dto.ServiceInstanceResponseDTO;
import eu.arrowhead.dto.enums.ServiceInterfacePolicy;

@Component
public class WorkflowExecutorApplicationInitListener extends ApplicationInitListener {

	private static final int REGISTRATION_ATTEMPTS = 5;
	private static final long REGISTRATION_RETRY_MILLIS = 2000;

	@Value("${server.ssl.client-auth:none}")
	private String clientAuth;

	// Several executors usually start together. The ServiceRegistry creates a new service definition (workflowExecution)
	// with check-then-insert, so concurrent first registrations can fail with a duplicate key ("Database operation
	// error", HTTP 500). The registration is idempotent (it revokes the system first), so it is repeated a few times.
	@Override
	public void onApplicationEvent(final ContextRefreshedEvent event)
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException, ConfigurationException {
		for (int attempt = 1;; attempt++) {
			try {
				super.onApplicationEvent(event);
				return;
			} catch (final InternalServerError ex) {
				if (attempt >= REGISTRATION_ATTEMPTS) {
					throw ex;
				}
				logger.warn("Registration attempt {} failed ({}); retrying", attempt, ex.getMessage());
				registeredServices.clear();
				Thread.sleep(REGISTRATION_RETRY_MILLIS * attempt);
			}
		}
	}

	// The framework registers the interfaces of non-certificate authentication policies with policy NONE. When TLS
	// requires a client certificate, every consumer must present an X.509 identity, so the offers are re-registered
	// as CERT_AUTH. (sysMonitor is registered as CERT_AUTH by the common publisher.)
	@Override
	protected void customInit(final ContextRefreshedEvent event) {
		if (standaloneMode || !sysInfo.isSslEnabled() || !"need".equalsIgnoreCase(clientAuth)) {
			return;
		}

		for (final ServiceModel model : sysInfo.getServices()) {
			final List<ServiceInstanceInterfaceRequestDTO> interfaces = model.interfaces()
					.stream()
					.map(i -> new ServiceInstanceInterfaceRequestDTO(i.templateName(), i.protocol(), ServiceInterfacePolicy.CERT_AUTH.name(), i.properties()))
					.toList();
			final ServiceInstanceCreateRequestDTO payload = new ServiceInstanceCreateRequestDTO(model.serviceDefinition(), model.version(), null, model.metadata(), interfaces);
			final ServiceInstanceResponseDTO response = arrowheadHttpService.consumeService(
					Constants.SERVICE_DEF_SERVICE_DISCOVERY,
					Constants.SERVICE_OP_REGISTER,
					Constants.SYS_NAME_SERVICE_REGISTRY,
					ServiceInstanceResponseDTO.class,
					payload);
			registeredServices.add(response.instanceId());
		}

		logger.info("Re-registered {} service(s) with CERT_AUTH interfaces", sysInfo.getServices().size());
	}
}
