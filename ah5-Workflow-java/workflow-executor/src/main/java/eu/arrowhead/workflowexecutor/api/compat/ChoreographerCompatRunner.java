/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.api.compat;

import java.util.Map;
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

import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.workflow.model.RecipeCodec;
import eu.arrowhead.workflowexecutor.WorkflowExecutorConstants;
import eu.arrowhead.workflowexecutor.adapter.Binding;
import eu.arrowhead.workflowexecutor.adapter.EquipmentAdapter;
import eu.arrowhead.workflowexecutor.adapter.InvocationException;
import eu.arrowhead.workflowexecutor.api.compat.CompatDTOs.AbortStepRequest;
import eu.arrowhead.workflowexecutor.api.compat.CompatDTOs.ExecuteStepRequest;
import eu.arrowhead.workflowexecutor.api.compat.CompatDTOs.ExecutedStepResult;

/**
 * Runs a Choreographer step with the same adapters as workflow steps: the provider is the main orchestration result the
 * Choreographer obtained for this executor, the input is the step's static parameters (operation from "operation"),
 * repeated quantity times; the result is reported with choreographerExecutor.notify-step-done.
 */
@Component
public class ChoreographerCompatRunner {

	private static final String SD_CHOREOGRAPHER_EXECUTOR = "choreographerExecutor";
	private static final String OP_NOTIFY_STEP_DONE = "notify-step-done";

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private ArrowheadHttpService arrowheadHttpService;

	@Autowired
	private java.util.List<EquipmentAdapter> adapters;

	@Autowired
	@Qualifier(WorkflowExecutorConstants.WORKER)
	private ExecutorService worker;

	@Value(WorkflowExecutorConstants.$CHOREOGRAPHER_SYSTEM_WD)
	private String choreographerSystem;

	private final Map<Long, Future<?>> running = new ConcurrentHashMap<>();

	public void start(final ExecuteStepRequest request) {
		running.put(request.sessionStepId(), worker.submit(() -> run(request)));
	}

	public void abort(final AbortStepRequest request) {
		final Future<?> f = running.remove(request.sessionStepId());
		if (f != null) {
			f.cancel(true);
			notify(new ExecutedStepResult(request.sessionId(), request.sessionStepId(), "ABORTED", "aborted", null));
		}
	}

	private void run(final ExecuteStepRequest request) {
		try {
			final Binding binding = Binding.fromOrchestrationResult(request.mainOrchestrationResult());
			final EquipmentAdapter adapter = adapters.stream().filter(a -> a.supports(binding)).findFirst()
					.orElseThrow(() -> new InvocationException("No adapter for interface " + binding.templateName(), false, null));
			final Map<String, String> params = request.staticParameters() == null ? Map.of() : request.staticParameters();
			final JsonNode input = RecipeCodec.toTree(params);
			JsonNode last = null;
			for (int i = 0; i < Math.max(1, request.quantity()); i++) {
				last = adapter.invoke(binding, params.get("operation"), input);
			}
			notify(new ExecutedStepResult(request.sessionId(), request.sessionStepId(), "SUCCESS", last == null ? null : last.toString(), null));
		} catch (final InvocationException ex) {
			notify(new ExecutedStepResult(request.sessionId(), request.sessionStepId(), ex.isTransient() ? "ERROR" : "FATAL_ERROR", ex.getMessage(), null));
		} catch (final RuntimeException ex) {
			notify(new ExecutedStepResult(request.sessionId(), request.sessionStepId(), "FATAL_ERROR", ex.getMessage(), ex.getClass().getName()));
		} finally {
			running.remove(request.sessionStepId());
		}
	}

	private void notify(final ExecutedStepResult result) {
		try {
			arrowheadHttpService.consumeService(SD_CHOREOGRAPHER_EXECUTOR, OP_NOTIFY_STEP_DONE, choreographerSystem, Void.class, result);
		} catch (final RuntimeException ex) {
			logger.warn("Unable to notify the Choreographer of step {}: {}", result.sessionStepId(), ex.getMessage());
		}
	}
}
