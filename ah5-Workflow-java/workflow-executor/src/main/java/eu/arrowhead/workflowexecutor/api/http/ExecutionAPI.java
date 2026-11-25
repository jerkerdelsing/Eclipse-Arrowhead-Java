/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.api.http;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.exception.ForbiddenException;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflow.wire.AbortRequest;
import eu.arrowhead.workflow.wire.ExecutionState;
import eu.arrowhead.workflow.wire.ExecutorInfo;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflowexecutor.WorkflowExecutorConstants;
import eu.arrowhead.workflowexecutor.WorkflowExecutorSystemInfo;
import eu.arrowhead.workflowexecutor.runtime.StepRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;

/** workflowExecution: start and abort are accepted from the WorkflowManager (or a sysop) only. */
@RestController
@RequestMapping(EXECUTION_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class ExecutionAPI {

	@Autowired
	private StepRunner runner;

	@Autowired
	private WorkflowExecutorSystemInfo sysInfo;

	@Value(WorkflowExecutorConstants.$MANAGER_SYSTEM_WD)
	private String managerSystem;

	@Operation(summary = "Declared capabilities cap(w), runnable services and workstation (workflowExecution.service-info)")
	@PostMapping(path = EXECUTION_SERVICE_INFO_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody ExecutorInfo serviceInfo() {
		return new ExecutorInfo(sysInfo.getSystemName(), sysInfo.capabilities(), sysInfo.services(), sysInfo.workstation(), sysInfo.mode());
	}

	@Operation(summary = "Accepts a work item and runs it asynchronously; results go to workflowWorkItem.report (workflowExecution.start)")
	@PostMapping(path = EXECUTION_START_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> start(final HttpServletRequest request, @RequestBody final WorkItem item) {
		checkManager(request, HttpMethod.POST.name() + " " + EXECUTION_PATH + EXECUTION_START_PATH);
		if (item == null || item.workItemId() == null || item.step() == null) {
			throw new InvalidParameterException("Work item is incomplete");
		}
		runner.submit(item);
		return ResponseEntity.status(HttpStatus.ACCEPTED).build();
	}

	@Operation(summary = "Aborts a running work item (workflowExecution.abort)")
	@PostMapping(path = EXECUTION_ABORT_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> abort(final HttpServletRequest request, @RequestBody final AbortRequest dto) {
		checkManager(request, HttpMethod.POST.name() + " " + EXECUTION_PATH + EXECUTION_ABORT_PATH);
		runner.abort(dto);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "State of a work item on this executor (workflowExecution.get-status)")
	@GetMapping(path = EXECUTION_STATUS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody ExecutionState status(@PathVariable final String workItemId) {
		return runner.status(workItemId);
	}

	private void checkManager(final HttpServletRequest request, final String origin) {
		if (!managerSystem.equals(HttpUtilities.acquireName(request, origin)) && !HttpUtilities.isSysop(request, origin)) {
			throw new ForbiddenException("Only " + managerSystem + " may start or abort work items", origin);
		}
	}
}
