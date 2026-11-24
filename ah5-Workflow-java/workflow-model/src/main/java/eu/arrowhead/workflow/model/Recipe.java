/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Workflow recipe W = (P, A, S, θ, ι, μ, q, pre, η, qnty, dep, κ) of Varga, Sensors 2026, 26, 7025, §4, in the canonical
 * JSON form of model/recipe.schema.json. A recipe is immutable: bindings, executor assignments and results belong to
 * sessions only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Recipe(
		String name,
		String version,
		String description,
		String firstAction,
		List<Parameter> parameters,
		List<Resource> resources,
		DispatchMode dispatch,
		List<Action> actions) {

	public Recipe {
		parameters = parameters == null ? List.of() : List.copyOf(parameters);
		resources = resources == null ? List.of() : List.copyOf(resources);
		actions = actions == null ? List.of() : List.copyOf(actions);
	}

	public String key() {
		return name + "@" + version;
	}

	/** How enabled steps reach executors (§7 of the architecture). */
	public enum DispatchMode {
		push, pull, event
	}

	/** Session parameter ρ declaration. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Parameter(String name, String type, Boolean required) {

		public boolean isRequired() {
			return required == null || required;
		}
	}

	/** Shared resource R that needs a lease. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Resource(String name, Double leaseDurationSeconds, String provider) {
	}

	/** Action α = (S_α, F_α, next_α); {@code next} is next_A (null for the last action). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Action(String name, String next, String workCentre, DispatchMode dispatch, List<Step> steps) {

		public Action {
			steps = steps == null ? List.of() : List.copyOf(steps);
		}
	}

	/**
	 * Step s with requirement profile σ(s), data dependency dep(s), input composition κ_s and policy extensions.
	 * {@code guards} maps a successor in next to a {@link GuardExpression}: the transition is taken only when the condition
	 * holds once s is completed (extension x-guard; a step whose incoming transitions are all dead is skipped).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Step(
			String name,
			ServiceRequirement service,
			List<String> next,
			List<String> dep,
			ExecutorRequirement executor,
			Map<String, Object> params,
			JsonNode input,
			Policy policy,
			Map<String, String> guards) {

		public Step {
			next = next == null ? List.of() : List.copyOf(next);
			dep = dep == null ? List.of() : List.copyOf(dep);
			params = params == null ? Map.of() : Map.copyOf(params);
			guards = guards == null || guards.isEmpty() ? null : Map.copyOf(guards);
		}

		/** A step without guarded transitions. */
		public Step(final String name, final ServiceRequirement service, final List<String> next, final List<String> dep, final ExecutorRequirement executor,
				final Map<String, Object> params, final JsonNode input, final Policy policy) {
			this(name, service, next, dep, executor, params, input, policy, null);
		}

		/** Condition of the transition to {@code successor} (extension x-guard); null when the transition is unconditional. */
		public String guard(final String successor) {
			return guards == null ? null : guards.get(successor);
		}

		public Map<String, String> guardsOrEmpty() {
			return guards == null ? Map.of() : guards;
		}

		/** η(s) is defined. */
		public boolean requiresExecutorCapability() {
			return executor != null && executor.capability() != null && !executor.capability().isBlank();
		}

		public Policy policyOrDefault() {
			return policy == null ? Policy.DEFAULT : policy;
		}

		public String serviceDefinition() {
			return service == null ? null : service.serviceDefinition();
		}
	}

	/** θ, ι, μ, q of a step plus the results its input needs (intent check V9). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ServiceRequirement(
			String serviceDefinition,
			List<String> interfaces,
			String minVersion,
			String maxVersion,
			Map<String, Object> metadata,
			Map<String, Object> qos,
			List<String> requiresResults) {

		public ServiceRequirement {
			interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
			requiresResults = requiresResults == null ? List.of() : List.copyOf(requiresResults);
		}
	}

	/** η(s): capability, optionally narrowed to a workstation or a named system. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ExecutorRequirement(String capability, String workstation, String system) {
	}

	/** Extensions outside the proven model (§6.6): retry, timeout, quantity > 1, lease. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Policy(
			Integer quantity,
			Double timeoutSeconds,
			Double nominalDurationSeconds,
			Retry retry,
			LeasePolicy lease,
			Boolean localCloudOnly) {

		public static final Policy DEFAULT = new Policy(null, null, null, null, null, null);

		public int quantityOrOne() {
			return quantity == null ? 1 : quantity;
		}

		public boolean isLocalCloudOnly() {
			return localCloudOnly == null || localCloudOnly;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Retry(Integer maxAttempts, OnError onError, Double backoffSeconds) {

		/** Default pause before a same-executor retry (onError = retry). */
		public static final double DEFAULT_BACKOFF_SECONDS = 1.0;

		public int maxAttemptsOrOne() {
			return maxAttempts == null ? 1 : maxAttempts;
		}

		public long backoffMillis() {
			return Math.round((backoffSeconds == null ? DEFAULT_BACKOFF_SECONDS : backoffSeconds) * 1000);
		}

		/**
		 * What ERROR (a transient failure reported by the executor) does while attempts remain:
		 * <ul>
		 * <li>rebind: exclude the executor for the step and dispatch to another one (Choreographer 5.2.2 semantics);</li>
		 * <li>retry: wait backoffSeconds, then dispatch to the same executor again (for transient provider-side failures,
		 * e.g. a single provider restarting or answering 408; the provider must accept repeated calls);</li>
		 * <li>abort: S-FailInvoke at once.</li>
		 * </ul>
		 */
		public enum OnError {
			rebind, retry, abort
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record LeasePolicy(String resource, LeaseMode mode) {

		public enum LeaseMode {
			reserve, use, release
		}
	}
}
