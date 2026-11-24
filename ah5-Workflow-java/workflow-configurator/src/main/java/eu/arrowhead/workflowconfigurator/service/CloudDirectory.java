/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator.service;

import static eu.arrowhead.workflowconfigurator.WorkflowConfiguratorConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.common.exception.DataNotFoundException;
import eu.arrowhead.workflow.wire.WorkflowServices;

/**
 * The local clouds the editor can design for, and the microservices in them.
 *
 * <ul>
 * <li>{@code live}: the cloud this system runs in; its systems and services come from the ServiceRegistry
 * (serviceRegistryManagement system-query and service-query, which need a ConsumerAuthorization grant).</li>
 * <li>{@code lcc:<id>}: a local-cloud design stored in LocalCloudConfigurator (localCloudConfiguration catalog and
 * list-configurations). Its systems are the design's components; their services are those the live ServiceRegistry
 * holds for these systems.</li>
 * <li>{@code solc:<design>:<cloud>}: a member cloud of a System of LocalClouds design in SoLocalCloudConfigurator
 * (soLocalCloudConfiguration list-designs, get-design). Another cloud's ServiceRegistry is not reachable from here, so
 * its services are listed only when it is the live cloud; otherwise the editor names service definitions itself.</li>
 * </ul>
 * Every source is optional: an unavailable one is reported in {@code sources} and the others are still listed.
 */
@Service
public class CloudDirectory {

	private static final JsonNodeFactory F = JsonNodeFactory.instance;
	private static final String SR_SYSTEMS_QUERY = "/serviceregistry/mgmt/systems/query";
	private static final String SR_SERVICES_QUERY = "/serviceregistry/mgmt/service-instances/query";

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private Upstream upstream;

	@Value($LCC_SYSTEM_WD)
	private String lccSystem;

	@Value($SOLC_SYSTEM_WD)
	private String solcSystem;

	@Value($SOLC_ENABLED_WD)
	private boolean solcEnabled;

	@Value($HIDDEN_SERVICES_WD)
	private List<String> hiddenServices;

	@Value("${arrowhead.cloud.name:TestCloud}")
	private String fallbackCloudName;

	//=================================================================================================
	// clouds

