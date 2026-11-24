/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.common.exception.ExternalServerError;
import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.workflow.wire.WorkflowServices;

/**
 * Consumed workflowProduct service of the Intelligent Product (Derhamy, Paper H): retrieve the recipe fragment for this
 * workstation, and store the results when the session closes. The product is the final arbiter of the work.
 */
@Component
public class ProductGateway {

	@Autowired
	private ArrowheadHttpService arrowheadHttpService;

	/** Response: {"recipe": {...}, "parameters": {...}, "subject": "..."}. */
	public JsonNode retrieveProcessStep(final String productSystem, final String workstationId) {
		final JsonNode order = arrowheadHttpService.consumeService(WorkflowServices.SD_PRODUCT, WorkflowServices.OP_RETRIEVE_PROCESS_STEP, productSystem, JsonNode.class,
				Map.of("workstationId", workstationId == null ? "" : workstationId));
		if (order == null || !order.has("recipe")) {
			throw new ExternalServerError("Product " + productSystem + " returned no recipe for this workstation");
		}
		return order;
	}

	public void storeProcessStepResult(final String productSystem, final Map<String, Object> payload) {
		arrowheadHttpService.consumeService(WorkflowServices.SD_PRODUCT, WorkflowServices.OP_STORE_PROCESS_STEP_RESULT, productSystem, Void.class, payload);
	}
}
