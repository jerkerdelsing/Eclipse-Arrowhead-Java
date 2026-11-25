/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.workflow.wire.LeaseRequest;
import eu.arrowhead.workflow.wire.LeaseResponse;
import eu.arrowhead.workflow.wire.LeaseStatus;
import eu.arrowhead.workflowmanager.WorkflowManagerConstants;
import eu.arrowhead.workflowmanager.jpa.entity.LeaseRecord;
import eu.arrowhead.workflowmanager.jpa.repository.LeaseRepository;

/**
 * workflowLease: the lease variant of the tool manager (Sensors paper §8). One valid lease per resource at a time;
 * reservations are granted in request (FIFO) order; a lease that is neither renewed nor released expires, and the next
 * queued reservation can then be granted. Expiry is an event of the lease provider, not a session transition.
 */
@Service
public class LeaseService {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private LeaseRepository repository;

	@Value(WorkflowManagerConstants.$LEASE_DEFAULT_DURATION_WD)
	private double defaultDuration;

	public synchronized LeaseResponse reserve(final String requester, final LeaseRequest request) {
		if (request == null || Utilities.isEmpty(request.resource())) {
			throw new InvalidParameterException("Resource is missing");
		}
		final double duration = request.durationSeconds() == null || request.durationSeconds() <= 0 ? defaultDuration : request.durationSeconds();
		final LeaseRecord lease = repository.saveAndFlush(new LeaseRecord(request.resource(), request.sessionId(), requester, UUID.randomUUID().toString(), duration,
				LeaseStatus.QUEUED.name()));
		tryGrant(lease);
		return response(repository.findById(lease.getId()).orElseThrow());
	}

	public synchronized LeaseResponse poll(final LeaseRequest request) {
		final Optional<LeaseRecord> lease = request == null || request.ticket() == null ? Optional.empty() : repository.findByTicket(request.ticket());
		if (lease.isEmpty()) {
			return new LeaseResponse(LeaseStatus.UNKNOWN, null, null, request == null ? null : request.ticket(), null, "Unknown ticket");
		}
		expire();
		tryGrant(lease.get());
		return response(repository.findById(lease.get().getId()).orElseThrow());
	}

	/** Extends a valid lease; the token is the capability (it travels in ξ(s_res) to the tool-using steps). */
	public synchronized LeaseResponse renew(final LeaseRequest request) {
		expire();
		final Optional<LeaseRecord> lease = request == null || request.token() == null ? Optional.empty() : repository.findByToken(request.token());
		if (lease.isEmpty() || !LeaseStatus.GRANTED.name().equals(lease.get().getStatus())) {
			return new LeaseResponse(lease.map(l -> LeaseStatus.valueOf(l.getStatus())).orElse(LeaseStatus.UNKNOWN), lease.map(LeaseRecord::getResource).orElse(null),
					request == null ? null : request.token(), null, null, "No valid lease");
		}
		final LeaseRecord l = lease.get();
		l.setLeaseUntil(Utilities.utcNow().plusNanos((long) (l.getDurationSeconds() * 1e9)));
		return response(repository.saveAndFlush(l));
	}

	public synchronized LeaseResponse release(final LeaseRequest request) {
		final Optional<LeaseRecord> lease = request == null || request.token() == null ? Optional.empty() : repository.findByToken(request.token());
		if (lease.isEmpty()) {
			return new LeaseResponse(LeaseStatus.UNKNOWN, null, request == null ? null : request.token(), null, null, "Unknown token");
		}
		final LeaseRecord l = lease.get();
		if (LeaseStatus.GRANTED.name().equals(l.getStatus())) {
			l.setStatus(LeaseStatus.RELEASED.name());
			repository.saveAndFlush(l);
		}
		return response(l);
	}

	/** Compensation extension: releases every lease a failed session still holds or waits for. */
	public synchronized int releaseSession(final long sessionId) {
		final List<LeaseRecord> leases = repository.findAllBySessionIdAndStatusIn(sessionId, List.of(LeaseStatus.GRANTED.name(), LeaseStatus.QUEUED.name()));
		leases.forEach(l -> l.setStatus(LeaseStatus.RELEASED.name()));
		repository.saveAllAndFlush(leases);
		return leases.size();
	}

	@Scheduled(fixedDelay = 500)
	public synchronized void expire() {
		final ZonedDateTime now = Utilities.utcNow();
		for (final LeaseRecord l : repository.findAllByStatus(LeaseStatus.GRANTED.name())) {
			if (l.getLeaseUntil() != null && l.getLeaseUntil().isBefore(now)) {
				l.setStatus(LeaseStatus.EXPIRED.name());
				repository.saveAndFlush(l);
				logger.info("Lease on {} held by session {} expired", l.getResource(), l.getSessionId());
			}
		}
	}

	//-------------------------------------------------------------------------------------------------
	private void tryGrant(final LeaseRecord lease) {
		if (!LeaseStatus.QUEUED.name().equals(lease.getStatus())) {
			return;
		}
		if (!repository.findAllByResourceAndStatusOrderByIdAsc(lease.getResource(), LeaseStatus.GRANTED.name()).isEmpty()) {
			return;
		}
		final List<LeaseRecord> queue = repository.findAllByResourceAndStatusOrderByIdAsc(lease.getResource(), LeaseStatus.QUEUED.name());
		if (queue.isEmpty() || queue.get(0).getId() != lease.getId()) {
			return; // FIFO: only the head of the queue is granted
		}
		lease.setStatus(LeaseStatus.GRANTED.name());
		lease.setToken(UUID.randomUUID().toString());
		lease.setLeaseUntil(Utilities.utcNow().plusNanos((long) (lease.getDurationSeconds() * 1e9)));
		repository.saveAndFlush(lease);
	}

	private static LeaseResponse response(final LeaseRecord l) {
		final LeaseStatus status = LeaseStatus.valueOf(l.getStatus());
		return new LeaseResponse(status, l.getResource(), status == LeaseStatus.GRANTED ? l.getToken() : null, l.getTicket(),
				l.getLeaseUntil() == null ? null : Utilities.convertZonedDateTimeToUTCString(l.getLeaseUntil()), null);
	}
}
