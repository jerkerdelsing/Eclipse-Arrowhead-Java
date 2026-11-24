/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.arrowhead.workflow.model.InputComposer;
import eu.arrowhead.workflow.model.Recipe;
import eu.arrowhead.workflow.model.Recipe.LeasePolicy.LeaseMode;
import eu.arrowhead.workflow.wire.AbortRequest;
import eu.arrowhead.workflow.wire.ExecutionState;
import eu.arrowhead.workflow.wire.LeaseRequest;
import eu.arrowhead.workflow.wire.LeaseResponse;
import eu.arrowhead.workflow.wire.LeaseStatus;
import eu.arrowhead.workflow.wire.ReportStatus;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflow.wire.WorkItemReport;
import eu.arrowhead.workflow.wire.WorkflowServices;
import eu.arrowhead.workflowexecutor.WorkflowExecutorConstants;
import eu.arrowhead.workflowexecutor.adapter.Binding;
import eu.arrowhead.workflowexecutor.adapter.EquipmentAdapter;
import eu.arrowhead.workflowexecutor.adapter.InvocationException;

/**
 * Runs one work item: the execution primitive compile → O_i → consume of the Sensors paper (§5.8), with this executor as
 * consumer.
 * <ol>
 * <li>lease reserve: wait for the grant (a pending call keeps the step running);</li>
 * <li>lease use: renew with the token from ξ(s_res) before the physical use, fail without a valid lease;</li>
 * <li>orchestrate θ(s) as requester → BOUND (S-Bind) or BIND_FAILED (⊥);</li>
 * <li>compose the input κ_s(ρ, ξ|dep(s)) and invoke through the first adapter that supports the binding;</li>
 * <li>lease release; report SUCCESS with d' (S-Done), or ERROR / FATAL_ERROR (S-FailInvoke).</li>
 * </ol>
 * A step whose service is {@code workflowLease} is executed against the WorkflowManager lease service only.
 */
@Component
public class StepRunner {

	public static final String LEASE_TOKEN = "leaseToken";
	public static final String LEASE_RESOURCE = "leaseResource";
	public static final String LEASE_UNTIL = "leaseUntil";

	private static final JsonNodeFactory F = JsonNodeFactory.instance;

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private ManagerClient manager;

	@Autowired
	private ProviderResolver resolver;

	@Autowired
	private List<EquipmentAdapter> adapters;

	@Autowired
	@Qualifier(WorkflowExecutorConstants.WORKER)
	private ExecutorService worker;

	@Value(WorkflowExecutorConstants.$LEASE_POLL_MS_WD)
	private long leasePollMs;

	private final Map<String, Execution> executions = new ConcurrentHashMap<>();

	private static final class Execution {
		private volatile String state = "ACCEPTED";
		private volatile String message;
		private volatile Future<?> future;
	}

	/** Thrown inside a run to end it with a report. */
	private static final class StepFailure extends Exception {
		private static final long serialVersionUID = 1L;
		private final ReportStatus status;

		StepFailure(final ReportStatus status, final String message) {
			super(message);
			this.status = status;
		}
	}

	public void submit(final WorkItem item) {
		final Execution execution = new Execution();
		if (executions.putIfAbsent(item.workItemId(), execution) != null) {
			return; // duplicate start
		}
		execution.future = worker.submit(() -> run(item, execution));
	}

	public void abort(final AbortRequest request) {
		final Execution execution = executions.get(request.workItemId());
		if (execution != null && execution.future != null) {
			execution.state = "ABORTED";
			execution.message = request.reason();
			execution.future.cancel(true);
		}
	}

	public ExecutionState status(final String workItemId) {
		final Execution e = executions.get(workItemId);
		return e == null ? new ExecutionState(workItemId, "UNKNOWN", null) : new ExecutionState(workItemId, e.state, e.message);
	}

	public int activeCount() {
		return (int) executions.values().stream().filter(e -> "ACCEPTED".equals(e.state) || "RUNNING".equals(e.state)).count();
	}

