/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.DataNotFoundException;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.workflow.model.Recipe;
import eu.arrowhead.workflow.model.RecipeCodec;
import eu.arrowhead.workflow.model.compat.ChoreographerPlanCompiler;
import eu.arrowhead.workflow.model.validation.RecipeValidator;
import eu.arrowhead.workflow.model.validation.ValidationReport;
import eu.arrowhead.workflowmanager.jpa.entity.RecipeRecord;
import eu.arrowhead.workflowmanager.jpa.repository.RecipeRepository;

/** workflowRecipe: validation, the versioned catalogue (recipes are immutable) and compilation to Choreographer plans. */
@Service
public class RecipeService {

	@Autowired
	private RecipeRepository repository;

	@Autowired
	private CatalogueCheck catalogueCheck;

	private final Map<Long, Recipe> parsed = new ConcurrentHashMap<>();

	public record Registration(RecipeRecord record, ValidationReport report, boolean created) {
	}

	public ValidationReport validate(final JsonNode document) {
		return validator().validate(parse(document));
	}

	public Registration register(final JsonNode document, final String requester) {
		final Recipe recipe = parse(document);
		final ValidationReport report = validator().validate(recipe);
		if (!report.accepted()) {
			return new Registration(null, report, false);
		}
		final String canonical = RecipeCodec.write(recipe);
		final var existing = repository.findByNameAndVersion(recipe.name(), recipe.version());
		if (existing.isPresent()) {
			if (!existing.get().getDocument().equals(canonical)) {
				throw new WorkflowConflictException("Recipe " + recipe.key() + " already exists with different content; recipes are immutable, register a new version");
			}
			return new Registration(existing.get(), report, false);
		}
		return new Registration(repository.saveAndFlush(new RecipeRecord(recipe.name(), recipe.version(), canonical, requester)), report, true);
	}

	public List<RecipeRecord> query(final String name) {
		return Utilities.isEmpty(name) ? repository.findAllByOrderByIdAsc() : repository.findAllByNameOrderByIdAsc(name);
	}

	public RecipeRecord get(final String name, final String version) {
		return repository.findByNameAndVersion(name, version).orElseThrow(() -> new DataNotFoundException("Recipe not found: " + name + "@" + version));
	}

	/** The given version, or the latest active version when version is empty. */
	public RecipeRecord resolveActive(final String name, final String version) {
		if (Utilities.isEmpty(name)) {
			throw new InvalidParameterException("Recipe name is missing");
		}
		final RecipeRecord record;
		if (Utilities.isEmpty(version)) {
			record = repository.findAllByNameOrderByIdAsc(name).stream().filter(r -> RecipeRecord.ACTIVE.equals(r.getStatus())).reduce((a, b) -> b)
					.orElseThrow(() -> new DataNotFoundException("No active version of recipe " + name));
		} else {
			record = get(name, version);
		}
		if (!RecipeRecord.ACTIVE.equals(record.getStatus())) {
			throw new WorkflowConflictException("Recipe " + name + "@" + record.getVersion() + " is retired");
		}
		return record;
	}

	public RecipeRecord retire(final String name, final String version) {
		final RecipeRecord record = get(name, version);
		record.setStatus(RecipeRecord.RETIRED);
		return repository.saveAndFlush(record);
	}

	public ChoreographerPlanCompiler.Plan compile(final String name, final String version) {
		return ChoreographerPlanCompiler.compile(recipeOf(resolveActive(name, version)));
	}

	public Recipe recipeOf(final RecipeRecord record) {
		return parsed.computeIfAbsent(record.getId(), id -> RecipeCodec.parse(record.getDocument()));
	}

	public Recipe recipeById(final long id) {
		return parsed.computeIfAbsent(id, k -> RecipeCodec.parse(repository.findById(k).orElseThrow(() -> new DataNotFoundException("Recipe not found: " + k)).getDocument()));
	}

	//-------------------------------------------------------------------------------------------------
	private RecipeValidator validator() {
		return new RecipeValidator().withCheck(catalogueCheck);
	}

	private static Recipe parse(final JsonNode document) {
		if (document == null || !document.isObject()) {
			throw new InvalidParameterException("Recipe document is missing");
		}
		try {
			return RecipeCodec.fromTree(document);
		} catch (final IllegalArgumentException ex) {
			throw new InvalidParameterException(ex.getMessage());
		}
	}
}
