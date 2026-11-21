/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.common.service.validation.name.SystemNameNormalizer;
import eu.arrowhead.dto.ServiceInstanceInterfaceResponseDTO;
import eu.arrowhead.dto.ServiceInstanceListResponseDTO;
import eu.arrowhead.dto.ServiceInstanceLookupRequestDTO;
import eu.arrowhead.dto.ServiceInstanceResponseDTO;
import eu.arrowhead.workflow.model.Recipe.Step;
import eu.arrowhead.workflow.wire.WorkflowServices;
import eu.arrowhead.workflowmanager.WorkflowManagerConstants;
import eu.arrowhead.workflowmanager.init.StandaloneModeSwitch;

/**
 * The executor set WE_i with cap(w). Executors are found in the ServiceRegistry as providers of workflowExecution
 * (metadata workflowCapabilities, workflowServices, workflowWorkstation), plus statically configured executors.
 */
@Component
public class ExecutorDirectory {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private ArrowheadHttpService arrowheadHttpService;

	@Autowired
	private StandaloneModeSwitch standalone;

	@Autowired
	private SystemNameNormalizer systemNameNormalizer;

	@Value(WorkflowManagerConstants.$STATIC_EXECUTORS_WD)
	private String staticExecutors;

	@Value(WorkflowManagerConstants.$DISCOVERY_CACHE_SECONDS_WD)
	private long cacheSeconds;

	private volatile List<ExecutorEndpoint> cached = List.of();
	private volatile long cachedAt;

	public record ExecutorEndpoint(String name, String scheme, String address, int port, String basePath, Set<String> capabilities, Set<String> services,
			String workstation, String mode, String source) {

		/** w ⊧ η(s) for steps that require a capability; otherwise the executor declares θ(s) (it stands for the choreographer). */
		public boolean canRun(final Step step) {
			final boolean runsService = WorkflowServices.covers(services, step.serviceDefinition());
			if (!step.requiresExecutorCapability()) {
				return runsService && !services.isEmpty();
			}
			final var requirement = step.executor();
			return capabilities.contains(requirement.capability())
					&& (requirement.workstation() == null || requirement.workstation().equals(workstation))
					&& (requirement.system() == null || requirement.system().equals(name))
					&& runsService;
		}

		public boolean isPull() {
			return "pull".equalsIgnoreCase(mode);
		}
	}

	public synchronized List<ExecutorEndpoint> all() {
		if (System.currentTimeMillis() - cachedAt > cacheSeconds * 1000) {
			final Map<String, ExecutorEndpoint> byName = new LinkedHashMap<>();
			parseStatic().forEach(e -> byName.put(e.name(), e));
			if (!standalone.isStandalone()) {
				try {
					discover().forEach(e -> byName.putIfAbsent(e.name(), e));
				} catch (final RuntimeException ex) {
					logger.warn("Executor discovery failed: {}", ex.getMessage());
				}
			}
			cached = List.copyOf(byName.values());
			cachedAt = System.currentTimeMillis();
		}
		return cached;
	}

	public void invalidate() {
		cachedAt = 0;
	}

	/** Candidates for a step, excluding executors already tried in this session (push mode). */
	public List<ExecutorEndpoint> candidates(final Step step, final Collection<String> excluded) {
		return all().stream().filter(e -> !e.isPull() && !excluded.contains(e.name()) && e.canRun(step)).toList();
	}

	public Optional<ExecutorEndpoint> byName(final String name) {
		return all().stream().filter(e -> e.name().equals(name)).findFirst();
	}

	//-------------------------------------------------------------------------------------------------
	private List<ExecutorEndpoint> parseStatic() {
		final List<ExecutorEndpoint> result = new ArrayList<>();
		if (staticExecutors == null) {
			return result;
		}
		for (final String entry : staticExecutors.split("[;\\n]")) {
			if (entry == null || entry.isBlank()) {
				continue;
			}
			final String[] parts = entry.trim().split("\\|", -1);
			if (parts.length < 4) {
				logger.warn("Ignoring malformed workflow.executors entry: {}", entry);
				continue;
			}
			final URI uri = URI.create(parts[1]);
			// same normalization as authenticated requester names (extended mode: wfe-agv -> WfeAgv)
			result.add(new ExecutorEndpoint(systemNameNormalizer.normalize(parts[0]), uri.getScheme(), uri.getHost(), uri.getPort(), WorkflowServices.EXECUTION_PATH, split(parts[2]), split(parts[3]),
					parts.length > 4 && !parts[4].isBlank() ? parts[4] : null, "push", "static"));
		}
		return result;
	}

	private List<ExecutorEndpoint> discover() {
		final ServiceInstanceLookupRequestDTO request = new ServiceInstanceLookupRequestDTO.Builder().serviceDefinitionName(WorkflowServices.SD_EXECUTION).build();
		final ServiceInstanceListResponseDTO response = arrowheadHttpService.consumeService(Constants.SERVICE_DEF_SERVICE_DISCOVERY, Constants.SERVICE_OP_LOOKUP,
				ServiceInstanceListResponseDTO.class, request);
		final List<ExecutorEndpoint> result = new ArrayList<>();
		if (response == null || response.entries() == null) {
			return result;
		}
		for (final ServiceInstanceResponseDTO instance : response.entries()) {
			if (instance.interfaces() == null || instance.interfaces().isEmpty() || instance.provider() == null) {
				continue;
			}
			final ServiceInstanceInterfaceResponseDTO intf = instance.interfaces().get(0);
			final Map<String, Object> p = intf.properties();
			final Object addresses = p.get("accessAddresses");
			final String address = addresses instanceof List<?> list && !list.isEmpty() ? String.valueOf(list.get(0)) : String.valueOf(addresses);
			final int port = p.get("accessPort") instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(p.get("accessPort")));
			final String scheme = intf.templateName() != null && intf.templateName().toLowerCase().contains("https") ? "https" : "http";
			final Map<String, Object> md = instance.metadata() == null ? Map.of() : instance.metadata();
			result.add(new ExecutorEndpoint(instance.provider().name(), scheme, address, port, String.valueOf(p.getOrDefault("basePath", WorkflowServices.EXECUTION_PATH)),
					values(md.get(WorkflowServices.METADATA_CAPABILITIES)), values(md.get(WorkflowServices.METADATA_SERVICES)),
					md.get(WorkflowServices.METADATA_WORKSTATION) == null ? null : String.valueOf(md.get(WorkflowServices.METADATA_WORKSTATION)),
					md.get(WorkflowServices.METADATA_MODE) == null ? "push" : String.valueOf(md.get(WorkflowServices.METADATA_MODE)), "registry"));
		}
		return result;
	}

	private static Set<String> split(final String csv) {
		final Set<String> result = new LinkedHashSet<>();
		for (final String s : csv.split(",")) {
			if (!s.isBlank()) {
				result.add(s.trim());
			}
		}
		return result;
	}

	private static Set<String> values(final Object value) {
		if (value == null) {
			return Set.of();
		}
		if (value instanceof Collection<?> c) {
			final Set<String> result = new LinkedHashSet<>();
			c.forEach(v -> result.add(String.valueOf(v)));
			return result;
		}
		return split(String.valueOf(value));
	}
}
