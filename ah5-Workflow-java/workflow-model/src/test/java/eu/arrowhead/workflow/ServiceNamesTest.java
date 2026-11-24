/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import eu.arrowhead.workflow.wire.WorkflowServices;

class ServiceNamesTest {

	@Test
	void kebabCaseConfigurationCoversTheCamelCaseNameTheChoreographerSends() {
		final List<String> configured = List.of("temperature-read", "heating_switch");
		assertTrue(WorkflowServices.covers(configured, "temperatureRead"));
		assertTrue(WorkflowServices.covers(configured, "temperature-read"));
		assertTrue(WorkflowServices.covers(configured, "heatingSwitch"));
		assertFalse(WorkflowServices.covers(configured, "temperatureEvaluate"));
	}

	@Test
	void emptyListAndWildcardCoverEverything() {
		assertTrue(WorkflowServices.covers(List.of(), "anything"));
		assertTrue(WorkflowServices.covers(List.of(WorkflowServices.ANY_SERVICE), "anything"));
	}
}
