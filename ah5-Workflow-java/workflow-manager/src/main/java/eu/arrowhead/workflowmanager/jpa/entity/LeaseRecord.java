/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.jpa.entity;

import java.time.ZonedDateTime;

import eu.arrowhead.common.jpa.ArrowheadEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A reservation of a shared resource: queued (ticket) or granted (token, leaseUntil). */
@Entity
@Table(name = "lease")
public class LeaseRecord extends ArrowheadEntity {

	@Column(nullable = false, length = VARCHAR_SMALL)
	private String resource;

	@Column(nullable = true)
	private Long sessionId;

	@Column(nullable = false, length = VARCHAR_SMALL)
	private String holder;

	@Column(nullable = false, unique = true, length = VARCHAR_SMALL)
	private String ticket;

	@Column(nullable = true, unique = true, length = VARCHAR_SMALL)
	private String token;

	@Column(nullable = false, length = VARCHAR_TINY)
	private String status;

	@Column(nullable = true)
	private ZonedDateTime leaseUntil;

	@Column(nullable = false)
	private double durationSeconds;

	public LeaseRecord() {
	}

	public LeaseRecord(final String resource, final Long sessionId, final String holder, final String ticket, final double durationSeconds, final String status) {
		this.resource = resource;
		this.sessionId = sessionId;
		this.holder = holder;
		this.ticket = ticket;
		this.durationSeconds = durationSeconds;
		this.status = status;
	}

	public long getId() { return id; }
	public String getResource() { return resource; }
	public Long getSessionId() { return sessionId; }
	public String getHolder() { return holder; }
	public String getTicket() { return ticket; }
	public String getToken() { return token; }
	public void setToken(final String token) { this.token = token; }
	public String getStatus() { return status; }
	public void setStatus(final String status) { this.status = status; }
	public ZonedDateTime getLeaseUntil() { return leaseUntil; }
	public void setLeaseUntil(final ZonedDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
	public double getDurationSeconds() { return durationSeconds; }
}
