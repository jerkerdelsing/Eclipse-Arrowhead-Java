/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.security;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Optional;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.SystemInfo;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.AuthException;
import eu.arrowhead.common.exception.ForbiddenException;
import eu.arrowhead.common.http.filter.ArrowheadFilter;
import eu.arrowhead.workflow.wire.ServiceOperations;
import eu.arrowhead.workflow.wire.ServiceOperations.ServiceOperation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Provider-side ConsumerAuthorization check (service.authorization.enabled=true). Runs after authentication: binds the
 * authenticated system to the client certificate and lets ConsumerAuthorization decide every service operation listed in
 * {@link ServiceOperations}. Sysop requests pass; management paths are left to the management filter. The owner and
 * holder checks of the services themselves still apply after this filter.
 */
@Component
@Order(ServiceAuthorizationFilter.ORDER)
@ConditionalOnProperty(name = ServiceAuthorizationFilter.PROPERTY, havingValue = "true")
public class ServiceAuthorizationFilter extends ArrowheadFilter {

	public static final String PROPERTY = "service.authorization.enabled";
	public static final int ORDER = Constants.REQUEST_FILTER_ORDER_AUTHORIZATION_MGMT_SERVICE + 5;

	@Autowired
	private SystemInfo sysInfo;

	@Autowired
	private ServiceAuthorizationService authorizationService;

	@Override
	protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws IOException, ServletException {
		try {
			final Optional<ServiceOperation> target = ServiceOperations.of(request.getMethod(), request.getRequestURI());
			if (target.isPresent()) {
				final String origin = request.getMethod() + " " + request.getRequestURI();
				final Object consumerAttr = request.getAttribute(Constants.HTTP_ATTR_ARROWHEAD_AUTHENTICATED_SYSTEM);
				if (consumerAttr == null) {
					throw new AuthException("No authenticated Arrowhead system", origin);
				}
				final String consumer = consumerAttr.toString();
				if (sysInfo.isSslEnabled() && !consumer.equals(certificateSystemName(request))) {
					throw new AuthException("Client certificate does not belong to the authenticated system", origin);
				}
				final Object sysop = request.getAttribute(Constants.HTTP_ATTR_ARROWHEAD_SYSOP_REQUEST);
				if (!Boolean.parseBoolean(String.valueOf(sysop))
						&& !authorizationService.isAuthorized(consumer, target.get().serviceDefinition(), target.get().operation())) {
					throw new ForbiddenException(consumer + " is not authorized for " + target.get().serviceDefinition() + "." + target.get().operation(), origin);
				}
			}
			chain.doFilter(request, response);
		} catch (final ArrowheadException ex) {
			handleException(ex, response);
		}
	}

	static String systemNameFromSubject(final String subjectDN) {
		try {
			for (final Rdn rdn : new LdapName(subjectDN).getRdns()) {
				if ("CN".equalsIgnoreCase(rdn.getType())) {
					final String commonName = rdn.getValue().toString();
					final int dot = commonName.indexOf('.');
					return dot < 0 ? commonName : commonName.substring(0, dot);
				}
			}
		} catch (final InvalidNameException ex) {
			return null;
		}
		return null;
	}

	private static String certificateSystemName(final HttpServletRequest request) {
		final X509Certificate[] certificates = (X509Certificate[]) request.getAttribute(Constants.HTTP_ATTR_JAKARTA_SERVLET_REQUEST_X509_CERTIFICATE);
		if (certificates == null || certificates.length == 0) {
			return null;
		}
		return systemNameFromSubject(certificates[0].getSubjectX500Principal().getName(X500Principal.RFC2253));
	}
}
