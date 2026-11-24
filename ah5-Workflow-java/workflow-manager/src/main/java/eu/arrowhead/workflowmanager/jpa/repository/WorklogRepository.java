/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.jpa.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import eu.arrowhead.common.jpa.RefreshableRepository;
import eu.arrowhead.workflowmanager.jpa.entity.Worklog;

@Repository
public interface WorklogRepository extends RefreshableRepository<Worklog, Long> {

	List<Worklog> findAllBySessionIdOrderByIdAsc(Long sessionId);

	List<Worklog> findTop500ByOrderByIdDesc();
}
