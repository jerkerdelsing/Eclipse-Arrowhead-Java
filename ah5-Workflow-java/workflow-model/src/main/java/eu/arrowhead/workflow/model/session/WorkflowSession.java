/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.workflow.model.Recipe;
import eu.arrowhead.workflow.model.Recipe.Action;
import eu.arrowhead.workflow.model.Recipe.Step;
import eu.arrowhead.workflow.model.RecipeGraph;
import eu.arrowhead.workflow.model.session.Markings.ActionMarking;
import eu.arrowhead.workflow.model.session.Markings.Rule;
import eu.arrowhead.workflow.model.session.Markings.SessionStatus;
import eu.arrowhead.workflow.model.session.Markings.StepMarking;

/**
 * Workflow session state and the transition rules of Varga, Sensors 2026, 26, 7025, §5.9.
 *
 * <p>Every model rule checks its premise and moves exactly one marking or the status; all rules require
 * status = running. The rules whose premise depends on the outside world (S-Bind: orchestration ≠ ⊥; S-Done:
 * consume ≠ fail; the failure rules) take the observed outcome as argument. {@link #advance()} applies the rules whose
 * premise is internal (A-Start, S-Enable, A-Finish, Z-Close) until none applies.
 *
 * <p>Extensions outside the proven model (x-rebind, x-abort, x-timeout, x-skip) are recorded with their own labels and listed
 * in {@link #extensions()}; Theorems 1 and 2 apply only to sessions without extensions.
 *
 * <p>Guarded transitions (x-guard): a transition p → s is <em>taken</em> when M(p) = completed and its condition holds over
 * ρ and ξ, and <em>dead</em> when M(p) = skipped or the condition is false. Once every p ∈ pre(s) is resolved, s is enabled if
 * at least one incoming transition is taken, and skipped (x-skip) if all are dead (dead-path elimination). Without guards
 * every transition is taken, so S-Enable keeps its premise ∀p ∈ pre(s): M(p) = completed and the run is a run of §5.9.
 * A session in which a step was skipped carries the extension "guard".
 *
 * <p>Not thread-safe; the WorkflowManager serialises access per session.
 */
public final class WorkflowSession {

	private final RecipeGraph graph;
	private final Map<String, StepMarking> m = new LinkedHashMap<>();
	private final Map<String, ActionMarking> ma = new LinkedHashMap<>();
	private final Map<String, JsonNode> beta = new LinkedHashMap<>();
	private final Map<String, String> eps = new LinkedHashMap<>();
	private final Map<String, String> runner = new LinkedHashMap<>();
	private final Map<String, JsonNode> xi = new LinkedHashMap<>();
	private final Set<String> extensions = new LinkedHashSet<>();
	private final List<Transition> trace = new ArrayList<>();
	// guard values once decided (ρ and ξ(p) do not change after p completed); not part of the snapshot, recomputed after restore
	private final Map<String, Boolean> guardDecisions = new java.util.HashMap<>();
	private SessionStatus status;
	private String failure;

	private WorkflowSession(final RecipeGraph graph) {
		this.graph = graph;
	}

	/** Initial state z0: every step inactive, α0 active, status running. */
	public static WorkflowSession start(final Recipe recipe) {
		final WorkflowSession z = new WorkflowSession(new RecipeGraph(recipe));
		for (final Action a : recipe.actions()) {
			z.ma.put(a.name(), ActionMarking.inactive);
			for (final Step s : a.steps()) {
				z.m.put(s.name(), StepMarking.inactive);
			}
		}
		z.ma.put(recipe.firstAction(), ActionMarking.active);
		z.status = SessionStatus.running;
		return z;
	}

	public static WorkflowSession restore(final Recipe recipe, final SessionSnapshot snapshot) {
		final WorkflowSession z = new WorkflowSession(new RecipeGraph(recipe));
		z.m.putAll(snapshot.stepMarking());
		z.ma.putAll(snapshot.actionMarking());
		z.beta.putAll(snapshot.binding());
		z.eps.putAll(snapshot.executorAssignment());
		z.runner.putAll(snapshot.runner());
		z.xi.putAll(snapshot.results());
		z.extensions.addAll(snapshot.extensions());
		z.trace.addAll(snapshot.trace());
		z.status = snapshot.status();
		z.failure = snapshot.failure();
		return z;
	}

