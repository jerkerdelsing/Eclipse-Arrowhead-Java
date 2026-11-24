/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import eu.arrowhead.workflow.model.InputComposer;
import eu.arrowhead.workflow.model.Recipe;
import eu.arrowhead.workflow.model.Recipe.Action;
import eu.arrowhead.workflow.model.Recipe.LeasePolicy.LeaseMode;
import eu.arrowhead.workflow.model.Recipe.Step;
import eu.arrowhead.workflow.model.RecipeGraph;

/**
 * Static recipe validation. Built-in checks:
 * <ul>
 * <li>structural well-formedness (W1)–(W4) of the Sensors paper, plus unique names;</li>
 * <li>model scope: qnty(s) = 1 (repeated execution is outside the proven model);</li>
 * <li>data coverage: input template references and {@code requiresResults} within dep(s) (intent V9);</li>
 * <li>leases: reserve ≺ use ≺ release (premise of Proposition 1), use depends on reserve (intent V8), lease longer
 * than the nominal duration of every tool step (intent V10);</li>
 * <li>guarded transitions (x-guard): each guard belongs to a transition in next, parses, and reads only results of the
 * source step or steps that precede it, and declared parameters. Guards are outside the proven model (G04 notes it).</li>
 * </ul>
 * Further checks (catalogue, authorisation dry run, domain plug-ins) are added with {@link #withCheck(RecipeCheck)}.
 */
public final class RecipeValidator {