	//-------------------------------------------------------------------------------------------------
	private void run(final WorkItem item, final Execution execution) {
		execution.state = "RUNNING";
		try {
			final JsonNode result = execute(item);
			report(item, ReportStatus.SUCCESS, null, result, null);
			execution.state = "DONE";
		} catch (final StepFailure ex) {
			execution.state = ex.status.name();
			execution.message = ex.getMessage();
			report(item, ex.status, null, null, ex.getMessage());
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
			execution.state = "ABORTED";
			report(item, ReportStatus.ABORTED, null, null, "aborted");
		} catch (final RuntimeException ex) {
			logger.warn("Work item {} failed unexpectedly", item.workItemId(), ex);
			execution.state = ReportStatus.FATAL_ERROR.name();
			execution.message = ex.getMessage();
			report(item, ReportStatus.FATAL_ERROR, null, null, ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
	}

	private JsonNode execute(final WorkItem item) throws StepFailure, InterruptedException {
		final Recipe.Policy policy = item.policy() == null ? Recipe.Policy.DEFAULT : item.policy();
		final Recipe.LeasePolicy lease = policy.lease();
		final Map<String, JsonNode> deps = item.dependencyResults() == null ? Map.of() : item.dependencyResults();

		// 1-2: lease handling before the call
		LeaseResponse granted = null;
		String usedToken = null;
		if (lease != null && lease.mode() == LeaseMode.reserve) {
			granted = awaitGrant(item, lease.resource());
		} else if (lease != null) {
			usedToken = leaseToken(deps, lease.resource())
					.orElseThrow(() -> new StepFailure(ReportStatus.FATAL_ERROR, "No lease token of '" + lease.resource() + "' in ξ|dep(s)"));
			if (lease.mode() == LeaseMode.use) {
				final LeaseResponse renewed = manager.renew(new LeaseRequest(null, null, null, null, usedToken));
				if (renewed == null || renewed.status() != LeaseStatus.GRANTED) {
					throw new StepFailure(ReportStatus.FATAL_ERROR, "No valid lease on '" + lease.resource() + "' (" + (renewed == null ? "no answer" : renewed.status()) + ")");
				}
			}
		}

		// 3: orchestration as requester (S-Bind), or the internal lease service
		JsonNode response;
		final boolean internal = item.service() == null || WorkflowServices.INTERNAL_LEASE_SERVICE.equals(item.service().serviceDefinition());
		if (internal) {
			bound(item, F.objectNode().put("provider", WorkflowServices.SYS_WORKFLOW_MANAGER).put("serviceDefinition", WorkflowServices.SD_LEASE));
			response = F.objectNode();
		} else {
			final Optional<Binding> binding;
			try {
				binding = resolver.resolve(item.service(), policy.isLocalCloudOnly());
			} catch (final RuntimeException ex) {
				throw new StepFailure(ReportStatus.BIND_FAILED, "Orchestration failed: " + ex.getMessage());
			}
			if (binding.isEmpty()) {
				throw new StepFailure(ReportStatus.BIND_FAILED, "No provider of '" + item.service().serviceDefinition() + "' for this executor");
			}
			bound(item, binding.get().toReport());

			// 4: input composition and invocation
			final Recipe.Step step = new Recipe.Step(item.step(), item.service(), List.of(), List.copyOf(deps.keySet()), item.executor(), item.params(), item.input(), policy);
			final JsonNode input;
			try {
				input = InputComposer.compose(step, item.parameters(), deps);
			} catch (final IllegalStateException ex) {
				throw new StepFailure(ReportStatus.FATAL_ERROR, ex.getMessage());
			}
			final EquipmentAdapter adapter = adapters.stream().filter(a -> a.supports(binding.get())).findFirst()
					.orElseThrow(() -> new StepFailure(ReportStatus.FATAL_ERROR, "No adapter for interface " + binding.get().templateName()));
			final Object operation = item.params() == null ? null : item.params().get("operation");
			try {
				response = adapter.invoke(binding.get(), operation == null ? null : String.valueOf(operation), input);
			} catch (final InvocationException ex) {
				throw new StepFailure(ex.isTransient() ? ReportStatus.ERROR : ReportStatus.FATAL_ERROR, ex.getMessage());
			}
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException();
			}
		}

		// 5: lease release after the call; the reserve step's result carries the token for later steps
		if (lease != null && lease.mode() == LeaseMode.release && usedToken != null) {
			manager.release(new LeaseRequest(null, null, null, null, usedToken));
		}
		if (granted != null) {
			final ObjectNode result = response != null && response.isObject() ? ((ObjectNode) response).deepCopy() : F.objectNode();
			if (response != null && !response.isObject() && !response.isNull()) {
				result.set("value", response);
			}
			result.put(LEASE_TOKEN, granted.token());
			result.put(LEASE_RESOURCE, lease.resource());
			result.put(LEASE_UNTIL, granted.leaseUntil());
			return result;
		}
		return response;
	}

	/** Reserve and wait until granted; the call is pending meanwhile, so the step stays running. */
	private LeaseResponse awaitGrant(final WorkItem item, final String resource) throws InterruptedException, StepFailure {
		LeaseResponse response = manager.reserve(new LeaseRequest(resource, item.sessionId(), item.leaseDurationSeconds(), null, null));
		while (response != null && response.status() == LeaseStatus.QUEUED) {
			Thread.sleep(leasePollMs);
			response = manager.poll(new LeaseRequest(null, null, null, response.ticket(), null));
		}
		if (response == null || response.status() != LeaseStatus.GRANTED) {
			throw new StepFailure(ReportStatus.FATAL_ERROR, "Lease on '" + resource + "' not granted: " + (response == null ? "no answer" : response.status()));
		}
		return response;
	}

	private static Optional<String> leaseToken(final Map<String, JsonNode> deps, final String resource) {
		return deps.values().stream()
				.filter(v -> v != null && v.hasNonNull(LEASE_TOKEN) && resource.equals(v.path(LEASE_RESOURCE).asText(resource)))
				.map(v -> v.get(LEASE_TOKEN).asText())
				.findFirst();
	}

	/** S-Bind must be accepted by the WorkflowManager before the equipment is used; otherwise the run stops. */
	private void bound(final WorkItem item, final JsonNode binding) throws InterruptedException {
		try {
			manager.report(new WorkItemReport(item.workItemId(), ReportStatus.BOUND, binding, null, null));
		} catch (final RuntimeException ex) {
			logger.warn("WorkflowManager did not accept BOUND for work item {}: {}; not invoking", item.workItemId(), ex.getMessage());
			throw new InterruptedException("binding refused");
		}
	}

	private void report(final WorkItem item, final ReportStatus status, final JsonNode binding, final JsonNode result, final String message) {
		try {
			manager.report(new WorkItemReport(item.workItemId(), status, binding, result, message));
		} catch (final RuntimeException ex) {
			logger.warn("Unable to report {} for work item {}: {}", status, item.workItemId(), ex.getMessage());
		}
	}
}
