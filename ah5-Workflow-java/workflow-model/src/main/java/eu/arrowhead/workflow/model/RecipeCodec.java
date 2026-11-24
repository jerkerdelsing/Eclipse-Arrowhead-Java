/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Reads and writes the canonical recipe JSON. Unknown fields are rejected (additionalProperties: false). */
public final class RecipeCodec {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
			.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

	private RecipeCodec() {
		throw new UnsupportedOperationException();
	}

	public static ObjectMapper mapper() {
		return MAPPER;
	}

	public static Recipe parse(final String json) {
		try {
			return MAPPER.readValue(json, Recipe.class);
		} catch (final JsonProcessingException ex) {
			throw new IllegalArgumentException("Invalid recipe document: " + ex.getOriginalMessage(), ex);
		}
	}

	public static Recipe parse(final InputStream in) {
		try {
			return MAPPER.readValue(in, Recipe.class);
		} catch (final IOException ex) {
			throw new IllegalArgumentException("Invalid recipe document: " + ex.getMessage(), ex);
		}
	}

	public static Recipe fromTree(final JsonNode node) {
		try {
			return MAPPER.treeToValue(node, Recipe.class);
		} catch (final JsonProcessingException ex) {
			throw new IllegalArgumentException("Invalid recipe document: " + ex.getOriginalMessage(), ex);
		}
	}

	public static String write(final Object value) {
		try {
			return MAPPER.writeValueAsString(value);
		} catch (final JsonProcessingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static JsonNode toTree(final Object value) {
		return MAPPER.valueToTree(value);
	}
}
