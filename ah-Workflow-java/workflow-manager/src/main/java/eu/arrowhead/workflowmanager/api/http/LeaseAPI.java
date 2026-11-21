/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.http;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflow.wire.LeaseRequest;
import eu.arrowhead.workflow.wire.LeaseResponse;
import eu.arrowhead.workflowmanager.service.LeaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;

/** workflowLease: FIFO leases on shared resources with expiry. */
@RestController
@RequestMapping(LEASE_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class LeaseAPI {

	@Autowired
	private LeaseService leaseService;

	@Operation(summary = "Reserves a resource: GRANTED with token and leaseUntil, or QUEUED with ticket (workflowLease.reserve)")
	@PostMapping(path = LEASE_RESERVE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody LeaseResponse reserve(final HttpServletRequest request, @RequestBody final LeaseRequest dto) {
		final String origin = HttpMethod.POST.name() + " " + LEASE_PATH + LEASE_RESERVE_PATH;
		return leaseService.reserve(HttpUtilities.acquireName(request, origin), dto);
	}

	@Operation(summary = "Polls a queued reservation by ticket (workflowLease.poll)")
	@PostMapping(path = LEASE_POLL_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody LeaseResponse poll(@RequestBody final LeaseRequest dto) {
		return leaseService.poll(dto);
	}

	@Operation(summary = "Renews a granted lease by token before each physical use (workflowLease.renew)")
	@PostMapping(path = LEASE_RENEW_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody LeaseResponse renew(@RequestBody final LeaseRequest dto) {
		return leaseService.renew(dto);
	}

	@Operation(summary = "Releases a lease by token (workflowLease.release)")
	@PostMapping(path = LEASE_RELEASE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody LeaseResponse release(@RequestBody final LeaseRequest dto) {
		return leaseService.release(dto);
	}
}
