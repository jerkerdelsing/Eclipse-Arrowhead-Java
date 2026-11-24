/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.http;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.Constants;
import eu.arrowhead.workflowmanager.api.dto.ExecutorListResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.WorkItemListResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.WorklogListResponseDTO;
import eu.arrowhead.workflowmanager.jpa.entity.WorkItemRecord;
import eu.arrowhead.workflowmanager.jpa.repository.WorkItemRepository;
import eu.arrowhead.workflowmanager.jpa.repository.WorklogRepository;
import eu.arrowhead.workflowmanager.service.ExecutorDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/** workflowManagement (management filter applies: path contains /mgmt/). */
@RestController
@RequestMapping(WFM_MANAGEMENT_PATH)
@SecurityRequirement(name = Constants.SECURITY_REQ_AUTHORIZATION)
public class WorkflowManagementAPI {

	@Autowired
	private WorklogRepository worklog;

	@Autowired
	private WorkItemRepository workItems;

	@Autowired
	private ExecutorDirectory directory;

	@Operation(summary = "Worklog of one session, or the latest 500 entries (workflowManagement.query-worklog)")
	@GetMapping(path = MGMT_WORKLOG_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody WorklogListResponseDTO worklog(@RequestParam(required = false) final Long sessionId) {
		final var entries = (sessionId == null ? worklog.findTop500ByOrderByIdDesc() : worklog.findAllBySessionIdOrderByIdAsc(sessionId)).stream()
				.map(DTOConverter::worklog).toList();
		return new WorklogListResponseDTO(entries, entries.size());
	}

	@Operation(summary = "Known executors with capabilities and services (workflowManagement.query-executors)")
	@GetMapping(path = MGMT_EXECUTORS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody ExecutorListResponseDTO executors() {
		directory.invalidate();
		final var entries = directory.all();
		return new ExecutorListResponseDTO(entries, entries.size());
	}

	@Operation(summary = "Work items of a session, or all active ones (workflowManagement.query-work-items)")
	@GetMapping(path = MGMT_WORK_ITEMS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody WorkItemListResponseDTO workItems(@RequestParam(required = false) final Long sessionId) {
		final List<WorkItemRecord> records = sessionId != null ? workItems.findAllBySessionIdOrderByIdAsc(sessionId)
				: workItems.findAllByStateInOrderByIdAsc(Arrays.stream(WorkItemRecord.State.values()).filter(WorkItemRecord.State::isActive).map(Enum::name).toList());
		final var entries = records.stream().map(DTOConverter::workItem).toList();
		return new WorkItemListResponseDTO(entries, entries.size());
	}
}