	public SessionSnapshot snapshot() {
		return new SessionSnapshot(new LinkedHashMap<>(m), new LinkedHashMap<>(ma), new LinkedHashMap<>(beta), new LinkedHashMap<>(eps),
				new LinkedHashMap<>(runner), new LinkedHashMap<>(xi), status, failure, new LinkedHashSet<>(extensions), new ArrayList<>(trace));
	}

	//=================================================================================================
	// model rules

	/** A-Start: M_A(α) = inactive, (α', α) ∈ next_A, M_A(α') = completed. */
	public void startAction(final String action) {
		requireRunning();
		final String previous = graph.previousAction(action);
		premise(ma.get(action) == ActionMarking.inactive && previous != null && ma.get(previous) == ActionMarking.completed, Rule.A_START, action);
		ma.put(action, ActionMarking.active);
		record(Rule.A_START, action, null);
	}

	/**
	 * S-Enable: M_A(α(s)) = active, M(s) = inactive, ∀p ∈ pre(s): M(p) = completed. With guards: every p ∈ pre(s) is
	 * resolved and at least one incoming transition is taken.
	 */
	public void enable(final String step) {
		enable(step, null);
	}

	public void enable(final String step, final JsonNode parameters) {
		requireRunning();
		premise(m.get(step) == StepMarking.inactive && ma.get(graph.actionOf(step)) == ActionMarking.active && enablable(step, parameters), Rule.S_ENABLE, step);
		m.put(step, StepMarking.enabled);
		record(Rule.S_ENABLE, step, null);
	}

	/**
	 * S-Bind: M(s) = enabled, z' ∈ asg(z, s), π = O(c_z'(s), compile(s, z'), γ, t) ≠ ⊥.
	 *
	 * @param executor the executor that ran the orchestration as consumer; recorded in ε only when η(s) is defined
	 *                 (validity (V1)), and as the runner in any case
	 * @param binding  π, the orchestration result
	 */
	public void bind(final String step, final String executor, final JsonNode binding) {
		requireRunning();
		premise(m.get(step) == StepMarking.enabled && binding != null && !binding.isNull(), Rule.S_BIND, step);
		final Step s = graph.step(step);
		if (s.requiresExecutorCapability()) {
			if (executor == null) {
				throw new RuleViolationException("S-Bind(" + step + "): η(s) is defined, an executor must be assigned (V3)");
			}
			eps.put(step, executor);
		}
		if (executor != null) {
			runner.put(step, executor);
		}
		beta.put(step, binding);
		m.put(step, StepMarking.running);
		record(Rule.S_BIND, step, executor);
	}

	/** S-Done: M(s) = running, consume(β(s), input(s, z)) = d' ≠ fail. */
	public void done(final String step, final JsonNode result) {
		requireRunning();
		premise(m.get(step) == StepMarking.running && beta.containsKey(step), Rule.S_DONE, step);
		m.put(step, StepMarking.completed);
		xi.put(step, result == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : result);
		record(Rule.S_DONE, step, null);
	}

	/** A-Finish: M_A(α) = active, ∀s ∈ S_α: M(s) = completed. */
	public void finishAction(final String action) {
		requireRunning();
		premise(ma.get(action) == ActionMarking.active
				&& graph.action(action).steps().stream().allMatch(s -> m.get(s.name()).isResolved()), Rule.A_FINISH, action);
		ma.put(action, ActionMarking.completed);
		record(Rule.A_FINISH, action, null);
	}

	/** Z-Close: ∀α ∈ A: M_A(α) = completed. */
	public void close() {
		requireRunning();
		premise(ma.values().stream().allMatch(v -> v == ActionMarking.completed), Rule.Z_CLOSE, null);
		status = SessionStatus.completed;
		record(Rule.Z_CLOSE, null, null);
	}

	/** S-FailBind: M(s) = enabled and orchestration gave ⊥ for every executor in asg(z, s) (vacuous if asg = ∅). */
	public void failBind(final String step, final String reason) {
		requireRunning();
		premise(m.get(step) == StepMarking.enabled, Rule.S_FAIL_BIND, step);
		status = SessionStatus.failed;
		failure = reason;
		record(Rule.S_FAIL_BIND, step, reason);
	}

