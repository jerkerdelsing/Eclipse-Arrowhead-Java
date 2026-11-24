/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.wire;

import static eu.arrowhead.workflow.wire.WorkflowServices.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Maps an HTTP request (method and path) to the service definition and operation it invokes, for the provider-side
 * ConsumerAuthorization check (authorization.verify). Management paths are not listed: the management filter protects them.
 */
public final class ServiceOperations {

	public record ServiceOperation(String serviceDefinition, String operation) {
	}

	private record Route(String method, Pattern path, ServiceOperation target) {
	}

	private static final String ID = "[^/]+";

	private static final List<Route> ROUTES = List.of(
			route("POST", RECIPE_PATH + RECIPE_VALIDATE_PATH, SD_RECIPE, OP_VALIDATE),
			route("POST", RECIPE_PATH + RECIPE_REGISTER_PATH, SD_RECIPE, OP_REGISTER),
			route("GET", RECIPE_PATH + RECIPE_QUERY_PATH, SD_RECIPE, OP_QUERY),
			route("GET", RECIPE_PATH + "/recipes/" + ID + "/" + ID, SD_RECIPE, OP_GET),
			route("DELETE", RECIPE_PATH + "/recipes/" + ID + "/" + ID, SD_RECIPE, OP_RETIRE),
			route("POST", RECIPE_PATH + RECIPE_COMPILE_PATH, SD_RECIPE, OP_COMPILE),
			route("POST", INSTANCE_PATH + INSTANCE_START_PATH, SD_INSTANCE, OP_START),
			route("DELETE", INSTANCE_PATH + "/abort/" + ID, SD_INSTANCE, OP_ABORT),
			route("GET", INSTANCE_PATH + INSTANCE_QUERY_PATH, SD_INSTANCE, OP_QUERY),
			route("GET", INSTANCE_PATH + "/sessions/" + ID, SD_INSTANCE, OP_GET_STATUS),
			route("GET", INSTANCE_PATH + "/sessions/" + ID + "/trace", SD_INSTANCE, OP_GET_TRACE),
			route("POST", WORK_ITEM_PATH + WORK_ITEM_CLAIM_PATH, SD_WORK_ITEM, OP_CLAIM),
			route("POST", WORK_ITEM_PATH + WORK_ITEM_REPORT_PATH, SD_WORK_ITEM, OP_REPORT),
			route("GET", CONTEXT_PATH + "/parameters/" + ID, SD_CONTEXT, OP_GET_PARAMETERS),
			route("GET", CONTEXT_PATH + "/results/" + ID, SD_CONTEXT, OP_GET_RESULTS),
			route("POST", CONTEXT_PATH + CONTEXT_PUT_RESULT_PATH, SD_CONTEXT, OP_PUT_RESULT),
			route("POST", LEASE_PATH + LEASE_RESERVE_PATH, SD_LEASE, OP_RESERVE),
			route("POST", LEASE_PATH + LEASE_POLL_PATH, SD_LEASE, OP_POLL),
			route("POST", LEASE_PATH + LEASE_RENEW_PATH, SD_LEASE, OP_RENEW),
			route("POST", LEASE_PATH + LEASE_RELEASE_PATH, SD_LEASE, OP_RELEASE),
			route("POST", EXECUTION_PATH + EXECUTION_SERVICE_INFO_PATH, SD_EXECUTION, OP_SERVICE_INFO),
			route("POST", EXECUTION_PATH + EXECUTION_START_PATH, SD_EXECUTION, OP_START),
			route("POST", EXECUTION_PATH + EXECUTION_ABORT_PATH, SD_EXECUTION, OP_ABORT),
			route("GET", EXECUTION_PATH + "/status/" + ID, SD_EXECUTION, OP_GET_STATUS));

	/** The service operation a request invokes, or empty for paths outside the registered services (management, docs). */
	public static Optional<ServiceOperation> of(final String method, final String path) {
		if (method == null || path == null) {
			return Optional.empty();
		}
		final String p = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
		return ROUTES.stream().filter(r -> r.method().equalsIgnoreCase(method) && r.path().matcher(p).matches()).map(Route::target).findFirst();
	}

	private static Route route(final String method, final String path, final String sd, final String op) {
		// the paths contain only letters, '/' and '-' besides ID, so they are regular expressions as they stand
		return new Route(method, Pattern.compile(path), new ServiceOperation(sd, op));
	}

	private ServiceOperations() {
		throw new UnsupportedOperationException();
	}
}
