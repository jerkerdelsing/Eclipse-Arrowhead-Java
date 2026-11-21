/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model;

import java.io.InputStream;

final class Examples {

	static final String FORKLIFT = "examples/shared-tool-tightening.recipe.json";
	static final String TEMPERATURE = "examples/temperature-control.recipe.json";

	private Examples() {
	}

	static Recipe load(final String resource) {
		try (InputStream in = Examples.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Missing test resource " + resource);
			}
			return RecipeCodec.parse(in);
		} catch (final java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static String json(final String resource) {
		try (InputStream in = Examples.class.getClassLoader().getResourceAsStream(resource)) {
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (final java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
