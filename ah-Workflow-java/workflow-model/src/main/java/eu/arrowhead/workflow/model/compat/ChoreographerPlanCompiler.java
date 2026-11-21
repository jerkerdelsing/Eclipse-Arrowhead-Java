/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import eu.arrowhead.workflow.model.Recipe;
import eu.arrowhead.workflow.model.Recipe.Action;
import eu.arrowhead.workflow.model.Recipe.Step;
import eu.arrowhead.workflow.model.RecipeCodec;
import eu.arrowhead.workflow.model.RecipeGraph;

/**
 * Compiles a recipe to the plan format of the existing Choreographer (choreographerManagement create-plan,
 * {@code PlanRequestDTO} of ah5-Choreographer-java-spring 5.x), following Table 6 of the Sensors paper and §5.2 of the
 * architecture. Lossy for WorkflowManager-only fields: version, η(s), leases, retry, timeout, input template.
 */
public final class ChoreographerPlanCompiler {

	public static final String PARAM_STEP = "step";
	public static final String PARAM_DEP = "dep";

	private ChoreographerPlanCompiler() {
		throw new UnsupportedOperationException();
	}

	public static Plan compile(final Recipe recipe) {
		final RecipeGraph graph = new RecipeGraph(recipe);
		final List<PlanAction> actions = new ArrayList<>();
		final List<String> lossy = new ArrayList<>();
		for (final Action a : recipe.actions()) {
			final List<PlanStep> steps = new ArrayList<>();
			for (final Step s : a.steps()) {
				final Map<String, String> staticParameters = new LinkedHashMap<>();
				s.params().forEach((k, v) -> staticParameters.put(k, v instanceof String str ? str : RecipeCodec.write(v)));
				staticParameters.put(PARAM_STEP, s.name());
				staticParameters.put(PARAM_DEP, String.join(",", s.dep()));
				steps.add(new PlanStep(s.name(), s.policyOrDefault().quantityOrOne(), requirement(s.service()), s.policyOrDefault().isLocalCloudOnly(), staticParameters, s.next()));
				if (s.executor() != null || s.policyOrDefault().lease() != null || s.policyOrDefault().retry() != null || s.policyOrDefault().timeoutSeconds() != null
						|| s.input() != null || s.guards() != null) {
					lossy.add(s.name());
				}
			}
			actions.add(new PlanAction(a.name(), a.next(), graph.firstSteps(a.name()), steps));
		}
		return new Plan(recipe.name(), recipe.firstAction(), actions, lossy);
	}

	private static ServiceRequirement requirement(final Recipe.ServiceRequirement r) {
		if (r == null) {
			return null;
		}
		List<String> versions = null;
		if (r.minVersion() != null && r.minVersion().equals(r.maxVersion())) {
			versions = List.of(r.minVersion());
		} else if (r.minVersion() != null && r.maxVersion() == null) {
			versions = List.of(r.minVersion());
		}
		return new ServiceRequirement(r.serviceDefinition(), versions, r.interfaces().isEmpty() ? null : r.interfaces());
	}

	/** {@code PlanRequestDTO(name, firstActionName, actions)}; {@code lossySteps} is informational and not part of the DTO. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Plan(String name, String firstActionName, List<PlanAction> actions, List<String> lossySteps) {

		/** The plan without the informational field, exactly in the Choreographer request shape. */
		public Map<String, Object> toRequest() {
			final Map<String, Object> request = new LinkedHashMap<>();
			request.put("name", name);
			request.put("firstActionName", firstActionName);
			request.put("actions", actions);
			return request;
		}
	}

	/** {@code ActionRequestDTO(name, nextActionName, firstStepNames, steps)}. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PlanAction(String name, String nextActionName, List<String> firstStepNames, List<PlanStep> steps) {
	}

	/** {@code StepRequestDTO(name, quantity, serviceRequirement, localCloudOnly, staticParameters, nextStepNames)}. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PlanStep(String name, Integer quantity, ServiceRequirement serviceRequirement, Boolean localCloudOnly, Map<String, String> staticParameters,
			List<String> nextStepNames) {
	}

	/** Subset of {@code OrchestrationServiceRequirementDTO}. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ServiceRequirement(String serviceDefinition, List<String> versions, List<String> interfaceTemplateNames) {
	}
}