	public ObjectNode clouds() {
		final ObjectNode out = F.objectNode();
		final ArrayNode sources = out.putArray("sources");
		final ArrayNode entries = out.putArray("entries");

		// catalog: cloud name and component id -> system name
		JsonNode catalog = null;
		try {
			catalog = upstream.call(SD_LOCAL_CLOUD_CONFIGURATION, "catalog", lccSystem, (Object) null);
			sources.add(source(lccSystem, "catalog", true, null));
		} catch (final UpstreamException ex) {
			sources.add(source(lccSystem, "catalog", false, ex));
		}
		final String liveCloud = catalog != null && catalog.hasNonNull("cloud") ? catalog.get("cloud").asText() : fallbackCloudName;
		final Map<String, ObjectNode> components = components(catalog);

		// live
		final ObjectNode live = entries.addObject();
		live.put("id", "live").put("name", liveCloud).put("cloud", liveCloud).put("source", Constants.SYS_NAME_SERVICE_REGISTRY).put("live", true)
				.put("description", "The running cloud " + liveCloud + " as its ServiceRegistry sees it");
		final ArrayNode liveSystems = live.putArray("systems");
		try {
			for (final String name : registeredSystems()) {
				liveSystems.addObject().put("name", name).put("running", true);
			}
			sources.add(source(Constants.SYS_NAME_SERVICE_REGISTRY, discoveryFallback ? "lookup" : "system-query", true, null));
			if (discoveryFallback) {
				out.put("note", "The ServiceRegistry refused management queries; systems and services come from discovery, which under the RESTRICTED"
						+ " policy shows only services offered with unrestrictedDiscovery. Grant serviceRegistryManagement system-query and service-query.");
			}
		} catch (final UpstreamException ex) {
			sources.add(source(Constants.SYS_NAME_SERVICE_REGISTRY, "system-query", false, ex));
		}
		final Set<String> running = new LinkedHashSet<>();
		liveSystems.forEach(s -> running.add(s.get("name").asText()));

		// LocalCloudConfigurator designs
		try {
			final JsonNode designs = upstream.call(SD_LOCAL_CLOUD_CONFIGURATION, "list-configurations", lccSystem, (Object) null);
			for (final JsonNode d : designs.path("entries")) {
				final ObjectNode e = entries.addObject();
				e.put("id", "lcc:" + d.path("id").asText()).put("name", d.path("name").asText()).put("cloud", liveCloud).put("source", lccSystem)
						.put("live", true).put("revision", d.path("revision").asLong()).put("updatedAt", d.path("updatedAt").asText(null))
						.put("description", "LocalCloudConfigurator design for " + liveCloud);
				e.set("systems", systems(d.path("systems"), components, running));
			}
			sources.add(source(lccSystem, "list-configurations", true, null));
		} catch (final UpstreamException ex) {
			sources.add(source(lccSystem, "list-configurations", false, ex));
		}

		// SoLocalCloudConfigurator member clouds
		if (solcEnabled) {
			try {
				final JsonNode designs = upstream.call(SD_SOLC_CONFIGURATION, "list-designs", solcSystem, (Object) null);
				for (final JsonNode d : designs.path("entries")) {
					final String designId = d.path("id").asText();
					final JsonNode full = upstream.call(SD_SOLC_CONFIGURATION, "get-design", solcSystem, List.of(designId));
					final JsonNode design = full.has("design") ? full.get("design") : full;
					for (final JsonNode m : design.path("members")) {
						final String cloud = m.path("cloud").asText();
						final boolean isLive = cloud.equals(liveCloud);
						final ObjectNode e = entries.addObject();
						e.put("id", "solc:" + designId + ":" + cloud).put("name", cloud).put("cloud", cloud).put("source", solcSystem).put("live", isLive)
								.put("description", "Member cloud of the System of LocalClouds " + d.path("name").asText()
										+ (m.path("lcDesign").hasNonNull("name") ? ", built from design " + m.path("lcDesign").path("name").asText() : ""));
						final ArrayNode ids = F.arrayNode();
						m.path("lcDesign").path("components").forEach(ids::add);
						m.path("intercloud").forEach(ids::add);
						e.set("systems", systems(ids, components, isLive ? running : Set.of()));
					}
				}
				sources.add(source(solcSystem, "list-designs", true, null));
			} catch (final UpstreamException ex) {
				sources.add(source(solcSystem, "list-designs", false, ex));
			}
		}
		return out;
	}

	//=================================================================================================
	// services

