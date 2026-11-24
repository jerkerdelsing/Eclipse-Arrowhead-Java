/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import eu.arrowhead.common.Constants;
import eu.arrowhead.workflowconfigurator.WorkflowConfiguratorConstants;

// The common DefaultSecurityConfig, except that the HTTPS-only rule applies to the Arrowhead connector, not to the
// plain-HTTP console connector (which has its own password login, see ConsoleFilter).
@Configuration
public class ConfiguratorSecurityConfig {

	//=================================================================================================
	// members

	@Value(Constants.$SERVER_SSL_ENABLED_WD)
	private boolean sslEnabled;

	@Value(WorkflowConfiguratorConstants.$CONSOLE_PORT_WD)
	private int consolePort;

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@Bean
	SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
		http.httpBasic(basic -> basic.disable())
				.csrf(csrf -> csrf.disable())
				.sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.NEVER));

		if (sslEnabled) {
			// Spring Security 7 has no channel security; the HTTPS redirect is limited to the Arrowhead connector instead
			http.redirectToHttps(https -> https.requestMatchers(request -> request.getLocalPort() != consolePort));
		}

		return http.build();
	}
}
