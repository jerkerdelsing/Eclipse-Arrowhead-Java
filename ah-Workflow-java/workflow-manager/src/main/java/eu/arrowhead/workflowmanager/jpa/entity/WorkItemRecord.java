/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.jpa.entity;

import eu.arrowhead.common.jpa.ArrowheadEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One attempt of a step: dispatched to (push) or claimed by (pull) one executor. */
@Entity
@Table(name = "work_item")
public class WorkItemRecord extends ArrowheadEntity {

	public enum State {
		OPEN, DISPATCHED, CLAIMED, BOUND, DONE, FAILED, ABORTED;

		public boolean isActive() {
			return this == OPEN || this == DISPATCHED || this == CLAIMED || this == BOUND;
		}
	}

	@Column(nullable = false, unique = true, length = VARCHAR_SMALL)
	private String uid;

	@Column(nullable = false)
	private long sessionId;

	@Column(nullable = false, length = VARCHAR_SMALL)
	private String actionName;

	@Column(nullable = false, length = VARCHAR_SMALL)
	private String stepName;

	@Column(nullable = true, length = VARCHAR_SMALL)
	private String executorName;

	@Column(nullable = false)
	private int attempt;

	@Column(nullable = false, length = VARCHAR_TINY)
	private String state;

	@Column(nullable = true, length = VARCHAR_LARGE)
	private String excluded;

	@Column(nullable = true, columnDefinition = "MEDIUMTEXT")
	private String pendingResult;

	@Column(nullable = true, columnDefinition = "MEDIUMTEXT")
	private String message;

	public WorkItemRecord() {
	}

	public WorkItemRecord(final String uid, final long sessionId, final String actionName, final String stepName, final String executorName, final int attempt,
			final State state, final String excluded) {
		this.uid = uid;
		this.sessionId = sessionId;
		this.actionName = actionName;
		this.stepName = stepName;
		this.executorName = executorName;
		this.attempt = attempt;
		this.state = state.name();
		this.excluded = excluded;
	}

	public long getId() { return id; }
	public String getUid() { return uid; }
	public long getSessionId() { return sessionId; }
	public String getActionName() { return actionName; }
	public String getStepName() { return stepName; }
	public String getExecutorName() { return executorName; }
	public void setExecutorName(final String executorName) { this.executorName = executorName; }
	public int getAttempt() { return attempt; }
	public State getState() { return State.valueOf(state); }
	public void setState(final State state) { this.state = state.name(); }
	public String getExcluded() { return excluded; }
	public String getPendingResult() { return pendingResult; }
	public void setPendingResult(final String pendingResult) { this.pendingResult = pendingResult; }
	public String getMessage() { return message; }
	public void setMessage(final String message) { this.message = message; }
}
