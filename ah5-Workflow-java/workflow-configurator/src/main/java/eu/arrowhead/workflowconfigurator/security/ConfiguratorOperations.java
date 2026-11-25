/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator.security;

import static eu.arrowhead.workflowconfigurator.WorkflowConfiguratorConstants.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Method and path of each workflowConfiguration operation, for the provider-side ConsumerAuthorization check. */
public final class ConfiguratorOperations {

	public record ServiceOperation(String serviceDefinition, String operation) {
	}

	private record Route(String method, Pattern path, String operation) {
	}

	private static final String ID = "[^/]+";
	private static final List<Route> ROUTES = List.of(
			route("GET", CLOUDS_PATH, OP_LIST_CLOUDS),
			route("GET", "/clouds/" + ID + "/services", OP_LIST_SERVICES),
			route("GET", EXECUTORS_PATH, OP_LIST_EXECUTORS),
			route("GET", RECIPES_PATH, OP_LIST_RECIPES),
			route("POST", RECIPE_VALIDATE_PATH, OP_VALIDATE_RECIPE),
			route("GET", "/recipes/" + ID + "/" + ID, OP_GET_RECIPE),
			route("POST", RECIPES_PATH, OP_STORE_RECIPE));

	private ConfiguratorOperations() {
		throw new UnsupportedOperationException();
	}

	public static Optional<ServiceOperation> of(final String method, final String path) {
		if (method == null || path == null) {
			return Optional.empty();
		}
		final String p = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
		return ROUTES.stream().filter(r -> r.method.equalsIgnoreCase(method) && r.path.matcher(p).matches()).findFirst()
				.map(r -> new ServiceOperation(SD_CONFIGURATION, r.operation));
	}

	private static Route route(final String method, final String path, final String operation) {
		final StringBuilder regex = new StringBuilder();
		final String[] literals = (API_PATH + path).split(Pattern.quote(ID), -1);
		for (int i = 0; i < literals.length; i++) {
			if (i > 0) {
				regex.append(ID);
			}
			regex.append(Pattern.quote(literals[i]));
		}
		return new Route(method, Pattern.compile(regex.toString()), operation);
	}
}
