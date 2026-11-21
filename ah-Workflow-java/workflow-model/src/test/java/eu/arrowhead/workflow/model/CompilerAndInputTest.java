/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import eu.arrowhead.workflow.model.compat.ChoreographerPlanCompiler;
import eu.arrowhead.workflow.model.compat.ChoreographerPlanCompiler.Plan;
import eu.arrowhead.workflow.model.compat.ChoreographerPlanCompiler.PlanAction;

class CompilerAndInputTest {

	private static final JsonNodeFactory F = JsonNodeFactory.instance;

	@Test
	void forkliftCompilesToTheChoreographerPlanShape() {
		final Plan plan = ChoreographerPlanCompiler.compile(Examples.load(Examples.FORKLIFT));
		assertEquals("shared-tool-tightening", plan.name());
		assertEquals("provision", plan.firstActionName());
		final PlanAction provision = plan.actions().get(0);
		assertEquals("use", provision.nextActionName());
		assertEquals(List.of("reserve", "program"), provision.firstStepNames());
		assertEquals("program,reserve", provision.steps().get(3).staticParameters().get("dep"));
		assertEquals("configure", provision.steps().get(3).staticParameters().get("step"));
		assertEquals(null, plan.actions().get(2).nextActionName());
		assertTrue(plan.lossySteps().contains("move"));
		final JsonNode request = RecipeCodec.toTree(plan.toRequest());
		assertEquals(Set3.of("name", "firstActionName", "actions"), Set3.fields(request));
	}

	@Test
	void templateIsComposedFromParametersAndDependencies() {
		final Recipe recipe = Examples.load(Examples.TEMPERATURE);
		final Recipe.Step evaluate = recipe.actions().get(0).steps().get(1);
		final Map<String, JsonNode> deps = new LinkedHashMap<>();
		deps.put("read", F.objectNode().put("temperature", 21.5));
		final JsonNode input = InputComposer.compose(evaluate, F.objectNode().put("threshold", 23.0), deps);
		assertEquals(21.5, input.get("temperature").asDouble());
		assertEquals(23.0, input.get("threshold").asDouble());
		assertThrows(IllegalStateException.class, () -> InputComposer.compose(evaluate, F.objectNode(), Map.of()));
	}

	@Test
	void defaultInputCarriesParametersResultsAndParams() {
		final Recipe recipe = Examples.load(Examples.FORKLIFT);
		final Recipe.Step move = recipe.actions().get(0).steps().get(2);
		final JsonNode input = InputComposer.compose(move, F.objectNode().put("line", "L2"), Map.of("reserve", F.objectNode().put("leaseToken", "t1")));
		assertEquals("L2", input.at("/parameters/line").asText());
		assertEquals("t1", input.at("/results/reserve/leaseToken").asText());
		assertEquals("station", input.at("/params/to").asText());
	}

	private static final class Set3 {
		static java.util.Set<String> of(final String... names) {
			return new java.util.TreeSet<>(List.of(names));
		}

		static java.util.Set<String> fields(final JsonNode node) {
			final java.util.Set<String> result = new java.util.TreeSet<>();
			node.fieldNames().forEachRemaining(result::add);
			return result;
		}
	}
}
