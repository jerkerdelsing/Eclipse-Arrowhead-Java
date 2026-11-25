/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.jpa.entity;

import eu.arrowhead.common.jpa.ArrowheadEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A workflow session; {@code state} is the JSON snapshot of z = (M, M_A, β, ε, ξ, status) with its trace. */
@Entity
@Table(name = "workflow_session")
public class SessionRecord extends ArrowheadEntity {

	@Column(nullable = false)
	private long recipeId;

	@Column(nullable = false, length = VARCHAR_SMALL)
	private String recipeName;

	@Column(nullable = false, length = VARCHAR_TINY)
	private String recipeVersion;

	@Column(nullable = true, length = VARCHAR_MEDIUM)
	private String subject;

	@Column(nullable = false, length = VARCHAR_SMALL)
	private String owner;

	@Column(nullable = false, length = VARCHAR_TINY)
	private String status;

	@Column(nullable = false, length = VARCHAR_TINY)
	private String dispatchMode;

	@Column(nullable = true, columnDefinition = "MEDIUMTEXT")
	private String parameters;

	@Column(nullable = false, columnDefinition = "MEDIUMTEXT")
	private String state;

	@Column(nullable = true, length = VARCHAR_SMALL)
	private String productSystem;

	@Column(nullable = true, length = VARCHAR_MEDIUM)
	private String extensions;

	@Column(nullable = true, columnDefinition = "MEDIUMTEXT")
	private String message;

	@Version
	@Column(nullable = false)
	private long revision;

	public SessionRecord() {
	}

	@SuppressWarnings("checkstyle:ParameterNumberCheck")
	public SessionRecord(final long recipeId, final String recipeName, final String recipeVersion, final String subject, final String owner, final String dispatchMode,
			final String parameters, final String state, final String productSystem) {
		this.recipeId = recipeId;
		this.recipeName = recipeName;
		this.recipeVersion = recipeVersion;
		this.subject = subject;
		this.owner = owner;
		this.dispatchMode = dispatchMode;
		this.parameters = parameters;
		this.state = state;
		this.productSystem = productSystem;
		this.status = "running";
	}

	public long getId() { return id; }
	public long getRecipeId() { return recipeId; }
	public String getRecipeName() { return recipeName; }
	public String getRecipeVersion() { return recipeVersion; }
	public String getSubject() { return subject; }
	public String getOwner() { return owner; }
	public String getStatus() { return status; }
	public void setStatus(final String status) { this.status = status; }
	public String getDispatchMode() { return dispatchMode; }
	public String getParameters() { return parameters; }
	public String getState() { return state; }
	public void setState(final String state) { this.state = state; }
	public String getProductSystem() { return productSystem; }
	public String getExtensions() { return extensions; }
	public void setExtensions(final String extensions) { this.extensions = extensions; }
	public String getMessage() { return message; }
	public void setMessage(final String message) { this.message = message; }
}
