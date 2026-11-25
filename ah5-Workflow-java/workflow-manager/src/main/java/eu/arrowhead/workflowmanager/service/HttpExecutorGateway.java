/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;

import eu.arrowhead.common.SystemInfo;
import eu.arrowhead.common.http.HttpService;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflow.wire.AbortRequest;
import eu.arrowhead.workflow.wire.ExecutionState;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflow.wire.WorkflowServices;
import eu.arrowhead.workflowmanager.service.ExecutorDirectory.ExecutorEndpoint;

@Component
public class HttpExecutorGateway implements ExecutorGateway {

	@Autowired
	private HttpService httpService;

	@Autowired
	private SystemInfo sysInfo;

	@Override
	public void start(final ExecutorEndpoint executor, final WorkItem item) {
		send(executor, WorkflowServices.EXECUTION_START_PATH, item);
	}

	@Override
	public void abort(final ExecutorEndpoint executor, final AbortRequest request) {
		send(executor, WorkflowServices.EXECUTION_ABORT_PATH, request);
	}

	@Override
	public ExecutionState status(final ExecutorEndpoint executor, final String workItemId) {
		final String path = WorkflowServices.EXECUTION_STATUS_PATH.replace("{workItemId}", workItemId);
		return httpService.sendRequest(uri(executor, path), HttpMethod.GET, ExecutionState.class, null, null, headers());
	}

	private void send(final ExecutorEndpoint executor, final String path, final Object payload) {
		httpService.sendRequest(uri(executor, path), HttpMethod.POST, Void.class, payload, null, headers());
	}

	private static UriComponents uri(final ExecutorEndpoint executor, final String path) {
		return HttpUtilities.createURI(executor.scheme(), executor.address(), executor.port(), executor.basePath() + path);
	}

	private Map<String, String> headers() {
		final String header = HttpUtilities.calculateAuthorizationHeader(sysInfo);
		return header == null ? Map.of() : Map.of(HttpHeaders.AUTHORIZATION, header);
	}
}