	/** Microservices of a cloud: provider, service definition, version, interfaces and operations. */
	public ObjectNode services(final String cloudId) {
		final ObjectNode clouds = clouds();
		ObjectNode cloud = null;
		for (final JsonNode c : clouds.path("entries")) {
			if (c.path("id").asText().equals(cloudId)) {
				cloud = (ObjectNode) c;
			}
		}
		if (cloud == null) {
			throw new DataNotFoundException("Unknown cloud " + cloudId);
		}
		final ObjectNode out = F.objectNode();
		out.put("cloud", cloudId).put("name", cloud.path("name").asText()).put("live", cloud.path("live").asBoolean());
		out.set("systems", cloud.get("systems"));
		final ArrayNode entries = out.putArray("entries");
		if (!cloud.path("live").asBoolean()) {
			out.put("note", "The ServiceRegistry of " + cloud.path("name").asText() + " is not reachable from this cloud; name the service definitions in the editor");
			return out;
		}
		final Set<String> inCloud = new LinkedHashSet<>();
		cloud.path("systems").forEach(s -> inCloud.add(s.path("name").asText()));
		final boolean all = "live".equals(cloudId);
		final JsonNode instances = serviceInstances(new ArrayList<>(inCloud));
		if (discoveryFallback) {
			out.put("note", "Discovery only: services offered without unrestrictedDiscovery are not listed (no serviceRegistryManagement grant)");
		}
		for (final JsonNode instance : instances) {
			final String definition = instance.path("serviceDefinition").path("name").asText();
			final String provider = instance.path("provider").path("name").asText();
			if (hiddenServices.contains(definition) || (!all && !inCloud.contains(provider))) {
				continue;
			}
			final ObjectNode e = entries.addObject();
			e.put("provider", provider).put("serviceDefinition", definition).put("version", instance.path("version").asText());
			e.set("metadata", instance.path("metadata").isObject() ? instance.get("metadata") : F.objectNode());
			final ArrayNode interfaces = e.putArray("interfaces");
			final Set<String> operations = new LinkedHashSet<>();
			for (final JsonNode i : instance.path("interfaces")) {
				interfaces.addObject().put("templateName", i.path("templateName").asText()).put("policy", i.path("policy").asText());
				final JsonNode ops = i.path("properties").path("operations");
				if (ops.isObject()) {
					ops.fieldNames().forEachRemaining(operations::add);
				} else if (ops.isArray()) {
					ops.forEach(o -> operations.add(o.asText()));
				}
			}
			final ArrayNode opArray = e.putArray("operations");
			operations.forEach(opArray::add);
		}
		return out;
	}

	/** WorkflowExecutors in the live cloud with cap(w), the services they run and their mode (for η(s)). */
	public ObjectNode executors() {
		final ObjectNode out = F.objectNode();
		final ArrayNode entries = out.putArray("entries");
		final ObjectNode query = F.objectNode();
		query.putArray("serviceDefinitionNames").add(WorkflowServices.SD_EXECUTION);
		JsonNode response;
		try {
			response = upstream.serviceRegistryManagement(Constants.SERVICE_OP_SERVICE_QUERY, SR_SERVICES_QUERY, query);
		} catch (final UpstreamException ex) {
			if (ex.status() != 403) {
				throw ex;
			}
			// workflowExecution is offered with unrestrictedDiscovery, so discovery finds the executors
			final ObjectNode lookup = F.objectNode();
			lookup.putArray("serviceDefinitionNames").add(WorkflowServices.SD_EXECUTION);
			response = upstream.call(Constants.SERVICE_DEF_SERVICE_DISCOVERY, Constants.SERVICE_OP_LOOKUP, Constants.SYS_NAME_SERVICE_REGISTRY, lookup);
		}
		for (final JsonNode instance : response.path("entries")) {
			final JsonNode md = instance.path("metadata");
			final ObjectNode e = entries.addObject();
			e.put("name", instance.path("provider").path("name").asText());
			e.set("capabilities", strings(md.get(WorkflowServices.METADATA_CAPABILITIES)));
			e.set("services", strings(md.get(WorkflowServices.METADATA_SERVICES)));
			e.put("mode", md.path(WorkflowServices.METADATA_MODE).asText("push"));
			if (md.hasNonNull(WorkflowServices.METADATA_WORKSTATION)) {
				e.put("workstation", md.get(WorkflowServices.METADATA_WORKSTATION).asText());
			}
		}
		return out;
	}

	//=================================================================================================
	// assistant methods

	/** Mode of the last ServiceRegistry read: management (all systems and services) or the discovery fallback. */
	private volatile boolean discoveryFallback;

