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
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.arrowhead.workflow.model.session.Markings.Rule;
import eu.arrowhead.workflow.model.session.Markings.SessionStatus;
import eu.arrowhead.workflow.model.session.Markings.StepMarking;
import eu.arrowhead.workflow.model.session.RuleViolationException;
import eu.arrowhead.workflow.model.session.SessionSnapshot;
import eu.arrowhead.workflow.model.session.Transition;
import eu.arrowhead.workflow.model.session.WorkflowSession;

/** Conformance checks from the Sensors paper: Theorem 1 (exact length), Lemma 2, failure semantics. */
class WorkflowSessionTest {

	private static final JsonNodeFactory F = JsonNodeFactory.instance;

	/** Runs a session; {@code failing} decides which step's invocation fails. Every bind uses executor "wfe". */
	private static WorkflowSession run(final Recipe recipe, final Predicate<String> failing) {
		final WorkflowSession z = WorkflowSession.start(recipe);
		final Deque<String> ready = new ArrayDeque<>(z.advance());
		while (!ready.isEmpty() && z.isRunning()) {
			final String s = ready.poll();
			z.dependencyResults(s); // Lemma 2: defined whenever s is enabled
			z.bind(s, "wfe", F.objectNode().put("provider", "p-" + s));
			if (failing.test(s)) {
				z.failInvoke(s, "consume = fail");
				break;
			}
			final ObjectNode result = F.objectNode().put("by", s);
			z.done(s, result);
			ready.addAll(z.advance());
		}
		return z;
	}

	@Test
	void temperatureControlTakesElevenTransitions() {
		final Recipe recipe = Examples.load(Examples.TEMPERATURE);
		final WorkflowSession z = run(recipe, s -> false);
		assertEquals(SessionStatus.completed, z.status());
		assertEquals(11, z.modelTransitionCount());
		assertEquals(z.graph().transitionBound(), z.modelTransitionCount());
		assertEquals(List.of("S-Enable(read)", "S-Bind(read)", "S-Done(read)", "S-Enable(evaluate)", "S-Bind(evaluate)", "S-Done(evaluate)",
				"S-Enable(switch)", "S-Bind(switch)", "S-Done(switch)", "A-Finish(control)", "Z-Close"),
				z.trace().stream().map(Transition::label).toList());
	}

	@Test
	void brokenSwitchFailsInvokeAfterNineTransitions() {
		final WorkflowSession z = run(Examples.load(Examples.TEMPERATURE), "switch"::equals);
		assertEquals(SessionStatus.failed, z.status());
		assertEquals(9, z.modelTransitionCount());
		assertEquals(Rule.S_FAIL_INVOKE, z.trace().get(8).rule());
		// failure is absorbing
		assertThrows(RuleViolationException.class, z::close);
		assertTrue(z.advance().isEmpty());
	}

	@Test
	void forkliftTakesThirtyThreeTransitions() {
		final WorkflowSession z = run(Examples.load(Examples.FORKLIFT), s -> false);
		assertEquals(SessionStatus.completed, z.status());
		assertEquals(33, z.modelTransitionCount());
		assertEquals(33, z.graph().transitionBound());
		assertTrue(z.extensions().isEmpty());
		// η defined only for the AGV steps: ε covers exactly those (validity V1)
		assertEquals("wfe", z.executor("move"));
		assertEquals(null, z.executor("program"));
		assertEquals("wfe", z.runner("program"));
	}

	@Test
	void failedAgvMissionLeavesReleaseInactive() {
		final WorkflowSession z = run(Examples.load(Examples.FORKLIFT), "move"::equals);
		assertEquals(SessionStatus.failed, z.status());
		assertEquals(StepMarking.completed, z.marking("reserve"));
		assertEquals(StepMarking.inactive, z.marking("release"));
		assertTrue(z.modelTransitionCount() <= 33);
	}

	@Test
	void premisesAreEnforced() {
		final WorkflowSession z = WorkflowSession.start(Examples.load(Examples.FORKLIFT));
		assertThrows(RuleViolationException.class, () -> z.enable("move")); // pre(move) = {reserve} not completed
		assertThrows(RuleViolationException.class, () -> z.startAction("use")); // provision not completed
		z.advance();
		assertThrows(RuleViolationException.class, () -> z.done("reserve", F.objectNode())); // not bound yet
		assertThrows(RuleViolationException.class, () -> z.bind("move", "agv", F.objectNode())); // not enabled
		// η(move) defined: binding without an executor violates (V3)
		z.bind("reserve", "wfe", F.objectNode());
		z.done("reserve", F.objectNode().put("leaseToken", "t"));
		z.advance();
		assertThrows(RuleViolationException.class, () -> z.bind("move", null, F.objectNode()));
	}

	@Test
	void dependencyResultsAreRestrictedToDep() {
		final WorkflowSession z = run(Examples.load(Examples.FORKLIFT), s -> false);
		final var input = z.dependencyResults("evaluate");
		assertEquals(List.of("tighten", "program"), List.copyOf(input.keySet()));
	}

	@Test
	void snapshotRoundTripPreservesState() throws Exception {
		final Recipe recipe = Examples.load(Examples.FORKLIFT);
		final WorkflowSession z = WorkflowSession.start(recipe);
		z.advance();
		z.bind("reserve", "wfe", F.objectNode().put("p", 1));
		final String json = RecipeCodec.mapper().writeValueAsString(z.snapshot());
		final WorkflowSession restored = WorkflowSession.restore(recipe, RecipeCodec.mapper().readValue(json, SessionSnapshot.class));
		assertEquals(StepMarking.running, restored.marking("reserve"));
		assertEquals(z.trace().size(), restored.trace().size());
		restored.done("reserve", F.objectNode());
		assertFalse(restored.advance().isEmpty());
	}

	@Test
	void extensionsAreFlagged() {
		final WorkflowSession z = WorkflowSession.start(Examples.load(Examples.TEMPERATURE));
		z.advance();
		z.bind("read", "a", F.objectNode());
		z.rebind("read", "ERROR from a");
		z.bind("read", "b", F.objectNode());
		z.done("read", (JsonNode) F.objectNode());
		assertTrue(z.extensions().contains("retry"));
		assertEquals(Rule.X_REBIND, z.trace().get(2).rule());
		z.abort("operator");
		assertEquals(SessionStatus.failed, z.status());
	}

	@Test
	void retryEnablesTheStepAgainAndIsFlagged() {
		final WorkflowSession z = WorkflowSession.start(Examples.load(Examples.TEMPERATURE));
		z.advance();
		z.bind("read", "a", F.objectNode());
		z.retry("read", "ERROR from a");
		assertEquals(StepMarking.enabled, z.marking("read"));
		z.bind("read", "a", F.objectNode());
		z.done("read", (JsonNode) F.objectNode());
		assertEquals(Rule.X_RETRY, z.trace().get(2).rule());
		assertTrue(z.extensions().contains("retry"));
		assertFalse(Rule.X_RETRY.isModelRule());
	}

	@Test
	void retryPolicyParsesAndDefaultsTheBackoff() throws Exception {
		final Recipe.Retry plain = RecipeCodec.mapper().readValue("{\"maxAttempts\":3,\"onError\":\"retry\"}", Recipe.Retry.class);
		assertEquals(Recipe.Retry.OnError.retry, plain.onError());
		assertEquals(1000, plain.backoffMillis());
		assertEquals(2500, RecipeCodec.mapper().readValue("{\"onError\":\"retry\",\"backoffSeconds\":2.5}", Recipe.Retry.class).backoffMillis());
	}
}
