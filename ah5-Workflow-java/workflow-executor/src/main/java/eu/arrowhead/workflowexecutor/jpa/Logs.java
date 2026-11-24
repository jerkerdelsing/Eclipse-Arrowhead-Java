/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.jpa;

import eu.arrowhead.common.jpa.LogEntity;
import jakarta.persistence.Entity;

/** Log entries of the common library (embedded H2 database; the executor keeps no other persistent state). */
@Entity
public class Logs extends LogEntity {
}
