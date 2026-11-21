/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.runtime;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.dto.OrchestrationRequestDTO;
import eu.arrowhead.dto.OrchestrationResponseDTO;
import eu.arrowhead.dto.OrchestrationServiceRequirementDTO;
import eu.arrowhead.workflow.model.Recipe;
import eu.arrowhead.workflowexecutor.WorkflowExecutorConstants;
import eu.arrowhead.workflowexecutor.adapter.Binding;

/**
 * O_i(c_z(s), compile_i(s, z), γ, t): pull orchestration with this executor as the requester, so authorisation, QoS
 * filtering and provider selection are evaluated for the executor. Returns empty for ⊥. In development, providers may be
 * configured statically (workflow.executor.static-providers) and then take precedence.
 */
@Component
public class ProviderResolver {

	private static final String FLAG_MATCHMAKING = "MATCHMAKING";
	private static final String FLAG_ALLOW_INTERCLOUD = "ALLOW_INTERCLOUD";

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private ArrowheadHttpService arrowheadHttpService;

	@Value(WorkflowExecutorConstants.$STATIC_PROVIDERS_WD)
	private String staticProviders;

	@Value(WorkflowExecutorConstants.$ORCHESTRATOR_WD)
	private String orchestrator;

	public Optional<Binding> resolve(final Recipe.ServiceRequirement requirement, final boolean localCloudOnly) {
		final Binding configured = parseStatic().get(requirement.serviceDefinition());
		if (configured != null) {
			return Optional.of(configured);
		}
		final OrchestrationServiceRequirementDTO.Builder req = new OrchestrationServiceRequirementDTO.Builder().serviceDefinition(requirement.serviceDefinition());
		if (!requirement.interfaces().isEmpty()) {
			req.interfaceTemplateNames(requirement.interfaces());
		}
		if (requirement.minVersion() != null && requirement.minVersion().equals(requirement.maxVersion())) {
			req.version(requirement.minVersion());
		}
		final OrchestrationRequestDTO request = new OrchestrationRequestDTO.Builder()
				.serviceRequirement(req.build())
				.orchestrationFlag(FLAG_MATCHMAKING, true)
				.orchestrationFlag(FLAG_ALLOW_INTERCLOUD, !localCloudOnly)
				.build();
		final OrchestrationResponseDTO response = orchestrator == null || orchestrator.isBlank()
				? arrowheadHttpService.consumeService(Constants.SERVICE_DEF_SERVICE_ORCHESTRATION, Constants.SERVICE_OP_ORCHESTRATION_PULL, OrchestrationResponseDTO.class, request)
				: arrowheadHttpService.consumeService(Constants.SERVICE_DEF_SERVICE_ORCHESTRATION, Constants.SERVICE_OP_ORCHESTRATION_PULL, orchestrator, OrchestrationResponseDTO.class, request);
		if (response == null || response.results() == null || response.results().isEmpty()) {
			if (response != null && response.warnings() != null && !response.warnings().isEmpty()) {
				logger.info("Orchestration of {} gave ⊥: {}", requirement.serviceDefinition(), response.warnings());
			}
			return Optional.empty();
		}
		return Optional.of(Binding.fromOrchestrationResult(response.results().get(0)));
	}

	private Map<String, Binding> parseStatic() {
		final Map<String, Binding> result = new LinkedHashMap<>();
		if (staticProviders == null || staticProviders.isBlank()) {
			return result;
		}
		for (final String entry : staticProviders.split("[;\\n]")) {
			if (entry.isBlank()) {
				continue;
			}
			final String[] parts = entry.trim().split("\\|");
			final URI uri = URI.create(parts[1]);
			final Map<String, Binding.Operation> ops = new LinkedHashMap<>();
			if (parts.length > 2) {
				for (final String op : parts[2].split(",")) {
					final String[] nameRest = op.split("=", 2);
					final String[] methodPath = nameRest[1].split(":", 2);
					ops.put(nameRest[0].trim(), new Binding.Operation(methodPath[0].trim(), methodPath[1].trim()));
				}
			}
			result.put(parts[0].trim(), new Binding("static:" + uri.getHost() + ":" + uri.getPort(), null, parts[0].trim(),
					"https".equals(uri.getScheme()) ? Constants.GENERIC_HTTPS_INTERFACE_TEMPLATE_NAME : Constants.GENERIC_HTTP_INTERFACE_TEMPLATE_NAME,
					uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath() == null ? "" : uri.getPath(), ops));
		}
		return result;
	}

	List<String> staticServices() {
		return List.copyOf(parseStatic().keySet());
	}
}
