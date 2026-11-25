/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.init;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.workflow.wire.WorkflowServices;
import eu.arrowhead.workflowexecutor.WorkflowExecutorConstants;
import eu.arrowhead.workflowexecutor.WorkflowExecutorSystemInfo;
import eu.arrowhead.workflowexecutor.api.compat.CompatDTOs.ExecutorRegistrationRequest;

/**
 * Option A (coexist with compatibility): registers this executor with the existing Choreographer
 * (choreographerExecutor.register) for every configured service definition, so Choreographer plans can use it too.
 */
@Component
public class ChoreographerRegistrar {

	private static final String SD_CHOREOGRAPHER_EXECUTOR = "choreographerExecutor";

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private ArrowheadHttpService arrowheadHttpService;

	@Autowired
	private WorkflowExecutorSystemInfo sysInfo;

	@Value(WorkflowExecutorConstants.$CHOREOGRAPHER_ENABLED_WD)
	private boolean enabled;

	@Value(WorkflowExecutorConstants.$CHOREOGRAPHER_SYSTEM_WD)
	private String choreographerSystem;

	@Value(WorkflowExecutorConstants.$CHOREOGRAPHER_MIN_VERSION_WD)
	private int minVersion;

	@Value(WorkflowExecutorConstants.$CHOREOGRAPHER_MAX_VERSION_WD)
	private int maxVersion;

	private boolean registered;

	@EventListener(ApplicationReadyEvent.class)
	public void register() {
		if (!enabled) {
			return;
		}
		final List<String> services = sysInfo.services().stream().filter(s -> !WorkflowServices.ANY_SERVICE.equals(s)).toList();
		if (services.isEmpty()) {
			logger.warn("Choreographer compatibility enabled, but workflow.executor.services lists no concrete service definition");
			return;
		}
		for (final String service : services) {
			try {
				arrowheadHttpService.consumeService(SD_CHOREOGRAPHER_EXECUTOR, "register", choreographerSystem, Void.class,
						new ExecutorRegistrationRequest(sysInfo.getDomainAddress(), sysInfo.getServerPort(), WorkflowServices.CHOREOGRAPHER_COMPAT_PATH, service, minVersion,
								maxVersion));
				registered = true;
				logger.info("Registered with {} as executor of {}", choreographerSystem, service);
			} catch (final RuntimeException ex) {
				logger.warn("Choreographer registration for {} failed: {}", service, ex.getMessage());
			}
		}
	}

	/**
	 * On ContextClosedEvent, which comes before any @PreDestroy: the common ApplicationInitListener revokes the services and
	 * logs out in its @PreDestroy, after which the identity token is invalid (found in the cloud test, 2026-11-20).
	 */
	@EventListener(ContextClosedEvent.class)
	public void unregister() {
		if (registered) {
			try {
				arrowheadHttpService.consumeService(SD_CHOREOGRAPHER_EXECUTOR, "unregister", choreographerSystem, Void.class);
				registered = false;
				logger.info("Unregistered from {}", choreographerSystem);
			} catch (final RuntimeException ex) {
				logger.warn("Choreographer unregistration failed: {}", ex.getMessage());
			}
		}
	}
}
