/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.api.compat;

import java.util.List;
import java.util.Map;

import eu.arrowhead.dto.OrchestrationResultDTO;
import eu.arrowhead.dto.OrchestrationServiceRequirementDTO;

/** Wire records of the Choreographer executor interface (ah5-Choreographer-java-spring 5.x, unchanged from v4.6). */
public final class CompatDTOs {

	private CompatDTOs() {
		throw new UnsupportedOperationException();
	}

	/** POST service-info */
	public record ExecutorServiceInfoRequest(String serviceDefinition, int minVersion, int maxVersion) {
	}

	public record ExecutorDependency(OrchestrationServiceRequirementDTO serviceRequirement, Boolean localCloudOnly) {
	}

	public record ExecutorServiceInfoResponse(String serviceDefinition, Integer minVersion, Integer maxVersion, List<ExecutorDependency> dependencies) {
	}

	/** POST start: the Choreographer orchestrated on behalf of this executor. */
	public record ExecuteStepRequest(long sessionId, long sessionStepId, List<OrchestrationResultDTO> preconditionOrchestrationResults,
			OrchestrationResultDTO mainOrchestrationResult, int quantity, Map<String, String> staticParameters) {
	}

	/** POST abort */
	public record AbortStepRequest(long sessionId, long sessionStepId) {
	}

	/** choreographerExecutor.notify-step-done; status SUCCESS, ERROR, FATAL_ERROR or ABORTED. */
	public record ExecutedStepResult(Long sessionId, Long sessionStepId, String status, String message, String exception) {
	}

	/** choreographerExecutor.register */
	public record ExecutorRegistrationRequest(String address, Integer port, String basePath, String serviceDefinitionName, Integer minVersion, Integer maxVersion) {
	}
}
