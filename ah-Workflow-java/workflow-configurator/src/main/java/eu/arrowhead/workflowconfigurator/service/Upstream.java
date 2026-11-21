/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;

import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.SystemInfo;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.common.http.HttpService;
import eu.arrowhead.common.http.HttpUtilities;

/**
 * Consumes the providers through the common library: orchestration on behalf of the WorkflowConfigurator, its
 * certificate and its identity token. Every failure becomes an {@link UpstreamException} with an HTTP status.
 */
@Component
public class Upstream {

	@Autowired
	private ArrowheadHttpService http;

	@Autowired
	private HttpService httpService;

	@Autowired
	private SystemInfo sysInfo;

	public JsonNode call(final String service, final String operation, final String provider, final Object payload) {
		try {
			return payload == null ? http.consumeService(service, operation, provider, JsonNode.class)
					: http.consumeService(service, operation, provider, JsonNode.class, payload);
		} catch (final ArrowheadException ex) {
			throw translate(provider, service, operation, ex);
		}
	}

	public JsonNode call(final String service, final String operation, final String provider, final List<String> pathParams) {
		try {
			return http.consumeService(service, operation, provider, JsonNode.class, pathParams);
		} catch (final ArrowheadException ex) {
			throw translate(provider, service, operation, ex);
		}
	}

	/**
	 * serviceRegistryManagement: the ServiceRegistry does not offer its own management service to the collector's
	 * lookup, so it is called at the configured ServiceRegistry address (as LocalCloudManager does), with this system's
	 * certificate and identity token; ConsumerAuthorization decides (management.policy=authorization).
	 */
	public JsonNode serviceRegistryManagement(final String operation, final String path, final Object payload) {
		final String scheme = sysInfo.isSslEnabled() ? Constants.HTTPS : Constants.HTTP;
		final UriComponents uri = HttpUtilities.createURI(scheme, sysInfo.getServiceRegistryAddress(), sysInfo.getServiceRegistryPort(), path);
		final String header = HttpUtilities.calculateAuthorizationHeader(sysInfo);
		try {
			return httpService.sendRequest(uri, HttpMethod.POST, JsonNode.class, payload, null, header == null ? Map.of() : Map.of(HttpHeaders.AUTHORIZATION, header));
		} catch (final ArrowheadException ex) {
			throw translate(Constants.SYS_NAME_SERVICE_REGISTRY, Constants.SERVICE_DEF_SERVICE_REGISTRY_MANAGEMENT, operation, ex);
		}
	}

	public JsonNode query(final String service, final String operation, final String provider, final MultiValueMap<String, String> query) {
		try {
			return http.consumeService(service, operation, provider, JsonNode.class, query);
		} catch (final ArrowheadException ex) {
			throw translate(provider, service, operation, ex);
		}
	}

	static UpstreamException translate(final String provider, final String service, final String operation, final ArrowheadException ex) {
		final String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
		int status = ex.getExceptionType() == null ? 502 : ex.getExceptionType().getErrorCode();
		if (message.contains("already exists")) {
			status = 409; // the WorkflowManager reports immutable-recipe conflicts as 409 with type INVALID_PARAMETER
		}
		if (status >= 500) {
			status = 502;
		}
		return new UpstreamException(status, provider, provider + " " + service + "." + operation + ": " + message);
	}
}