	private List<String> registeredSystems() {
		final ObjectNode query = F.objectNode();
		query.putObject("pagination").put("page", 0).put("size", 1000);
		JsonNode response;
		try {
			response = upstream.serviceRegistryManagement(Constants.SERVICE_OP_SYSTEM_QUERY, SR_SYSTEMS_QUERY, query);
			discoveryFallback = false;
		} catch (final UpstreamException ex) {
			if (ex.status() != 403) {
				throw ex;
			}
			// no management permission: systemDiscovery lists the systems too
			logger.warn("ServiceRegistry management refused ({}); falling back to discovery", ex.getMessage());
			discoveryFallback = true;
			response = upstream.call(Constants.SERVICE_DEF_SYSTEM_DISCOVERY, Constants.SERVICE_OP_LOOKUP, Constants.SYS_NAME_SERVICE_REGISTRY, F.objectNode());
		}
		final List<String> names = new ArrayList<>();
		response.path("entries").forEach(s -> names.add(s.path("name").asText()));
		names.sort(String::compareToIgnoreCase);
		return names;
	}

	private JsonNode serviceInstances(final List<String> providers) {
		if (providers.isEmpty()) {
			return F.arrayNode();
		}
		final ObjectNode query = F.objectNode();
		final ArrayNode names = query.putArray("providerNames");
		providers.forEach(names::add);
		if (!discoveryFallback) {
			try {
				final ObjectNode q = query.deepCopy();
				q.putObject("pagination").put("page", 0).put("size", 1000);
				return upstream.serviceRegistryManagement(Constants.SERVICE_OP_SERVICE_QUERY, SR_SERVICES_QUERY, q).path("entries");
			} catch (final UpstreamException ex) {
				if (ex.status() != 403) {
					throw ex;
				}
				discoveryFallback = true;
			}
		}
		// serviceDiscovery lookup: under RESTRICTED discovery only services offered with unrestrictedDiscovery
		return upstream.call(Constants.SERVICE_DEF_SERVICE_DISCOVERY, Constants.SERVICE_OP_LOOKUP, Constants.SYS_NAME_SERVICE_REGISTRY, query).path("entries");
	}

	private static Map<String, ObjectNode> components(final JsonNode catalog) {
		final Map<String, ObjectNode> map = new HashMap<>();
		if (catalog != null) {
			for (final JsonNode s : catalog.path("systems")) {
				map.put(s.path("id").asText(), (ObjectNode) s);
			}
		}
		return map;
	}

	/** Design component ids as systems; components without an Arrowhead system name keep their id. */
	private static ArrayNode systems(final JsonNode ids, final Map<String, ObjectNode> components, final Set<String> running) {
		final Map<String, ObjectNode> unique = new LinkedHashMap<>();
		for (final JsonNode idNode : ids) {
			final String id = idNode.asText();
			final ObjectNode c = components.get(id);
			final String name = c != null && c.hasNonNull("name") ? c.get("name").asText() : id;
			final ObjectNode s = F.objectNode().put("id", id).put("name", name).put("running", running.contains(name));
			if (c != null && c.hasNonNull("kind")) {
				s.put("kind", c.get("kind").asText());
			}
			unique.putIfAbsent(name, s);
		}
		final ArrayNode out = F.arrayNode();
		unique.values().forEach(out::add);
		return out;
	}

	private static ArrayNode strings(final JsonNode node) {
		final ArrayNode out = F.arrayNode();
		if (node == null || node.isNull()) {
			return out;
		}
		if (node.isArray()) {
			node.forEach(v -> out.add(v.asText()));
		} else {
			for (final String part : node.asText().replace("[", "").replace("]", "").split(",")) {
				if (!part.isBlank()) {
					out.add(part.trim());
				}
			}
		}
		return out;
	}

	private ObjectNode source(final String system, final String operation, final boolean ok, final UpstreamException ex) {
		final ObjectNode s = F.objectNode().put("system", system).put("operation", operation).put("available", ok);
		if (ex != null) {
			s.put("status", ex.status()).put("error", ex.getMessage());
			logger.warn("{} {} unavailable: {}", system, operation, ex.getMessage());
		}
		return s;
	}

	/** Refuses ids that cannot be a cloud id (they end up in log lines). */
	public static String checkCloudId(final String id) {
		if (id == null || !id.matches("[A-Za-z0-9:_.-]{1,200}")) {
			throw new InvalidParameterException("Invalid cloud id");
		}
		return id;
	}
}
