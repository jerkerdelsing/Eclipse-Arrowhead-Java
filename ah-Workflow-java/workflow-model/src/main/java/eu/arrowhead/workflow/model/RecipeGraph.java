/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.arrowhead.workflow.model.Recipe.Action;
import eu.arrowhead.workflow.model.Recipe.Step;

/**
 * Derived structure of a recipe: α(s), pre(s), F_α, the action path α0 → … → αn and the precedence relation p ≺ s.
 * Construction is tolerant: it never throws on malformed recipes, so the validator can report every problem.
 */
public final class RecipeGraph {

	private final Recipe recipe;
	private final Map<String, Action> actions = new LinkedHashMap<>();
	private final Map<String, Step> steps = new LinkedHashMap<>();
	private final Map<String, String> actionOfStep = new HashMap<>();
	private final Map<String, Set<String>> pre = new HashMap<>();
	private final List<String> actionPath = new ArrayList<>();
	private final Map<String, Integer> actionIndex = new HashMap<>();

	public RecipeGraph(final Recipe recipe) {
		this.recipe = recipe;
		for (final Action action : recipe.actions()) {
			actions.putIfAbsent(action.name(), action);
			for (final Step step : action.steps()) {
				if (steps.putIfAbsent(step.name(), step) == null) {
					actionOfStep.put(step.name(), action.name());
				}
				pre.computeIfAbsent(step.name(), k -> new LinkedHashSet<>());
			}
		}
		for (final Action action : recipe.actions()) {
			for (final Step step : action.steps()) {
				for (final String successor : step.next()) {
					if (action.name().equals(actionOfStep.get(successor))) {
						pre.computeIfAbsent(successor, k -> new LinkedHashSet<>()).add(step.name());
					}
				}
			}
		}
		// follow next_A from α0; stops at an unknown name or a repeated action (cycle)
		String current = recipe.firstAction();
		final Set<String> seen = new HashSet<>();
		while (current != null && actions.containsKey(current) && seen.add(current)) {
			actionIndex.put(current, actionPath.size());
			actionPath.add(current);
			current = actions.get(current).next();
		}
	}

	public Recipe recipe() {
		return recipe;
	}

	public Map<String, Action> actions() {
		return Collections.unmodifiableMap(actions);
	}

	public Map<String, Step> steps() {
		return Collections.unmodifiableMap(steps);
	}

	public Step step(final String name) {
		return steps.get(name);
	}

	public Action action(final String name) {
		return actions.get(name);
	}

	/** α(s). */
	public String actionOf(final String step) {
		return actionOfStep.get(step);
	}

	/** pre(s), derived from next_α. */
	public Set<String> pre(final String step) {
		return Collections.unmodifiableSet(pre.getOrDefault(step, Set.of()));
	}

	/** F_α. */
	public List<String> firstSteps(final String action) {
		final Action a = actions.get(action);
		if (a == null) {
			return List.of();
		}
		return a.steps().stream().map(Step::name).filter(s -> pre(s).isEmpty()).toList();
	}

	/** The action path α0 → α1 → … reachable from the initial action (W1). */
	public List<String> actionPath() {
		return Collections.unmodifiableList(actionPath);
	}

	/** The predecessor of an action on the path, or null for α0 / unreachable actions. */
	public String previousAction(final String action) {
		final Integer index = actionIndex.get(action);
		return index == null || index == 0 ? null : actionPath.get(index - 1);
	}

	/** pre+(s): transitive predecessors inside the same action. */
	public Set<String> transitivePre(final String step) {
		final Set<String> result = new LinkedHashSet<>();
		final Deque<String> work = new ArrayDeque<>(pre(step));
		while (!work.isEmpty()) {
			final String p = work.pop();
			if (result.add(p)) {
				work.addAll(pre(p));
			}
		}
		return result;
	}

	/** p ≺ s: p ∈ pre+(s), or α(p) is strictly before α(s) on the action path. */
	public boolean precedes(final String p, final String s) {
		final String ap = actionOfStep.get(p);
		final String as = actionOfStep.get(s);
		if (ap == null || as == null) {
			return false;
		}
		if (ap.equals(as)) {
			return transitivePre(s).contains(p);
		}
		final Integer ip = actionIndex.get(ap);
		final Integer is = actionIndex.get(as);
		return ip != null && is != null && ip < is;
	}

	/** True when the step graph of the action is acyclic (Kahn's algorithm, successors restricted to the action). */
	public boolean isStepGraphAcyclic(final String action) {
		final Action a = actions.get(action);
		if (a == null) {
			return true;
		}
		final Map<String, Integer> indegree = new HashMap<>();
		for (final Step s : a.steps()) {
			indegree.put(s.name(), pre(s.name()).size());
		}
		final Deque<String> ready = new ArrayDeque<>();
		indegree.forEach((k, v) -> {
			if (v == 0) {
				ready.add(k);
			}
		});
		int visited = 0;
		while (!ready.isEmpty()) {
			final String s = ready.pop();
			visited++;
			for (final String successor : steps.get(s).next()) {
				if (indegree.containsKey(successor) && indegree.merge(successor, -1, Integer::sum) == 0) {
					ready.add(successor);
				}
			}
		}
		return visited == indegree.size();
	}

	public int stepCount() {
		return steps.size();
	}

	public int actionCount() {
		return actions.size();
	}

	/** Theorem 1: a failure-free run that ends with Z-Close has exactly 3|S| + 2|A| transitions. */
	public int transitionBound() {
		return 3 * stepCount() + 2 * actionCount();
	}
}
