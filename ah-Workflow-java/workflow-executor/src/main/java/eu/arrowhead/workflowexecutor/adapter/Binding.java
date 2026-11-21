/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.adapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.arrowhead.dto.OrchestrationResultDTO;
import eu.arrowhead.dto.ServiceInstanceInterfaceResponseDTO;

/** π: the provider interface selected by orchestration for this executor as consumer. */
public record Binding(String provider, String serviceInstanceId, String serviceDefinition, String templateName, String scheme, String address, int port,
		String basePath, Map<String, Operation> operations) {

	public record Operation(String method, String path) {
	}

	/** First interface of the first orchestration result (generic_http / generic_https property layout). */
	@SuppressWarnings("unchecked")
	public static Binding fromOrchestrationResult(final OrchestrationResultDTO result) {
		if (result == null || result.interfaces() == null || result.interfaces().isEmpty()) {
			throw new IllegalArgumentException("Orchestration result has no interface");
		}
		final ServiceInstanceInterfaceResponseDTO intf = result.interfaces().get(0);
		final Map<String, Object> p = intf.properties() == null ? Map.of() : intf.properties();
		final Object addresses = p.get("accessAddresses");
		final String address = addresses instanceof List<?> list && !list.isEmpty() ? String.valueOf(list.get(0)) : String.valueOf(p.get("accessAddresses"));
		final int port = p.get("accessPort") instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(p.get("accessPort")));
		final Map<String, Operation> operations = new LinkedHashMap<>();
		if (p.get("operations") instanceof Map<?, ?> ops) {
			((Map<String, Object>) ops).forEach((name, value) -> {
				if (value instanceof Map<?, ?> m) {
					operations.put(name, new Operation(String.valueOf(m.get("method")), String.valueOf(m.get("path"))));
				}
			});
		}
		final String template = intf.templateName();
		final String scheme = template != null && template.toLowerCase().contains("https") ? "https" : "http";
		return new Binding(result.providerName(), result.serviceInstanceId(), result.serviceDefinitition(), template, scheme, address, port,
				String.valueOf(p.getOrDefault("basePath", "")), operations);
	}

	/** Reported to the WorkflowManager as β(s); contains no credentials. */
	public JsonNode toReport() {
		final ObjectNode node = JsonNodeFactory.instance.objectNode();
		node.put("provider", provider);
		if (serviceInstanceId != null) {
			node.put("serviceInstanceId", serviceInstanceId);
		}
		node.put("serviceDefinition", serviceDefinition);
		node.put("interface", templateName);
		node.put("address", address);
		node.put("port", port);
		return node;
	}
}
