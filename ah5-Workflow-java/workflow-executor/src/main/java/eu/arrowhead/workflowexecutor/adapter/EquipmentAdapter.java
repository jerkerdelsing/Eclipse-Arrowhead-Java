/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.adapter;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Invocation SPI (consume_i): one implementation per interface family. Add a Spring bean implementing this interface to
 * support another protocol (MQTT, CoAP, OPC UA, a legacy controller); the first adapter that supports a binding is used.
 */
public interface EquipmentAdapter {

	boolean supports(Binding binding);

	/** @return d', the provider's result */
	JsonNode invoke(Binding binding, String operation, JsonNode input) throws InvocationException;
}