	/** S-FailInvoke: M(s) = running, consume(β(s), input(s, z)) = fail. */
	public void failInvoke(final String step, final String reason) {
		requireRunning();
		premise(m.get(step) == StepMarking.running, Rule.S_FAIL_INVOKE, step);
		status = SessionStatus.failed;
		failure = reason;
		record(Rule.S_FAIL_INVOKE, step, reason);
	}

	/**
	 * Applies the internally decided rules (Z-Close, A-Finish, A-Start, S-Enable, and x-skip) until none applies.
	 *
	 * @return the steps enabled by this call, in recipe order
	 */
	public List<String> advance() {
		return advance(null);
	}

	/** As {@link #advance()}; {@code parameters} is ρ, which guards may read. */
	public List<String> advance(final JsonNode parameters) {
		final List<String> enabled = new ArrayList<>();
		boolean progress = true;
		while (progress && status == SessionStatus.running) {
			progress = false;
			if (ma.values().stream().allMatch(v -> v == ActionMarking.completed)) {
				close();
				break;
			}
			for (final String a : graph.actionPath()) {
				final ActionMarking marking = ma.get(a);
				if (marking == ActionMarking.active && graph.action(a).steps().stream().allMatch(s -> m.get(s.name()).isResolved())) {
					finishAction(a);
					progress = true;
					break;
				}
				if (marking == ActionMarking.inactive && graph.previousAction(a) != null && ma.get(graph.previousAction(a)) == ActionMarking.completed) {
					startAction(a);
					progress = true;
					break;
				}
				if (marking == ActionMarking.active) {
					for (final Step s : graph.action(a).steps()) {
						if (m.get(s.name()) != StepMarking.inactive || !graph.pre(s.name()).stream().allMatch(p -> m.get(p).isResolved())) {
							continue;
						}
						if (enablable(s.name(), parameters)) {
							enable(s.name(), parameters);
							enabled.add(s.name());
						} else {
							skip(s.name(), parameters);
						}
						progress = true;
					}
				}
			}
		}
		return enabled;
	}

	//=================================================================================================
	// extensions outside the proven model (§6.6)

	/** x-rebind: retry a step with another executor after ERROR. Decreases a marking, so Lemma 1(i) no longer holds. */
	public void rebind(final String step, final String reason) {
		requireRunning();
		premise(m.get(step) == StepMarking.running || m.get(step) == StepMarking.enabled, Rule.X_REBIND, step);
		m.put(step, StepMarking.enabled);
		beta.remove(step);
		eps.remove(step);
		runner.remove(step);
		extensions.add("retry");
		record(Rule.X_REBIND, step, reason);
	}

	/**
	 * x-retry: after ERROR, the step is enabled again for another attempt on the same executor (after a backoff). Same
	 * marking change as x-rebind, so Lemma 1(i) no longer holds either; the trace tells the two apart.
	 */
	public void retry(final String step, final String reason) {
		requireRunning();
		premise(m.get(step) == StepMarking.running || m.get(step) == StepMarking.enabled, Rule.X_RETRY, step);
		m.put(step, StepMarking.enabled);
		beta.remove(step);
		eps.remove(step);
		runner.remove(step);
		extensions.add("retry");
		record(Rule.X_RETRY, step, reason);
	}

	/** x-abort: external abort of a running session. */
	public void abort(final String reason) {
		requireRunning();
		status = SessionStatus.failed;
		failure = reason;
		extensions.add("abort");
		record(Rule.X_ABORT, null, reason);
	}

	/** x-timeout: a pending call exceeded the step timeout and is treated as fail (timing rule, not in the model). */
	public void timeout(final String step, final String reason) {
		requireRunning();
		premise(m.get(step) == StepMarking.running || m.get(step) == StepMarking.enabled, Rule.X_TIMEOUT, step);
		status = SessionStatus.failed;
		failure = reason;
		extensions.add("timeout");
		record(Rule.X_TIMEOUT, step, reason);
	}

	/** x-skip: every p ∈ pre(s) is resolved and no incoming transition is taken (dead-path elimination). */
	public void skip(final String step, final JsonNode parameters) {
		requireRunning();
		premise(m.get(step) == StepMarking.inactive && ma.get(graph.actionOf(step)) == ActionMarking.active && !graph.pre(step).isEmpty()
				&& graph.pre(step).stream().allMatch(p -> m.get(p).isResolved()) && graph.pre(step).stream().noneMatch(p -> taken(p, step, parameters)),
				Rule.X_SKIP, step);
		m.put(step, StepMarking.skipped);
		extensions.add("guard");
		record(Rule.X_SKIP, step, graph.pre(step).stream().map(p -> m.get(p) == StepMarking.skipped ? p + " skipped" : p + ": " + graph.step(p).guard(step) + " is false")
				.collect(java.util.stream.Collectors.joining("; ")));
	}

