/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import eu.arrowhead.workflow.wire.ServiceOperations;
import eu.arrowhead.workflow.wire.ServiceOperations.ServiceOperation;

class ServiceOperationsTest {

	@Test
	void mapsEveryServiceOperation() {
		assertEquals(new ServiceOperation("workflowRecipe", "register"), ServiceOperations.of("POST", "/workflowmanager/recipe/register").orElseThrow());
		assertEquals(new ServiceOperation("workflowRecipe", "retire"), ServiceOperations.of("DELETE", "/workflowmanager/recipe/recipes/a/1.0.0").orElseThrow());
		assertEquals(new ServiceOperation("workflowInstance", "get-trace"), ServiceOperations.of("GET", "/workflowmanager/instance/sessions/7/trace").orElseThrow());
		assertEquals(new ServiceOperation("workflowInstance", "get-status"), ServiceOperations.of("GET", "/workflowmanager/instance/sessions/7/").orElseThrow());
		assertEquals(new ServiceOperation("workflowContext", "get-results"), ServiceOperations.of("GET", "/workflowmanager/context/results/w-1").orElseThrow());
		assertEquals(new ServiceOperation("workflowExecution", "start"), ServiceOperations.of("POST", "/workflowexecutor/execution/start").orElseThrow());
	}

	@Test
	void leavesOtherPathsToTheirFilters() {
		assertTrue(ServiceOperations.of("GET", "/workflowmanager/mgmt/worklog").isEmpty());
		assertTrue(ServiceOperations.of("GET", "/workflowmanager/recipe/register").isEmpty());
		assertTrue(ServiceOperations.of("POST", "/workflowexecutor/choreographer/start").isEmpty());
		assertTrue(ServiceOperations.of("GET", "/workflowmanager/instance/sessions/7/trace/x").isEmpty());
	}
}