	private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,62}$");
	private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

	private final List<RecipeCheck> checks = new ArrayList<>();

	public RecipeValidator() {
		checks.add(RecipeValidator::structure);
		checks.add(RecipeValidator::modelScope);
		checks.add(RecipeValidator::dataDependencies);
		checks.add(RecipeValidator::leases);
		checks.add(RecipeValidator::guards);
	}

	public RecipeValidator withCheck(final RecipeCheck check) {
		checks.add(check);
		return this;
	}

	public ValidationReport validate(final Recipe recipe) {
		final RecipeGraph graph = new RecipeGraph(recipe);
		final List<Finding> findings = new ArrayList<>();
		for (final RecipeCheck check : checks) {
			check.check(graph, findings);
		}
		return ValidationReport.of(recipe.key(), findings);
	}

	//-------------------------------------------------------------------------------------------------
	// W1-W3 and naming
	static void structure(final RecipeGraph g, final List<Finding> f) {
		final Recipe r = g.recipe();
		if (r.name() == null || !IDENTIFIER.matcher(r.name()).matches()) {
			f.add(new Finding("S00", Verdict.REVISE, "schema", "recipe", "Recipe name is missing or not an identifier"));
		}
		if (r.version() == null || !VERSION.matcher(r.version()).matches()) {
			f.add(new Finding("S00", Verdict.REVISE, "schema", "recipe", "Recipe version must be MAJOR.MINOR.PATCH"));
		}
		if (r.actions().isEmpty()) {
			f.add(new Finding("S08", Verdict.REVISE, "W1", "recipe", "Recipe has no actions"));
			return;
		}
		final Set<String> actionNames = new HashSet<>();
		final Set<String> stepNames = new HashSet<>();
		for (final Action a : r.actions()) {
			if (a.name() == null || !IDENTIFIER.matcher(a.name()).matches()) {
				f.add(new Finding("S00", Verdict.REVISE, "schema", "action " + a.name(), "Action name is missing or not an identifier"));
			}
			if (!actionNames.add(a.name())) {
				f.add(new Finding("S01", Verdict.REVISE, "W1", "action " + a.name(), "Duplicate action name"));
			}
			if (a.steps().isEmpty()) {
				f.add(new Finding("S08", Verdict.REVISE, "W2", "action " + a.name(), "Action has no steps"));
			}
			if (a.next() != null && !g.actions().containsKey(a.next())) {
				f.add(new Finding("S04", Verdict.REVISE, "W1", "action " + a.name(), "next action '" + a.next() + "' does not exist"));
			}
			for (final Step s : a.steps()) {
				if (s.name() == null || !IDENTIFIER.matcher(s.name()).matches()) {
					f.add(new Finding("S00", Verdict.REVISE, "schema", "step " + s.name(), "Step name is missing or not an identifier"));
				}
				if (!stepNames.add(s.name())) {
					f.add(new Finding("S02", Verdict.REVISE, "W2", "step " + s.name(), "Duplicate step name (step sets must be disjoint)"));
				}
				if (s.service() == null || s.service().serviceDefinition() == null || s.service().serviceDefinition().isBlank()) {
					f.add(new Finding("S09", Verdict.REVISE, "σ(s)", "step " + s.name(), "Step has no service definition θ(s)"));
				}
				for (final String successor : s.next()) {
					if (!a.name().equals(g.actionOf(successor))) {
						f.add(new Finding("S06", Verdict.REVISE, "W3", "step " + s.name(),
								"next step '" + successor + "' is unknown or belongs to another action"));
					}
				}
			}
			if (!g.isStepGraphAcyclic(a.name())) {
				f.add(new Finding("S07", Verdict.REVISE, "W2", "action " + a.name(), "Step graph is cyclic"));
			}
		}
		if (r.firstAction() == null || !g.actions().containsKey(r.firstAction())) {
			f.add(new Finding("S03", Verdict.REVISE, "W1", "recipe", "firstAction '" + r.firstAction() + "' does not exist"));
			return;
		}
		final List<String> path = g.actionPath();
		final String last = path.get(path.size() - 1);
		if (g.action(last).next() != null && g.actions().containsKey(g.action(last).next())) {
			f.add(new Finding("S05", Verdict.REVISE, "W1", "action " + last, "Action graph is cyclic"));
		}
		for (final String a : g.actions().keySet()) {
			if (!path.contains(a)) {
				f.add(new Finding("S05", Verdict.REVISE, "W1", "action " + a, "Action is not reachable from the initial action"));
			}
		}
		final Set<String> parameterNames = new HashSet<>();
		for (final Recipe.Parameter p : r.parameters()) {
			if (!parameterNames.add(p.name())) {
				f.add(new Finding("P01", Verdict.REVISE, "schema", "parameter " + p.name(), "Duplicate parameter"));
			}
		}
	}

	//-------------------------------------------------------------------------------------------------
	static void modelScope(final RecipeGraph g, final List<Finding> f) {
		for (final Step s : g.steps().values()) {
			if (s.policyOrDefault().quantityOrOne() != 1) {
				f.add(new Finding("M01", Verdict.REVISE, "§5.9 qnty = 1", "step " + s.name(),
						"qnty(s) > 1 is outside the proven model and not supported; repeat the step explicitly or start several sessions"));
			}
			if (s.executor() != null && !s.requiresExecutorCapability() && (s.executor().workstation() != null || s.executor().system() != null)) {
				f.add(new Finding("M02", Verdict.REVISE, "η(s)", "step " + s.name(), "Executor narrowing needs a capability"));
			}
		}
	}

	//-------------------------------------------------------------------------------------------------
	// W4, input template coverage, intent V9
	static void dataDependencies(final RecipeGraph g, final List<Finding> f) {
		final Set<String> declaredParameters = new HashSet<>();
		g.recipe().parameters().forEach(p -> declaredParameters.add(p.name()));
		for (final Step s : g.steps().values()) {
			for (final String p : s.dep()) {
				if (g.step(p) == null) {
					f.add(new Finding("D01", Verdict.REVISE, "W4", "step " + s.name(), "dep refers to unknown step '" + p + "'"));
				} else if (!g.precedes(p, s.name())) {
					f.add(new Finding("D02", Verdict.REVISE, "W4", "step " + s.name(), "dep '" + p + "' does not precede the step (p ≺ s required)"));
				}
			}
			if (s.input() != null && !s.input().isNull()) {
				for (final String referenced : InputComposer.referencedSteps(s.input())) {
					if (!s.dep().contains(referenced)) {
						f.add(new Finding("D03", Verdict.REVISE, "§4.8 κ_s", "step " + s.name(), "input refers to result '" + referenced + "' that is not in dep(s)"));
					}
				}
				for (final String parameter : InputComposer.referencedParameters(s.input())) {
					if (!declaredParameters.contains(parameter)) {
						f.add(new Finding("D05", Verdict.CLARIFY, "§4.8 ρ", "step " + s.name(), "input refers to undeclared session parameter '" + parameter + "'"));
					}
				}
			}
			if (s.service() != null) {
				for (final String required : s.service().requiresResults()) {
					final boolean covered = s.dep().stream().map(g::step).anyMatch(d -> d != null && required.equals(d.serviceDefinition()));
					if (!covered) {
						f.add(new Finding("D04", Verdict.REVISE, "intent V9", "step " + s.name(),
								"input needs the result of service '" + required + "', but no step in dep(s) provides it"));
					}
				}
			}
		}
	}

	//-------------------------------------------------------------------------------------------------
	// Proposition 1 premise, intent V7, V8, V10
	static void leases(final RecipeGraph g, final List<Finding> f) {
		final Map<String, Recipe.Resource> resources = new java.util.HashMap<>();
		g.recipe().resources().forEach(r -> resources.put(r.name(), r));
		for (final Step s : g.steps().values()) {
			final Recipe.LeasePolicy lease = s.policyOrDefault().lease();
			if (lease == null) {
				continue;
			}
			final Recipe.Resource resource = resources.get(lease.resource());
			if (resource == null) {
				f.add(new Finding("L01", Verdict.REVISE, "resources R", "step " + s.name(), "Lease resource '" + lease.resource() + "' is not declared"));
				continue;
			}
			if (lease.mode() == LeaseMode.reserve) {
				continue;
			}
			final List<Step> reserves = g.steps().values().stream()
					.filter(r -> r.policyOrDefault().lease() != null && r.policyOrDefault().lease().mode() == LeaseMode.reserve
							&& lease.resource().equals(r.policyOrDefault().lease().resource()) && g.precedes(r.name(), s.name()))
					.toList();
			if (reserves.isEmpty()) {
				f.add(new Finding("L02", Verdict.REVISE, "Prop. 1 / intent V7", "step " + s.name(), "No reserve step of '" + lease.resource() + "' precedes this step"));
			} else if (reserves.stream().noneMatch(r -> s.dep().contains(r.name()))) {
				f.add(new Finding("L03", Verdict.REVISE, "intent V8", "step " + s.name(), "Lease-using step must depend on its reserve step to read the lease token"));
			}
			if (lease.mode() == LeaseMode.use) {
				final Double nominal = s.policyOrDefault().nominalDurationSeconds();
				if (resource.leaseDurationSeconds() == null) {
					f.add(new Finding("L04", Verdict.CLARIFY, "intent V8", "resource " + resource.name(), "Lease duration is not declared"));
				} else if (nominal != null && resource.leaseDurationSeconds() <= nominal) {
					f.add(new Finding("L04", Verdict.CLARIFY, "intent V10", "step " + s.name(),
							"Lease of " + resource.leaseDurationSeconds() + " s does not exceed the nominal step duration of " + nominal + " s"));
				}
				final boolean releasedAfter = g.steps().values().stream()
						.anyMatch(r -> r.policyOrDefault().lease() != null && r.policyOrDefault().lease().mode() == LeaseMode.release
								&& lease.resource().equals(r.policyOrDefault().lease().resource()) && g.precedes(s.name(), r.name()));
				if (!releasedAfter) {
					f.add(new Finding("L05", Verdict.REVISE, "Prop. 1 / intent V7", "step " + s.name(), "No release step of '" + lease.resource() + "' follows this tool use"));
				}
			}
		}
	}

	//-------------------------------------------------------------------------------------------------
	// x-guard
	static void guards(final RecipeGraph g, final List<Finding> f) {
		final Set<String> declaredParameters = new HashSet<>();
		g.recipe().parameters().forEach(p -> declaredParameters.add(p.name()));
		boolean any = false;
		for (final Step s : g.steps().values()) {
			for (final Map.Entry<String, String> e : s.guardsOrEmpty().entrySet()) {
				any = true;
				final String where = "transition " + s.name() + " → " + e.getKey();
				if (!s.next().contains(e.getKey())) {
					f.add(new Finding("G01", Verdict.REVISE, "x-guard", where, "Guard for '" + e.getKey() + "', which is not in next(" + s.name() + ")"));
					continue;
				}
				final eu.arrowhead.workflow.model.GuardExpression guard;
				try {
					guard = eu.arrowhead.workflow.model.GuardExpression.parse(e.getValue());
				} catch (final IllegalArgumentException ex) {
					f.add(new Finding("G02", Verdict.REVISE, "x-guard", where, "Condition does not parse: " + ex.getMessage()));
					continue;
				}
				for (final String step : guard.referencedSteps()) {
					if (g.step(step) == null) {
						f.add(new Finding("G03", Verdict.REVISE, "x-guard", where, "Condition reads the result of unknown step '" + step + "'"));
					} else if (!step.equals(s.name()) && !g.precedes(step, s.name())) {
						f.add(new Finding("G03", Verdict.REVISE, "x-guard", where,
								"Condition reads the result of '" + step + "', which does not precede " + s.name() + " (its result may not exist yet)"));
					}
				}
				for (final String parameter : guard.referencedParameters()) {
					if (!declaredParameters.contains(parameter)) {
						f.add(new Finding("G05", Verdict.CLARIFY, "x-guard", where, "Condition reads undeclared session parameter '" + parameter + "'"));
					}
				}
			}
		}
		if (any) {
			f.add(new Finding("G04", Verdict.ACCEPT, "x-guard", "recipe",
					"Guarded transitions are outside the proven model: a session that skips a step is not covered by Theorems 1-2"));
		}
	}
}
