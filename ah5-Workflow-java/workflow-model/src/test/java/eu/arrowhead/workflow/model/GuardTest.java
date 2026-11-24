/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.arrowhead.workflow.model.session.Markings.Rule;
import eu.arrowhead.workflow.model.session.Markings.SessionStatus;
import eu.arrowhead.workflow.model.session.Markings.StepMarking;
import eu.arrowhead.workflow.model.session.SessionSnapshot;
import eu.arrowhead.workflow.model.session.WorkflowSession;
import eu.arrowhead.workflow.model.validation.RecipeValidator;
import eu.arrowhead.workflow.model.validation.ValidationReport;

/** Extension x-guard: the condition language, its validation, and dead-path elimination in the session. */
class GuardTest {

	private static final JsonNodeFactory F = JsonNodeFactory.instance;
	private static final String BRANCHING = "examples/temperature-branching.recipe.json";

	@Test
	void expressionLanguage() {
		final ObjectNode rho = F.objectNode().put("threshold", 23).put("mode", "auto");
		final Map<String, JsonNode> xi = Map.of("read", F.objectNode().put("temperature", 21.5).set("tags", F.arrayNode().add("a")));
		assertTrue(eval("${result.read.temperature} < ${param.threshold}", rho, xi));
		assertTrue(eval("${result.read.temperature} == 21.50", rho, xi));
		assertTrue(eval("${param.mode} == 'auto' && !(${param.threshold} >= 30)", rho, xi));
		assertTrue(eval("${result.read.tags.0} == \"a\" || false", rho, xi));
		assertTrue(eval("${result.missing.x} == null", rho, xi));
		assertTrue(eval("${result.read.temperature} > -5", rho, xi));
		assertFalse(eval("${param.mode} > 3", rho, xi)); // ordering string with number is false
		assertFalse(eval("${result.read.none}", rho, xi));
		assertTrue(eval("${result.read.tags}", rho, xi));
		final GuardExpression g = GuardExpression.parse("${result.read.temperature} < ${param.threshold}");
		assertEquals(Set.of("read"), g.referencedSteps());
		assertEquals(Set.of("threshold"), g.referencedParameters());
		for (final String bad : List.of("", "${result.read.x} <", "(true", "foo == 1", "${other.x} == 1", "1 = 1", "'open")) {
			assertThrows(IllegalArgumentException.class, () -> GuardExpression.parse(bad), bad);
		}
	}

	@Test
	void branchingRecipeValidates() {
		final ValidationReport report = new RecipeValidator().validate(Examples.load(BRANCHING));
		assertTrue(report.findings().stream().noneMatch(f -> f.outcome() != eu.arrowhead.workflow.model.validation.Verdict.ACCEPT), report.toString());
		assertTrue(report.findings().stream().anyMatch(f -> f.code().equals("G04")));
	}

	@Test
	void invalidGuardsAreReported() {
		final String json = Examples.json(BRANCHING)
				.replace("\"heatOff\": \"!${result.evaluate.heat}\"", "\"nowhere\": \"true\", \"heatOff\": \"${result.confirm.x} ==\"");
		final ValidationReport report = new RecipeValidator().validate(RecipeCodec.parse(json));
		final Set<String> codes = new java.util.HashSet<>();
		report.findings().forEach(f -> codes.add(f.code()));
		assertTrue(codes.containsAll(Set.of("G01", "G02")), report.toString());
		final String later = Examples.json(BRANCHING).replace("\"!${result.evaluate.heat}\"", "\"${result.confirm.on} == true\"");
		assertTrue(new RecipeValidator().validate(RecipeCodec.parse(later)).findings().stream().anyMatch(f -> f.code().equals("G03")));
	}

	@Test
	void heatOnBranchRunsAndHeatOffIsSkipped() {
		final WorkflowSession z = run(Examples.load(BRANCHING), F.objectNode().put("threshold", 23), true);
		assertEquals(SessionStatus.completed, z.status());
		assertEquals(StepMarking.completed, z.marking("heatOn"));
		assertEquals(StepMarking.skipped, z.marking("heatOff"));
		assertEquals(StepMarking.completed, z.marking("confirm"), "the join runs after the taken branch");
		assertTrue(z.extensions().contains("guard"));
		assertEquals(1, z.trace().stream().filter(t -> t.rule() == Rule.X_SKIP).count());
		assertTrue(z.dependencyResults("confirm").get("heatOff").isNull());
	}

	@Test
	void heatOffBranchWhenWarmEnough() {
		final WorkflowSession z = run(Examples.load(BRANCHING), F.objectNode().put("threshold", 20), false);
		assertEquals(SessionStatus.completed, z.status());
		assertEquals(StepMarking.skipped, z.marking("heatOn"));
		assertEquals(StepMarking.completed, z.marking("heatOff"));
		assertEquals(StepMarking.completed, z.marking("confirm"));
	}

	@Test
	void allDeadPathsSkipTheJoinToo() {
		final String json = Examples.json(BRANCHING).replace("\"!${result.evaluate.heat}\"", "\"false\"");
		final WorkflowSession z = run(RecipeCodec.parse(json), F.objectNode().put("threshold", 20), false);
		assertEquals(SessionStatus.completed, z.status());
		assertEquals(StepMarking.skipped, z.marking("heatOn"));
		assertEquals(StepMarking.skipped, z.marking("heatOff"));
		assertEquals(StepMarking.skipped, z.marking("confirm"), "dead paths propagate");
	}

	@Test
	void unguardedRecipesKeepTheModelRun() {
		final WorkflowSession z = run(Examples.load(Examples.TEMPERATURE), F.objectNode().put("threshold", 23), true);
		assertEquals(11, z.modelTransitionCount());
		assertTrue(z.extensions().isEmpty());
	}

	@Test
	void decisionSurvivesRestore() {
		final Recipe recipe = Examples.load(BRANCHING);
		final ObjectNode rho = F.objectNode().put("threshold", 23);
		final WorkflowSession z = WorkflowSession.start(recipe);
		z.advance(rho);
		for (final String s : List.of("read", "evaluate")) {
			z.bind(s, "wfe", F.objectNode());
			z.done(s, s.equals("evaluate") ? F.objectNode().put("heat", true) : F.objectNode().put("temperature", 21));
			if (s.equals("read")) {
				z.advance(rho);
			}
		}
		final SessionSnapshot snapshot = z.snapshot();
		final WorkflowSession restored = WorkflowSession.restore(recipe, snapshot);
		assertEquals(List.of("heatOn"), restored.advance(rho));
		assertEquals(StepMarking.skipped, restored.marking("heatOff"));
	}

	/** Runs every enabled step; evaluate returns heat = {@code heat}. */
	private static WorkflowSession run(final Recipe recipe, final ObjectNode rho, final boolean heat) {
		final WorkflowSession z = WorkflowSession.start(recipe);
		final Deque<String> ready = new ArrayDeque<>(z.advance(rho));
		while (!ready.isEmpty() && z.isRunning()) {
			final String s = ready.poll();
			z.dependencyResults(s);
			z.bind(s, "wfe", F.objectNode().put("provider", "p-" + s));
			final ObjectNode result = F.objectNode().put("by", s).put("heat", heat).put("on", s.equals("heatOn"));
			z.done(s, result);
			ready.addAll(z.advance(rho));
		}
		return z;
	}

	private static boolean eval(final String expression, final JsonNode rho, final Map<String, JsonNode> xi) {
		return GuardExpression.parse(expression).evaluate(rho, xi);
	}
}
