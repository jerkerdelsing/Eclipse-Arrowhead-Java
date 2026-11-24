/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator.api.http;

import static eu.arrowhead.workflowconfigurator.WorkflowConfiguratorConstants.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.arrowhead.common.Constants;
import eu.arrowhead.workflowconfigurator.service.CloudDirectory;
import eu.arrowhead.workflowconfigurator.service.RecipeGateway;
import eu.arrowhead.workflowconfigurator.service.UpstreamException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/** workflowConfiguration: used by the graphical editor (console connector) and by other tools (Arrowhead connector). */
@RestController
@RequestMapping(path = API_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class ConfigurationAPI {

	@Autowired
	private CloudDirectory clouds;

	@Autowired
	private RecipeGateway recipes;

	@Operation(summary = "Configured local clouds: the live cloud, LocalCloudConfigurator designs, SoLC member clouds (list-clouds)")
	@GetMapping(CLOUDS_PATH)
	public JsonNode listClouds() {
		return clouds.clouds();
	}

	@Operation(summary = "Microservices of a cloud from the ServiceRegistry (list-services)")
	@GetMapping(CLOUD_SERVICES_PATH)
	public JsonNode listServices(@PathVariable final String id) {
		return clouds.services(CloudDirectory.checkCloudId(id));
	}

	@Operation(summary = "WorkflowExecutors with their capabilities (list-executors)")
	@GetMapping(EXECUTORS_PATH)
	public JsonNode listExecutors() {
		return clouds.executors();
	}

	@Operation(summary = "Recipes stored in the WorkflowManager (list-recipes)")
	@GetMapping(RECIPES_PATH)
	public JsonNode listRecipes(@RequestParam(required = false) final String name) {
		return recipes.list(name);
	}

	@Operation(summary = "One stored recipe (get-recipe)")
	@GetMapping(RECIPE_ITEM_PATH)
	public JsonNode getRecipe(@PathVariable final String name, @PathVariable final String version) {
		return recipes.get(name, version);
	}

	@Operation(summary = "Validates a workflow with the WorkflowManager (validate-recipe)")
	@PostMapping(path = RECIPE_VALIDATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public JsonNode validate(@RequestBody final JsonNode recipe) {
		return recipes.validate(recipe);
	}

	@Operation(summary = "Stores a workflow in the WorkflowManager when it validates (store-recipe)")
	@PostMapping(path = RECIPES_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> store(@RequestBody final JsonNode recipe) {
		final ObjectNode result = recipes.store(recipe);
		final HttpStatus status = !result.path("stored").asBoolean() ? HttpStatus.UNPROCESSABLE_CONTENT
				: result.path("created").asBoolean() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(result);
	}

	@ExceptionHandler(UpstreamException.class)
	public ResponseEntity<JsonNode> upstream(final UpstreamException ex) {
		final ObjectNode body = JsonNodeFactory.instance.objectNode().put("error", ex.getMessage()).put("provider", ex.provider()).put("status", ex.status());
		return ResponseEntity.status(ex.status()).contentType(MediaType.APPLICATION_JSON).body(body);
	}
}
