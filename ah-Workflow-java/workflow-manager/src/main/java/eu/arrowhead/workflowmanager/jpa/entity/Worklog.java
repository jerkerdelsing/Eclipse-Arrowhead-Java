/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.jpa.entity;

import eu.arrowhead.common.jpa.UnmodifiableArrowheadEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Execution journal of sessions; createdAt is the entry date. */
@Entity
@Table(name = "worklog")
public class Worklog extends UnmodifiableArrowheadEntity {

	@Column(nullable = true)
	private Long sessionId;

	@Column(nullable = true, length = VARCHAR_MEDIUM)
	private String recipe;

	@Column(nullable = true, length = VARCHAR_SMALL)
	private String stepName;

	@Column(nullable = false, columnDefinition = "MEDIUMTEXT")
	private String message;

	@Column(nullable = true, columnDefinition = "MEDIUMTEXT")
	private String detail;

	public Worklog() {
	}

	public Worklog(final Long sessionId, final String recipe, final String stepName, final String message, final String detail) {
		this.sessionId = sessionId;
		this.recipe = recipe;
		this.stepName = stepName;
		this.message = message;
		this.detail = detail;
	}

	public long getId() { return id; }
	public java.time.ZonedDateTime getCreatedAt() { return createdAt; }
	public Long getSessionId() { return sessionId; }
	public String getRecipe() { return recipe; }
	public String getStepName() { return stepName; }
	public String getMessage() { return message; }
	public String getDetail() { return detail; }
}