	public void markExtension(final String extension) {
		extensions.add(extension);
	}

	//=================================================================================================
	// queries

	/**
	 * input(s, z) restricted data: ξ|dep(s). Defined iff every p ∈ dep(s) has a result (Lemma 2). A skipped dependency
	 * (x-guard) contributes null.
	 */
	public Map<String, JsonNode> dependencyResults(final String step) {
		final Map<String, JsonNode> result = new LinkedHashMap<>();
		for (final String p : graph.step(step).dep()) {
			if (m.get(p) == StepMarking.skipped) {
				result.put(p, com.fasterxml.jackson.databind.node.NullNode.getInstance());
				continue;
			}
			final JsonNode value = xi.get(p);
			if (value == null) {
				throw new RuleViolationException("input(" + step + ") undefined: ξ(" + p + ") missing (Lemma 2 violated)");
			}
			result.put(p, value);
		}
		return result;
	}

	public List<String> stepsWithMarking(final StepMarking marking) {
		return m.entrySet().stream().filter(e -> e.getValue() == marking).map(Map.Entry::getKey).toList();
	}

	public StepMarking marking(final String step) {
		return m.get(step);
	}

	public ActionMarking actionMarking(final String action) {
		return ma.get(action);
	}

	public SessionStatus status() {
		return status;
	}

	public boolean isRunning() {
		return status == SessionStatus.running;
	}

	public String failure() {
		return failure;
	}

	public JsonNode result(final String step) {
		return xi.get(step);
	}

	public Map<String, JsonNode> results() {
		return Collections.unmodifiableMap(xi);
	}

	public String executor(final String step) {
		return eps.get(step);
	}

	public String runner(final String step) {
		return runner.get(step);
	}

	public JsonNode binding(final String step) {
		return beta.get(step);
	}

	public Set<String> extensions() {
		return Collections.unmodifiableSet(extensions);
	}

	public List<Transition> trace() {
		return Collections.unmodifiableList(trace);
	}

	/** Number of transitions by model rules (Theorem 1 counts these). */
	public long modelTransitionCount() {
		return trace.stream().filter(t -> t.rule().isModelRule()).count();
	}

	public RecipeGraph graph() {
		return graph;
	}

	//=================================================================================================
	// assistant methods

	/** ∀p ∈ pre(s) resolved, and pre(s) = ∅ or some transition p → s is taken. Without guards: ∀p ∈ pre(s): M(p) = completed. */
	private boolean enablable(final String step, final JsonNode parameters) {
		final Set<String> pre = graph.pre(step);
		if (!pre.stream().allMatch(p -> m.get(p).isResolved())) {
			return false;
		}
		return pre.isEmpty() || pre.stream().anyMatch(p -> taken(p, step, parameters));
	}

	/** The transition p → s is taken: M(p) = completed and its guard (if any) holds over ρ and ξ. */
	private boolean taken(final String p, final String step, final JsonNode parameters) {
		if (m.get(p) != StepMarking.completed) {
			return false;
		}
		final String guard = graph.step(p).guard(step);
		if (guard == null) {
			return true;
		}
		final Boolean cached = guardDecisions.get(p + "->" + step);
		if (cached != null) {
			return cached;
		}
		final boolean value = eu.arrowhead.workflow.model.GuardExpression.parse(guard).evaluate(parameters, xi);
		guardDecisions.put(p + "->" + step, value);
		return value;
	}

	private void requireRunning() {
		if (status != SessionStatus.running) {
			throw new RuleViolationException("No rule applies: session status is " + status);
		}
	}

	private static void premise(final boolean holds, final Rule rule, final String target) {
		if (!holds) {
			throw new RuleViolationException("Premise of " + rule.label() + (target == null ? "" : "(" + target + ")") + " does not hold");
		}
	}

	private void record(final Rule rule, final String target, final String detail) {
		trace.add(new Transition(trace.size() + 1, rule, target, detail, Instant.now().toString()));
	}
}
