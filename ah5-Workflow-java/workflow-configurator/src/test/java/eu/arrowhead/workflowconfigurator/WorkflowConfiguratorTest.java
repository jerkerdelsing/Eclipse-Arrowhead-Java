/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;

import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.workflow.model.RecipeCodec;
import eu.arrowhead.workflowconfigurator.console.ConsolePasswordHash;
import eu.arrowhead.workflowconfigurator.service.Upstream;
import eu.arrowhead.workflowconfigurator.service.UpstreamException;
import jakarta.servlet.http.Cookie;

/** Console login and host checks, cloud aggregation, service listing and the store flow, against mocked providers. */
@SpringBootTest(classes = WorkflowConfiguratorMain.class)
@AutoConfigureMockMvc
class WorkflowConfiguratorTest {

	private static final int CONSOLE = 18485;
	private static final String PASSWORD = "correct horse";
	private static final String API = "/workflowconfigurator/api";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private Upstream upstream;

	@MockitoBean
	private ArrowheadHttpService arrowheadHttpService;

	@DynamicPropertySource
	static void properties(final DynamicPropertyRegistry registry) {
		registry.add("workflow.standalone", () -> "true");
		registry.add("arrowhead.security.profile", () -> "development");
		registry.add("console.port", () -> String.valueOf(CONSOLE));
		registry.add("console.password-hash", () -> ConsolePasswordHash.create(PASSWORD));
	}

