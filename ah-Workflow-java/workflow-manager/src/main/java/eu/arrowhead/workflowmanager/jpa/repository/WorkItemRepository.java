/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.jpa.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import eu.arrowhead.common.jpa.RefreshableRepository;
import eu.arrowhead.workflowmanager.jpa.entity.WorkItemRecord;

@Repository
public interface WorkItemRepository extends RefreshableRepository<WorkItemRecord, Long> {

	Optional<WorkItemRecord> findByUid(String uid);

	List<WorkItemRecord> findAllBySessionIdOrderByIdAsc(long sessionId);

	List<WorkItemRecord> findAllBySessionIdAndStepNameOrderByIdAsc(long sessionId, String stepName);

	List<WorkItemRecord> findAllByStateInOrderByIdAsc(Collection<String> states);
}
