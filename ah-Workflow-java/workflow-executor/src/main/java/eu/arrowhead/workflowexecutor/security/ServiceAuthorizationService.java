/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.Defaults;
import eu.arrowhead.common.SystemInfo;
import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.dto.AuthorizationVerifyRequestDTO;
import eu.arrowhead.dto.enums.AuthorizationTargetType;

/** Asks ConsumerAuthorization (authorization.verify) whether a consumer may invoke an operation of this provider; decisions are cached for 30 s. */
@Service
public class ServiceAuthorizationService {

	static final long CACHE_MILLIS = 30_000;

	private final Logger logger = LogManager.getLogger(getClass());

	@Autowired
	private ArrowheadHttpService httpService;

	@Autowired
	private SystemInfo sysInfo;

	private final Map<String, Decision> cache = new ConcurrentHashMap<>();

	private record Decision(boolean allowed, long expiresAt) {
	}

	public boolean isAuthorized(final String consumer, final String serviceDefinition, final String operation) {
		final String key = consumer + "|" + serviceDefinition + "|" + operation;
		final long now = System.currentTimeMillis();
		final Decision cached = cache.get(key);
		if (cached != null && cached.expiresAt() > now) {
			return cached.allowed();
		}
		final AuthorizationVerifyRequestDTO payload = new AuthorizationVerifyRequestDTO(sysInfo.getSystemName(), consumer, Defaults.DEFAULT_CLOUD,
				AuthorizationTargetType.SERVICE_DEF.name(), serviceDefinition, operation);
		try {
			final boolean allowed = Boolean.TRUE.equals(httpService.consumeService(Constants.SERVICE_DEF_AUTHORIZATION, Constants.SERVICE_OP_VERIFY, Boolean.class, payload));
			cache.put(key, new Decision(allowed, now + CACHE_MILLIS));
			return allowed;
		} catch (final RuntimeException ex) {
			// fail closed and do not cache, so the next request asks again
			logger.error("Authorization check failed for {}: {}", key, ex.getMessage());
			logger.debug(ex);
			return false;
		}
	}
}
