/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.jpa.entity;

import eu.arrowhead.common.jpa.ArrowheadEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A registered, immutable recipe version (canonical JSON document). */
@Entity
@Table(name = "recipe", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "version" }))
public class RecipeRecord extends ArrowheadEntity {

	public static final String ACTIVE = "ACTIVE";
	public static final String RETIRED = "RETIRED";

	@Column(nullable = false, length = VARCHAR_SMALL)
	private String name;

	@Column(nullable = false, length = VARCHAR_TINY)
	private String version;

	@Column(nullable = false, length = VARCHAR_TINY)
	private String status;

	@Column(nullable = false, columnDefinition = "MEDIUMTEXT")
	private String document;

	@Column(nullable = true, length = VARCHAR_SMALL)
	private String registeredBy;

	public RecipeRecord() {
	}

	public RecipeRecord(final String name, final String version, final String document, final String registeredBy) {
		this.name = name;
		this.version = version;
		this.document = document;
		this.registeredBy = registeredBy;
		this.status = ACTIVE;
	}

	public long getId() { return id; }
	public String getName() { return name; }
	public String getVersion() { return version; }
	public String getStatus() { return status; }
	public void setStatus(final String status) { this.status = status; }
	public String getDocument() { return document; }
	public String getRegisteredBy() { return registeredBy; }
}