	private static JsonNode json(final String text) {
		try {
			return RecipeCodec.mapper().readTree(text);
		} catch (final java.io.IOException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	private static RequestPostProcessor console() {
		return r -> {
			r.setLocalPort(CONSOLE);
			r.setRemoteAddr("127.0.0.1");
			r.addHeader("Host", "localhost:" + CONSOLE);
			return r;
		};
	}

	private Cookie login() throws Exception {
		final var result = mvc.perform(post(API + "/session").with(console()).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
				.andReturn();
		return result.getResponse().getCookie("wfc_session");
	}

	private MockHttpServletRequestBuilder as(final MockHttpServletRequestBuilder b, final Cookie session) {
		return b.with(console()).cookie(session);
	}

	@BeforeEach
	void providers() throws Exception {
		when(upstream.call(eq("localCloudConfiguration"), eq("catalog"), eq("LocalCloudConfigurator"), isNull(Object.class))).thenReturn(json("""
				{"cloud":"TestCloud","systems":[{"id":"serviceregistry","name":"ServiceRegistry","kind":"core"},
				 {"id":"timemanager","name":"TimeManager","kind":"support"},{"id":"datamanager","name":"DataManager","kind":"support"}]}"""));
		when(upstream.call(eq("localCloudConfiguration"), eq("list-configurations"), eq("LocalCloudConfigurator"), isNull(Object.class))).thenReturn(json("""
				{"entries":[{"id":"u1","name":"Office","systems":["serviceregistry","timemanager","datamanager"],"revision":3}]}"""));
		when(upstream.call(eq("soLocalCloudConfiguration"), eq("list-designs"), eq("SoLocalCloudConfigurator"), isNull(Object.class))).thenReturn(json("""
				{"entries":[{"id":"d1","name":"Factory","members":["Plant","Office"]}]}"""));
		when(upstream.call(eq("soLocalCloudConfiguration"), eq("get-design"), eq("SoLocalCloudConfigurator"), eq(List.of("d1")))).thenReturn(json("""
				{"id":"d1","design":{"name":"Factory","members":[{"cloud":"Plant","lcDesign":{"id":"u2","name":"Plant","components":["serviceregistry","datamanager"]}}]}}"""));
		when(upstream.serviceRegistryManagement(eq("system-query"), eq("/serviceregistry/mgmt/systems/query"), any(Object.class))).thenReturn(json("""
				{"entries":[{"name":"TimeManager"},{"name":"ServiceRegistry"},{"name":"WfEquipment"}]}"""));
		when(upstream.serviceRegistryManagement(eq("service-query"), eq("/serviceregistry/mgmt/service-instances/query"), any(Object.class))).thenReturn(json("""
				{"entries":[
				 {"provider":{"name":"TimeManager"},"serviceDefinition":{"name":"time"},"version":"1.0.0","metadata":{},
				  "interfaces":[{"templateName":"generic_https","policy":"CERT_AUTH","properties":{"operations":{"get-time":{"path":"/time","method":"GET"}}}}]},
				 {"provider":{"name":"TimeManager"},"serviceDefinition":{"name":"sysMonitor"},"version":"1.0.0","metadata":{},
				  "interfaces":[{"templateName":"generic_amqps","policy":"CERT_AUTH","properties":{"operations":["echo"]}}]},
				 {"provider":{"name":"WfEquipment"},"serviceDefinition":{"name":"temperatureRead"},"version":"1.0.0","metadata":{},
				  "interfaces":[{"templateName":"generic_https","policy":"CERT_AUTH","properties":{"operations":{"run":{"path":"/run","method":"POST"}}}}]},
				 {"provider":{"name":"WfeA"},"serviceDefinition":{"name":"workflowExecution"},"version":"1.0.0",
				  "metadata":{"workflowCapabilities":["agv"],"workflowServices":["*"],"workflowMode":"push"},"interfaces":[]}]}"""));
	}

	@Test
	void consoleNeedsLoginAndLocalHost() throws Exception {
		mvc.perform(get(API + "/clouds").with(console())).andExpect(status().isUnauthorized());
		mvc.perform(post(API + "/session").with(console()).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
		mvc.perform(get("/index.html").with(console())).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("WorkflowConfigurator")))
				.andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")));
		mvc.perform(get("/model.js").with(console())).andExpect(status().isOk());
		mvc.perform(get("/index.html").with(console()).header("Host", "evil.example:" + CONSOLE)).andExpect(status().isForbidden());
		// the console's paths do not exist on the Arrowhead connector
		mvc.perform(get("/index.html")).andExpect(status().isNotFound());
	}

	@Test
	void cloudsCombineRegistryDesignsAndSolcMembers() throws Exception {
		final Cookie session = login();
		mvc.perform(as(get(API + "/clouds"), session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries[0].id").value("live"))
				.andExpect(jsonPath("$.entries[0].name").value("TestCloud"))
				.andExpect(jsonPath("$.entries[0].systems.length()").value(3))
				.andExpect(jsonPath("$.entries[1].id").value("lcc:u1"))
				.andExpect(jsonPath("$.entries[1].name").value("Office"))
				.andExpect(jsonPath("$.entries[1].systems[1].name").value("TimeManager"))
				.andExpect(jsonPath("$.entries[1].systems[1].running").value(true))
				.andExpect(jsonPath("$.entries[1].systems[2].running").value(false))
				.andExpect(jsonPath("$.entries[2].id").value("solc:d1:Plant"))
				.andExpect(jsonPath("$.entries[2].live").value(false))
				.andExpect(jsonPath("$.sources[0].available").value(true));
	}

	@Test
	void anUnavailableSourceIsReportedNotFatal() throws Exception {
		when(upstream.call(eq("soLocalCloudConfiguration"), eq("list-designs"), eq("SoLocalCloudConfigurator"), isNull(Object.class)))
				.thenThrow(new UpstreamException(403, "SoLocalCloudConfigurator", "SoLocalCloudConfigurator soLocalCloudConfiguration.list-designs: forbidden"));
		final Cookie session = login();
		mvc.perform(as(get(API + "/clouds"), session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(2))
				.andExpect(jsonPath("$.sources[?(@.system == 'SoLocalCloudConfigurator')].available").value(false));
	}

	@Test
	void servicesOfADesignAreThoseOfItsSystems() throws Exception {
		final Cookie session = login();
		mvc.perform(as(get(API + "/clouds/lcc:u1/services"), session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(1))
				.andExpect(jsonPath("$.entries[0].provider").value("TimeManager"))
				.andExpect(jsonPath("$.entries[0].serviceDefinition").value("time"))
				.andExpect(jsonPath("$.entries[0].operations[0]").value("get-time"));
		mvc.perform(as(get(API + "/clouds/live/services"), session))
				.andExpect(jsonPath("$.entries[?(@.serviceDefinition == 'temperatureRead')].provider").value("WfEquipment"))
				.andExpect(jsonPath("$.entries[?(@.serviceDefinition == 'sysMonitor')]").isEmpty());
		mvc.perform(as(get(API + "/clouds/solc:d1:Plant/services"), session))
				.andExpect(jsonPath("$.entries").isEmpty())
				.andExpect(jsonPath("$.note").exists());
		mvc.perform(as(get(API + "/clouds/nope/services"), session)).andExpect(status().isNotFound());
		mvc.perform(as(get(API + "/executors"), session))
				.andExpect(jsonPath("$.entries[?(@.name == 'WfeA')].capabilities[0]").value("agv"));
	}

	@Test
	void withoutManagementPermissionDiscoveryIsUsedAndSaid() throws Exception {
		when(upstream.serviceRegistryManagement(eq("system-query"), eq("/serviceregistry/mgmt/systems/query"), any(Object.class)))
				.thenThrow(new UpstreamException(403, "ServiceRegistry", "ServiceRegistry serviceRegistryManagement.system-query: Requester has no management permission"));
		when(upstream.serviceRegistryManagement(eq("service-query"), eq("/serviceregistry/mgmt/service-instances/query"), any(Object.class)))
				.thenThrow(new UpstreamException(403, "ServiceRegistry", "no management permission"));
		when(upstream.call(eq("systemDiscovery"), eq("lookup"), eq("ServiceRegistry"), any(Object.class))).thenReturn(json("{\"entries\":[{\"name\":\"WfeA\"}]}"));
		when(upstream.call(eq("serviceDiscovery"), eq("lookup"), eq("ServiceRegistry"), any(Object.class))).thenReturn(json("""
				{"entries":[{"provider":{"name":"WfeA"},"serviceDefinition":{"name":"workflowExecution"},"version":"1.0.0",
				 "metadata":{"workflowCapabilities":["agv"]},"interfaces":[]}]}"""));
		final Cookie session = login();
		mvc.perform(as(get(API + "/clouds"), session))
				.andExpect(jsonPath("$.entries[0].systems[0].name").value("WfeA"))
				.andExpect(jsonPath("$.note").exists())
				.andExpect(jsonPath("$.sources[?(@.system == 'ServiceRegistry')].operation").value("lookup"));
		mvc.perform(as(get(API + "/clouds/live/services"), session))
				.andExpect(jsonPath("$.entries[0].serviceDefinition").value("workflowExecution"))
				.andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.containsString("Discovery only")));
		mvc.perform(as(get(API + "/executors"), session)).andExpect(jsonPath("$.entries[0].capabilities[0]").value("agv"));
	}

	@Test
	void storeValidatesFirstAndRegistersOnlyAcceptedRecipes() throws Exception {
		final Cookie session = login();
		final String recipe = "{\"name\":\"r\",\"version\":\"1.0.0\",\"firstAction\":\"a\",\"actions\":[]}";
		when(upstream.call(eq("workflowRecipe"), eq("validate"), eq("WorkflowManager"), any(Object.class)))
				.thenReturn(json("{\"recipe\":\"r@1.0.0\",\"verdict\":\"REVISE\",\"findings\":[{\"code\":\"S08\",\"outcome\":\"REVISE\",\"location\":\"recipe\",\"message\":\"no actions\"}]}"));
		mvc.perform(as(post(API + "/recipes"), session).contentType(MediaType.APPLICATION_JSON).content(recipe))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.stored").value(false))
				.andExpect(jsonPath("$.report.findings[0].code").value("S08"));
		verify(upstream, never()).call(eq("workflowRecipe"), eq("register"), eq("WorkflowManager"), any(Object.class));

		when(upstream.call(eq("workflowRecipe"), eq("validate"), eq("WorkflowManager"), any(Object.class))).thenReturn(json("{\"verdict\":\"ACCEPT\",\"findings\":[]}"));
		when(upstream.call(eq("workflowRecipe"), eq("register"), eq("WorkflowManager"), any(Object.class)))
				.thenReturn(json("{\"recipe\":{\"name\":\"r\",\"version\":\"1.0.0\"},\"created\":true}"));
		mvc.perform(as(post(API + "/recipes"), session).contentType(MediaType.APPLICATION_JSON).content(recipe))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.stored").value(true))
				.andExpect(jsonPath("$.recipe.name").value("r"));

		when(upstream.call(eq("workflowRecipe"), eq("register"), eq("WorkflowManager"), any(Object.class)))
				.thenThrow(new UpstreamException(409, "WorkflowManager", "WorkflowManager workflowRecipe.register: Recipe r@1.0.0 already exists with different content"));
		mvc.perform(as(post(API + "/recipes"), session).contentType(MediaType.APPLICATION_JSON).content(recipe))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("already exists")));
	}

	@Test
	void recipesAreListedAndReadFromTheWorkflowManager() throws Exception {
		final Cookie session = login();
		when(upstream.query(eq("workflowRecipe"), eq("query"), eq("WorkflowManager"), any())).thenReturn(json("{\"entries\":[{\"name\":\"r\",\"version\":\"1.0.0\"}],\"count\":1}"));
		when(upstream.call(eq("workflowRecipe"), eq("get"), eq("WorkflowManager"), anyList())).thenReturn(json("{\"name\":\"r\",\"version\":\"1.0.0\",\"recipe\":{}}"));
		mvc.perform(as(get(API + "/recipes"), session)).andExpect(jsonPath("$.count").value(1));
		mvc.perform(as(get(API + "/recipes/r/1.0.0"), session)).andExpect(jsonPath("$.name").value("r"));
		// the list asks for summaries only (a full list exceeded the HTTP client's response buffer in ChipletTest)
		verify(upstream).query(eq("workflowRecipe"), eq("query"), eq("WorkflowManager"),
				org.mockito.ArgumentMatchers.argThat(q -> q != null && List.of("true").equals(q.get("summary"))));
	}
}
