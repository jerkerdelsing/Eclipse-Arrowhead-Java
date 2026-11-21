/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.runtime;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import java.net.URI;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;

import eu.arrowhead.common.SystemInfo;
import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.common.http.HttpService;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflow.wire.ClaimRequest;
import eu.arrowhead.workflow.wire.LeaseRequest;
import eu.arrowhead.workflow.wire.LeaseResponse;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflow.wire.WorkItemReport;
import eu.arrowhead.workflowexecutor.WorkflowExecutorConstants;

/** Consumed WorkflowManager services: workflowWorkItem and workflowLease (ServiceRegistry lookup, or workflow.manager.url). */
@Component
public class ManagerClient {

	@Autowired
	private ArrowheadHttpService arrowheadHttpService;

	@Autowired
	private HttpService httpService;

	@Autowired
	private SystemInfo sysInfo;

	@Value(WorkflowExecutorConstants.$MANAGER_URL_WD)
	private String managerUrl;

	public void report(final WorkItemReport report) {
		call(SD_WORK_ITEM, OP_REPORT, WORK_ITEM_PATH + WORK_ITEM_REPORT_PATH, Void.class, report);
	}

	public WorkItem claim(final ClaimRequest request) {
		return call(SD_WORK_ITEM, OP_CLAIM, WORK_ITEM_PATH + WORK_ITEM_CLAIM_PATH, WorkItem.class, request);
	}

	public LeaseResponse reserve(final LeaseRequest request) {
		return call(SD_LEASE, OP_RESERVE, LEASE_PATH + LEASE_RESERVE_PATH, LeaseResponse.class, request);
	}

	public LeaseResponse poll(final LeaseRequest request) {
		return call(SD_LEASE, OP_POLL, LEASE_PATH + LEASE_POLL_PATH, LeaseResponse.class, request);
	}

	public LeaseResponse renew(final LeaseRequest request) {
		return call(SD_LEASE, OP_RENEW, LEASE_PATH + LEASE_RENEW_PATH, LeaseResponse.class, request);
	}

	public LeaseResponse release(final LeaseRequest request) {
		return call(SD_LEASE, OP_RELEASE, LEASE_PATH + LEASE_RELEASE_PATH, LeaseResponse.class, request);
	}

	private <T> T call(final String service, final String operation, final String path, final Class<T> type, final Object payload) {
		if (managerUrl == null || managerUrl.isBlank()) {
			return arrowheadHttpService.consumeService(service, operation, SYS_WORKFLOW_MANAGER, type, payload);
		}
		final URI base = URI.create(managerUrl);
		final UriComponents uri = HttpUtilities.createURI(base.getScheme(), base.getHost(), base.getPort(), path);
		final String header = HttpUtilities.calculateAuthorizationHeader(sysInfo);
		return httpService.sendRequest(uri, HttpMethod.POST, type, payload, null, header == null ? Map.of() : Map.of(HttpHeaders.AUTHORIZATION, header));
	}
}
