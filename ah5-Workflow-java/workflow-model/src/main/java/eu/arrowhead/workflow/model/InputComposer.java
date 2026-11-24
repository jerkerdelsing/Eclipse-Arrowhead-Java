/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Input composition κ_s(ρ, ξ|dep(s)) (Sensors paper §4.8, §5.4).
 *
 * <p>The template is any JSON value. A string that is exactly one placeholder is replaced by the referenced JSON value;
 * placeholders inside longer strings are replaced by their text. Placeholders:
 * <ul>
 * <li>{@code ${param.<name>[.<path>]}}: session parameter ρ</li>
 * <li>{@code ${result.<step>[.<path>]}}: stored result ξ(step); the step must be in dep(s)</li>
 * <li>{@code ${params.<key>}}: static step parameter</li>
 * </ul>
 * Without a template the input is {@code {"parameters": ρ, "results": ξ|dep(s), "params": params(s)}}.
 * Because κ_s takes a map indexed by step, the input does not depend on the order of parallel predecessors.
 */
public final class InputComposer {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(param|result|params)\\.([A-Za-z][A-Za-z0-9_-]*)((?:\\.[A-Za-z0-9_-]+)*)}");
	private static final JsonNodeFactory F = JsonNodeFactory.instance;

	private InputComposer() {
		throw new UnsupportedOperationException();
	}

	public static JsonNode compose(final Recipe.Step step, final JsonNode parameters, final Map<String, JsonNode> dependencyResults) {
		final JsonNode rho = parameters == null ? F.objectNode() : parameters;
		final JsonNode params = RecipeCodec.toTree(step.params());
		if (step.input() == null || step.input().isNull()) {
			final ObjectNode input = F.objectNode();
			input.set("parameters", rho);
			final ObjectNode results = input.putObject("results");
			dependencyResults.forEach(results::set);
			input.set("params", params);
			return input;
		}
		return substitute(step.input(), rho, dependencyResults, params);
	}

	/** Steps a template refers to; dep(s) must contain them (and can be inferred from them). */
	public static Set<String> referencedSteps(final JsonNode template) {
		return references(template, "result");
	}

	public static Set<String> referencedParameters(final JsonNode template) {
		return references(template, "param");
	}

	//-------------------------------------------------------------------------------------------------
	private static Set<String> references(final JsonNode template, final String kind) {
		final Set<String> result = new LinkedHashSet<>();
		collect(template, kind, result);
		return result;
	}

	private static void collect(final JsonNode node, final String kind, final Set<String> into) {
		if (node == null) {
			return;
		}
		if (node.isTextual()) {
			final Matcher m = PLACEHOLDER.matcher(node.asText());
			while (m.find()) {
				if (kind.equals(m.group(1))) {
					into.add(m.group(2));
				}
			}
		} else if (node.isContainerNode()) {
			node.forEach(child -> collect(child, kind, into));
		}
	}

	private static JsonNode substitute(final JsonNode node, final JsonNode rho, final Map<String, JsonNode> results, final JsonNode params) {
		if (node.isTextual()) {
			final String text = node.asText();
			final Matcher whole = PLACEHOLDER.matcher(text);
			if (whole.matches()) {
				return resolve(whole, rho, results, params);
			}
			final Matcher m = PLACEHOLDER.matcher(text);
			final StringBuilder sb = new StringBuilder();
			while (m.find()) {
				final JsonNode value = resolve(m, rho, results, params);
				m.appendReplacement(sb, Matcher.quoteReplacement(value.isValueNode() ? value.asText() : value.toString()));
			}
			m.appendTail(sb);
			return TextNode.valueOf(sb.toString());
		}
		if (node.isObject()) {
			final ObjectNode out = F.objectNode();
			final Iterator<Map.Entry<String, JsonNode>> it = node.fields();
			while (it.hasNext()) {
				final Map.Entry<String, JsonNode> e = it.next();
				out.set(e.getKey(), substitute(e.getValue(), rho, results, params));
			}
			return out;
		}
		if (node.isArray()) {
			final ArrayNode out = F.arrayNode();
			node.forEach(child -> out.add(substitute(child, rho, results, params)));
			return out;
		}
		return node;
	}

	private static JsonNode resolve(final Matcher m, final JsonNode rho, final Map<String, JsonNode> results, final JsonNode params) {
		final String kind = m.group(1);
		final String name = m.group(2);
		JsonNode value = switch (kind) {
		case "param" -> rho.get(name);
		case "params" -> params.get(name);
		default -> {
			if (!results.containsKey(name)) {
				// Lemma 2 guarantees ξ(p) for p ∈ dep(s); a reference outside dep(s) is refused
				throw new IllegalStateException("Input refers to result of step '" + name + "', which is not in dep(s) or not completed");
			}
			yield results.get(name);
		}
		};
		final String path = m.group(3);
		if (path != null && !path.isEmpty()) {
			for (final String segment : path.substring(1).split("\\.")) {
				value = value == null ? null : value.isArray() && segment.matches("\\d+") ? value.get(Integer.parseInt(segment)) : value.get(segment);
			}
		}
		return value == null ? F.nullNode() : value;
	}
}
