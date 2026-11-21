/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.sosconfigurator.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import jakarta.servlet.Filter;

// Builds the real security filter chain with TLS on. Spring Security 7 removed channel security, so a requiresChannel call
// compiles but fails when the chain is built; this context would then not start.
@SpringJUnitWebConfig(SosSecurityConfigTlsTest.TestConfig.class)
@TestPropertySource(properties = { "server.ssl.enabled=true", "console.port=8495" })
public class SosSecurityConfigTlsTest {

	//=================================================================================================
	// members

	private static final int CONSOLE_PORT = 8495;
	private static final int API_PORT = 8494;
	private static final int MAPPED_HTTP_PORT = 8080;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private Filter springSecurityFilterChain;

	private MockMvc mvc;

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@BeforeEach
	public void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void plainHttpOnNonConsolePortIsRedirectedToHttps() throws Exception {
		// The real Arrowhead connector (8494) is TLS-only when server.ssl.enabled=true, so Tomcat rejects plain HTTP there before
		// Spring Security sees it. The default PortMapper knows 8080 -> 8443, which shows the redirect applies off the console port.
		mvc.perform(get("http://localhost:" + MAPPED_HTTP_PORT + "/ping").with(request -> {
			request.setLocalPort(MAPPED_HTTP_PORT);
			return request;
		}))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("https://localhost:8443/**"));
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void httpsOnArrowheadConnectorIsServed() throws Exception {
		mvc.perform(get("https://localhost:" + API_PORT + "/ping").secure(true).with(request -> {
			request.setLocalPort(API_PORT);
			return request;
		}))
				.andExpect(status().isOk());
	}

	//-------------------------------------------------------------------------------------------------
	@Test
	public void plainHttpOnConsoleConnectorIsNotRedirected() throws Exception {
		mvc.perform(get("http://localhost:" + CONSOLE_PORT + "/ping").with(request -> {
			request.setLocalPort(CONSOLE_PORT);
			return request;
		}))
				.andExpect(status().isOk());
	}

	//=================================================================================================
	// nested classes

	//-------------------------------------------------------------------------------------------------
	@Configuration
	@EnableWebMvc
	@EnableWebSecurity
	@Import(SosSecurityConfig.class)
	static class TestConfig {

		// registered as a member class of the configuration
		@RestController
		static class PingController {
			@GetMapping("/ping")
			public String ping() {
				return "pong";
			}
		}
	}
}
