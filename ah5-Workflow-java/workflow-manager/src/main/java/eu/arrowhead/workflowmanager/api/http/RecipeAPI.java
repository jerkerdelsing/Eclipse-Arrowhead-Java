/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.http;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflow.model.compat.ChoreographerPlanCompiler;
import eu.arrowhead.workflow.model.validation.ValidationReport;
import eu.arrowhead.workflowmanager.api.dto.CompileRequestDTO;
import eu.arrowhead.workflowmanager.api.dto.RecipeListResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.RecipeRegistrationResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.RecipeResponseDTO;
import eu.arrowhead.workflowmanager.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;

/** workflowRecipe. */
@RestController
@RequestMapping(RECIPE_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class RecipeAPI {

	@Autowired
	private RecipeService recipeService;

	@Operation(summary = "Validates a recipe: (W1)-(W4), model scope, data coverage, leases, catalogue (workflowRecipe.validate)")
	@PostMapping(path = RECIPE_VALIDATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody ValidationReport validate(@RequestBody final JsonNode recipe) {
		return recipeService.validate(recipe);
	}

	@Operation(summary = "Registers an accepted recipe version; 400 with the validation report otherwise (workflowRecipe.register)")
	@PostMapping(path = RECIPE_REGISTER_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<RecipeRegistrationResponseDTO> register(final HttpServletRequest request, @RequestBody final JsonNode recipe) {
		final String origin = HttpMethod.POST.name() + " " + RECIPE_PATH + RECIPE_REGISTER_PATH;
		final RecipeService.Registration r = recipeService.register(recipe, HttpUtilities.acquireName(request, origin));
		if (r.record() == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new RecipeRegistrationResponseDTO(null, r.report(), false));
		}
		return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK).body(new RecipeRegistrationResponseDTO(DTOConverter.recipe(r.record()), r.report(), r.created()));
	}

	@Operation(summary = "Lists registered recipes, optionally of one name (workflowRecipe.query); summary=true leaves out the "
			+ "recipe documents, so the list stays small however large the recipes are")
	@GetMapping(path = RECIPE_QUERY_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody RecipeListResponseDTO query(@RequestParam(required = false) final String name,
			@RequestParam(required = false, defaultValue = "false") final boolean summary) {
		final var entries = recipeService.query(name).stream().map(r -> summary ? DTOConverter.recipeSummary(r) : DTOConverter.recipe(r)).toList();
		return new RecipeListResponseDTO(entries, entries.size());
	}

	@Operation(summary = "Returns one recipe version (workflowRecipe.get)")
	@GetMapping(path = RECIPE_ITEM_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody RecipeResponseDTO get(@PathVariable final String name, @PathVariable final String version) {
		return DTOConverter.recipe(recipeService.get(name, version));
	}

	@Operation(summary = "Retires a recipe version; running sessions continue (workflowRecipe.retire)")
	@DeleteMapping(path = RECIPE_ITEM_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody RecipeResponseDTO retire(@PathVariable final String name, @PathVariable final String version) {
		return DTOConverter.recipe(recipeService.retire(name, version));
	}

	@Operation(summary = "Compiles a recipe to a Choreographer plan request (workflowRecipe.compile)")
	@PostMapping(path = RECIPE_COMPILE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody ChoreographerPlanCompiler.Plan compile(@RequestBody final CompileRequestDTO dto) {
		return recipeService.compile(dto.name(), dto.version());
	}
}
