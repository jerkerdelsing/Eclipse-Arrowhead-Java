/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.adapter;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import eu.arrowhead.common.SystemInfo;
import eu.arrowhead.common.http.HttpService;
import eu.arrowhead.common.http.HttpUtilities;
import eu.arrowhead.workflow.model.RecipeCodec;

/**
 * generic_http / generic_https providers: the input is sent as the JSON body (GET: no body) of the chosen operation; the
 * JSON response is the result. The executor identifies itself with its own Authorization header, so authorisation is
 * evaluated for the executor as consumer c_z(s). Orchestration authorization tokens (TOKEN_AUTH interfaces) are not used.
 * HTTP 4xx is a refusal (FATAL_ERROR); 5xx, 408, 423, 429 and connection problems are transient (ERROR).
 */
@Component
@Order(100)
public class HttpJsonAdapter implements EquipmentAdapter {

	private static final Duration TIMEOUT = Duration.ofMinutes(10);

	@Autowired
	private HttpService httpService;

	@Autowired
	private SystemInfo sysInfo;

	@Override
	public boolean supports(final Binding binding) {
		return binding.templateName() == null || binding.templateName().toLowerCase().startsWith("generic_http");
	}

	@Override
	public JsonNode invoke(final Binding binding, final String operation, final JsonNode input) throws InvocationException {
		final String opName = operation != null ? operation : binding.operations().size() == 1 ? binding.operations().keySet().iterator().next() : null;
		final Binding.Operation op = opName == null ? null : binding.operations().get(opName);
		if (op == null) {
			throw new InvocationException("Operation " + (opName == null ? "(unspecified; set params.operation)" : opName) + " is not offered by " + binding.provider(), false, null);
		}
		final HttpMethod method = HttpMethod.valueOf(op.method().toUpperCase());
		final UriComponents uri = HttpUtilities.createURI(binding.scheme(), binding.address(), binding.port(), binding.basePath() + op.path());
		final String header = HttpUtilities.calculateAuthorizationHeader(sysInfo);
		final WebClient client = binding.scheme().equals("https") ? httpService.createSecureWebClient() : httpService.createInsecureWebClient();
		try {
			final WebClient.RequestBodySpec spec = client.method(method).uri(uri.toUri()).accept(MediaType.APPLICATION_JSON);
			if (header != null) {
				spec.header(HttpHeaders.AUTHORIZATION, header);
			}
			final WebClient.RequestHeadersSpec<?> request = method == HttpMethod.GET || input == null ? spec
					: spec.contentType(MediaType.APPLICATION_JSON).bodyValue(RecipeCodec.write(input));
			final String body = request.retrieve().bodyToMono(String.class).block(TIMEOUT);
			return body == null || body.isBlank() ? NullNode.getInstance() : RecipeCodec.mapper().readTree(body);
		} catch (final WebClientResponseException ex) {
			final int code = ex.getStatusCode().value();
			final boolean transientFailure = code >= 500 || code == 408 || code == 423 || code == 429;
			throw new InvocationException(binding.provider() + " answered " + code + " to " + opName + ": " + ex.getResponseBodyAsString(), transientFailure, ex);
		} catch (final JsonProcessingException ex) {
			throw new InvocationException(binding.provider() + " returned no JSON for " + opName, false, ex);
		} catch (final RuntimeException ex) {
			throw new InvocationException(binding.provider() + " unreachable for " + opName + ": " + ex.getMessage(), true, ex);
		}
	}
}
