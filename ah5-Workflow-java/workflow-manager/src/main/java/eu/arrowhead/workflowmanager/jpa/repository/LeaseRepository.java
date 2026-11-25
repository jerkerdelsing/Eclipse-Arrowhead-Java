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
import eu.arrowhead.workflowmanager.jpa.entity.LeaseRecord;

@Repository
public interface LeaseRepository extends RefreshableRepository<LeaseRecord, Long> {

	Optional<LeaseRecord> findByTicket(String ticket);

	Optional<LeaseRecord> findByToken(String token);

	List<LeaseRecord> findAllByResourceAndStatusOrderByIdAsc(String resource, String status);

	List<LeaseRecord> findAllByStatus(String status);

	List<LeaseRecord> findAllBySessionIdAndStatusIn(Long sessionId, Collection<String> statuses);
}
