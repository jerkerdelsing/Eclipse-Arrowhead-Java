/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpServer;

import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.workflow.model.Recipe;
import eu.arrowhead.workflow.model.RecipeCodec;
import eu.arrowhead.workflow.wire.LeaseResponse;
import eu.arrowhead.workflow.wire.LeaseStatus;
import eu.arrowhead.workflow.wire.ReportStatus;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflow.wire.WorkItemReport;
import eu.arrowhead.workflowexecutor.runtime.ManagerClient;

/** The WorkflowExecutor against a fake equipment provider; the WorkflowManager client is a mock that records reports. */
@SpringBootTest(classes = WorkflowExecutorMain.class)
@AutoConfigureMockMvc
class WorkflowExecutorTest {

	private static final JsonNodeFactory F = JsonNodeFactory.instance;
	private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
	private static final HttpServer EQUIPMENT = startEquipment();

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ManagerClient manager;

	@MockitoBean
	private ArrowheadHttpService arrowheadHttpService;

	private final ObjectMapper json = RecipeCodec.mapper();

	private static HttpServer startEquipment() {
		try {
			final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/equip", exchange -> {
				final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				RECEIVED.add(exchange.getRequestURI().getPath() + " " + body);
				final String path = exchange.getRequestURI().getPath();
				final int code = path.endsWith("/fail") ? 500 : path.endsWith("/bad") ? 400 : 200;
				final byte[] out = (code == 200 ? "{\"torque\":42.5,\"echo\":" + (body.isEmpty() ? "null" : body) + "}" : "{\"errorMessage\":\"no\",\"errorCode\":" + code + "}")
						.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(code, out.length);
				exchange.getResponseBody().write(out);
				exchange.close();
			});
			server.start();
			return server;
		} catch (final IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@AfterAll
	static void stop() {
		EQUIPMENT.stop(0);
	}

	@DynamicPropertySource
	static void properties(final DynamicPropertyRegistry registry) {
		final String base = "http://127.0.0.1:" + EQUIPMENT.getAddress().getPort() + "/equip";
		registry.add("arrowhead.security.profile", () -> "development");
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:wfe;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("sysmonitor.enabled", () -> "false");
		registry.add("workflow.standalone", () -> "true");
		registry.add("workflow.executor.capabilities", () -> "nutrunner");
		registry.add("workflow.executor.static-providers", () -> "tighten-fasteners|" + base + "|tighten=POST:/tighten;"
				+ "flaky|" + base + "|run=POST:/fail;refusing|" + base + "|run=POST:/bad");
	}

	@BeforeEach
	void reset() {
		RECEIVED.clear();
		Mockito.reset(manager);
	}

	private WorkItem item(final String id, final String service, final Recipe.Policy policy, final JsonNode input, final Map<String, JsonNode> deps) {
		return new WorkItem(id, 7L, "shared-tool-tightening@1.0.0", "use", "tighten", 1, new Recipe.ServiceRequirement(service, null, null, null, null, null, null),
				new Recipe.ExecutorRequirement("nutrunner", null, null), Map.of(), input, policy, 120.0, F.objectNode().put("line", "L2"), deps);
	}

	private void start(final WorkItem item, final String caller, final int expected) throws Exception {
		mvc.perform(post("/workflowexecutor/execution/start").header("Authorization", "Bearer SYSTEM//" + caller).contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(item))).andExpect(status().is(expected));
	}

	private List<WorkItemReport> reports(final int count) {
		final ArgumentCaptor<WorkItemReport> captor = ArgumentCaptor.forClass(WorkItemReport.class);
		verify(manager, timeout(5000).times(count)).report(captor.capture());
		return captor.getAllValues();
	}

	@Test
	void runsAStepBindsThenSucceedsWithComposedInput() throws Exception {
		final JsonNode input = json.readTree("{\"program\":\"${result.configure.program}\",\"line\":\"${param.line}\"}");
		start(item("w1", "tighten-fasteners", null, input, Map.of("configure", F.objectNode().put("program", "P7"))), "WorkflowManager", 202);
		final List<WorkItemReport> reports = reports(2);
		assertEquals(ReportStatus.BOUND, reports.get(0).status());
		assertEquals(ReportStatus.SUCCESS, reports.get(1).status());
		assertEquals(42.5, reports.get(1).result().get("torque").asDouble());
		assertEquals("/equip/tighten {\"program\":\"P7\",\"line\":\"L2\"}", RECEIVED.get(0));
	}

	@Test
	void onlyTheWorkflowManagerMayStart() throws Exception {
		start(item("w2", "tighten-fasteners", null, null, Map.of()), "Intruder", 403);
	}

	@Test
	void noProviderIsBindFailed() throws Exception {
		start(item("w3", "unknown-service", null, null, Map.of()), "WorkflowManager", 202);
		assertEquals(ReportStatus.BIND_FAILED, reports(1).get(0).status());
		assertTrue(RECEIVED.isEmpty());
	}

	@Test
	void providerErrorsMapToErrorAndFatalError() throws Exception {
		start(item("w4", "flaky", null, null, Map.of()), "WorkflowManager", 202);
		start(item("w5", "refusing", null, null, Map.of()), "WorkflowManager", 202);
		final List<WorkItemReport> reports = reports(4);
		final Map<String, ReportStatus> last = new java.util.HashMap<>();
		reports.forEach(r -> last.put(r.workItemId(), r.status()));
		assertEquals(ReportStatus.ERROR, last.get("w4"));
		assertEquals(ReportStatus.FATAL_ERROR, last.get("w5"));
	}

	@Test
	void reserveStepWaitsForTheGrantAndPassesTheToken() throws Exception {
		when(manager.reserve(any())).thenReturn(new LeaseResponse(LeaseStatus.QUEUED, "NR7", null, "t-1", null, null));
		when(manager.poll(any())).thenReturn(new LeaseResponse(LeaseStatus.QUEUED, "NR7", null, "t-1", null, null),
				new LeaseResponse(LeaseStatus.GRANTED, "NR7", "token-1", "t-1", "2026-11-19T12:00:00Z", null));
		final Recipe.Policy reserve = new Recipe.Policy(null, null, null, null, new Recipe.LeasePolicy("NR7", Recipe.LeasePolicy.LeaseMode.reserve), null);
		start(item("w6", "workflowLease", reserve, null, Map.of()), "WorkflowManager", 202);
		final WorkItemReport done = reports(2).get(1);
		assertEquals(ReportStatus.SUCCESS, done.status());
		assertEquals("token-1", done.result().get("leaseToken").asText());
		assertEquals("NR7", done.result().get("leaseResource").asText());
	}

	@Test
	void toolUseWithoutAValidLeaseFailsBeforeTouchingTheEquipment() throws Exception {
		when(manager.renew(any())).thenReturn(new LeaseResponse(LeaseStatus.EXPIRED, "NR7", "token-1", null, null, "No valid lease"));
		final Recipe.Policy use = new Recipe.Policy(null, null, null, null, new Recipe.LeasePolicy("NR7", Recipe.LeasePolicy.LeaseMode.use), null);
		start(item("w7", "tighten-fasteners", use, null, Map.of("reserve", F.objectNode().put("leaseToken", "token-1").put("leaseResource", "NR7"))), "WorkflowManager", 202);
		final WorkItemReport report = reports(1).get(0);
		assertEquals(ReportStatus.FATAL_ERROR, report.status());
		assertTrue(report.message().contains("No valid lease"));
		assertTrue(RECEIVED.isEmpty());
	}

	@Test
	void choreographerCompatibleExecution() throws Exception {
		final int port = EQUIPMENT.getAddress().getPort();
		final String request = """
				{"sessionId":3,"sessionStepId":11,"preconditionOrchestrationResults":[],"quantity":2,
				 "staticParameters":{"operation":"tighten","step":"tight","dep":"cfg,res"},
				 "mainOrchestrationResult":{"providerName":"Nutrunner","serviceDefinitition":"tighten-fasteners","interfaces":[
				   {"templateName":"generic_http","protocol":"http","policy":"NONE","properties":{"accessAddresses":["127.0.0.1"],"accessPort":%d,"basePath":"/equip",
				    "operations":{"tighten":{"method":"POST","path":"/tighten"}}}}]}}""".formatted(port);
		mvc.perform(post("/workflowexecutor/choreographer/start").header("Authorization", "Bearer SYSTEM//WorkflowManager").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isForbidden());
		mvc.perform(post("/workflowexecutor/choreographer/start").header("Authorization", "Bearer SYSTEM//Choreographer").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isOk());
		final ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(arrowheadHttpService, timeout(5000)).consumeService(eq("choreographerExecutor"), eq("notify-step-done"), eq("Choreographer"), eq(Void.class), payload.capture());
		final JsonNode result = json.valueToTree(payload.getValue());
		assertEquals("SUCCESS", result.get("status").asText());
		assertEquals(11, result.get("sessionStepId").asLong());
		assertEquals(2, RECEIVED.size()); // quantity 2
	}
}
