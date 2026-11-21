/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator.service;

import static eu.arrowhead.workflowconfigurator.WorkflowConfiguratorConstants.$MANAGER_SYSTEM_WD;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.workflow.wire.WorkflowServices;

/**
 * The WorkflowManager's workflowRecipe service: the editor's workflows are stored there and nowhere else. Storing
 * validates first, because a refused registration's report (HTTP 400 body) does not survive the common HTTP client; the
 * WorkflowManager validates again when it registers.
 */
@Service
public class RecipeGateway {

	private static final JsonNodeFactory F = JsonNodeFactory.instance;
	private static final int MAX_RECIPE_BYTES = 512 * 1024;

	@Autowired
	private Upstream upstream;

	@Value($MANAGER_SYSTEM_WD)
	private String manager;

	public JsonNode list(final String name) {
		final MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
		if (name != null && !name.isBlank()) {
			query.add("name", name);
		}
		return upstream.query(WorkflowServices.SD_RECIPE, WorkflowServices.OP_QUERY, manager, query);
	}

	public JsonNode get(final String name, final String version) {
		return upstream.call(WorkflowServices.SD_RECIPE, WorkflowServices.OP_GET, manager, List.of(name, version));
	}

	public JsonNode validate(final JsonNode recipe) {
		check(recipe);
		return upstream.call(WorkflowServices.SD_RECIPE, WorkflowServices.OP_VALIDATE, manager, recipe);
	}

	/** validate, then register when the report accepts: {stored, report, recipe?, created?}. */
	public ObjectNode store(final JsonNode recipe) {
		final JsonNode report = validate(recipe);
		final ObjectNode out = F.objectNode();
		out.set("report", report);
		if (!"ACCEPT".equals(report.path("verdict").asText())) {
			out.put("stored", false);
			return out;
		}
		final JsonNode registered = upstream.call(WorkflowServices.SD_RECIPE, WorkflowServices.OP_REGISTER, manager, recipe);
		out.put("stored", true);
		out.put("created", registered.path("created").asBoolean());
		out.set("recipe", registered.path("recipe"));
		return out;
	}

	private static void check(final JsonNode recipe) {
		if (recipe == null || !recipe.isObject()) {
			throw new InvalidParameterException("The workflow must be a recipe object");
		}
		if (recipe.toString().length() > MAX_RECIPE_BYTES) {
			throw new InvalidParameterException("The recipe is larger than " + MAX_RECIPE_BYTES / 1024 + " KiB");
		}
	}
}
