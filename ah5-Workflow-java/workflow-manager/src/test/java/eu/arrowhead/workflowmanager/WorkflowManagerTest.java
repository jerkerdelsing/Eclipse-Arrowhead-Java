/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import eu.arrowhead.common.exception.ForbiddenException;
import eu.arrowhead.common.sysmonitor.SysMonitorPublisher;
import eu.arrowhead.common.sysmonitor.SysMonitorReading;
import eu.arrowhead.workflow.model.session.Markings.Rule;
import eu.arrowhead.workflow.model.session.WorkflowSession;
import eu.arrowhead.workflow.wire.AbortRequest;
import eu.arrowhead.workflow.wire.ExecutionState;
import eu.arrowhead.workflow.wire.ClaimRequest;
import eu.arrowhead.workflow.wire.LeaseRequest;
import eu.arrowhead.workflow.wire.LeaseStatus;
import eu.arrowhead.workflow.wire.ReportStatus;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflow.wire.WorkItemReport;
import eu.arrowhead.workflowmanager.api.dto.StartSessionRequestDTO;
import eu.arrowhead.workflowmanager.engine.WorkflowEngine;
import eu.arrowhead.workflowmanager.jpa.entity.SessionRecord;
import eu.arrowhead.workflowmanager.service.ExecutorDirectory.ExecutorEndpoint;
import eu.arrowhead.workflowmanager.service.ExecutorGateway;
import eu.arrowhead.workflowmanager.service.ExecutorObservations;
import eu.arrowhead.workflowmanager.service.LeaseService;
import eu.arrowhead.workflowmanager.service.RecipeService;

/**
 * The whole WorkflowManager (controllers, filters, JPA on H2, engine) with simulated push executors: WfeAgv has the
 * capability agv, WfeA and WfeB run any service. The simulated executor answers each start like a WorkflowExecutor:
 * BOUND, then SUCCESS, unless the test's behaviour says otherwise.
 */
@SpringBootTest(classes = { WorkflowManagerMain.class, WorkflowManagerTest.SimulatedExecutors.class })
@AutoConfigureMockMvc
class WorkflowManagerTest {

	private static final JsonNodeFactory F = JsonNodeFactory.instance;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WorkflowEngine engine;

	@Autowired
	private RecipeService recipes;

	@Autowired
	private LeaseService leases;

	@Autowired
	private SimulatedExecutors executors;

	@Autowired
	private ExecutorObservations observations;

	/** A publisher that never publishes: the observations stay with their source, where the tests read them. */
	@MockitoBean
	private SysMonitorPublisher publisher;

	private final ObjectMapper json = new ObjectMapper();

	@DynamicPropertySource
	static void properties(final DynamicPropertyRegistry registry) {
		registry.add("arrowhead.security.profile", () -> "development");
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:wfm;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;IGNORECASE=TRUE;CACHE_SIZE=16384;INIT=RUNSCRIPT FROM 'classpath:database/schema.sql';DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		// the schema is the system's own schema.sql (INIT of the url); Hibernate only validates it
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("sysmonitor.enabled", () -> "false");
		registry.add("workflow.standalone", () -> "true");
		registry.add("workflow.pull.claim-timeout", () -> "2");
		registry.add("workflow.executor.liveness.check-seconds", () -> "1");
		registry.add("workflow.executor.liveness.lost-after-seconds", () -> "3");
		// recipes whose executors are absent must still register here, to exercise S-FailBind with asg(z, s) = ∅
		registry.add("workflow.validator.catalogue-check", () -> "false");
		registry.add("workflow.executors", () -> "WfeAgv|http://127.0.0.1:18001|agv|*;WfeA|http://127.0.0.1:18002||*;WfeB|http://127.0.0.1:18003||*");
	}

	/** Simulated push executors; behaviour(executor, item) returns the report to send after BOUND, or a BIND_FAILED report. */
	@TestConfiguration
	static class SimulatedExecutors {

		@Autowired
		@Lazy
		WorkflowEngine engine;

		volatile BiFunction<String, WorkItem, WorkItemReport> behaviour;
		/** workflowExecution.get-status of the simulated executors: executor name -> state, or an exception. */
		volatile Function<String, String> liveness = executor -> "RUNNING";
		final Map<String, String> ranOn = new ConcurrentHashMap<>();
		final Set<String> aborted = ConcurrentHashMap.newKeySet();

		@Bean
		@Primary
		ExecutorGateway simulatedGateway() {
			return new ExecutorGateway() {
				@Override
				public void start(final ExecutorEndpoint executor, final WorkItem item) {
					new Thread(() -> {
						final WorkItemReport outcome = behaviour.apply(executor.name(), item);
						if (outcome == null) {
							return; // the executor disappears before binding
						}
						if (outcome.status() != ReportStatus.BIND_FAILED) {
							engine.report(executor.name(), false, new WorkItemReport(item.workItemId(), ReportStatus.BOUND, F.objectNode().put("provider", "p-" + item.step()), null, null));
							ranOn.put(item.sessionId() + "/" + item.step(), executor.name());
						}
						if (outcome.status() != ReportStatus.BOUND) { // BOUND: the executor disappears after binding
							engine.report(executor.name(), false, outcome);
						}
					}).start();
				}

				@Override
				public void abort(final ExecutorEndpoint executor, final AbortRequest request) {
					aborted.add(request.workItemId());
				}

				@Override
				public ExecutionState status(final ExecutorEndpoint executor, final String workItemId) {
					return new ExecutionState(workItemId, liveness.apply(executor.name()), null);
				}
			};
		}
	}

	private static WorkItemReport success(final WorkItem item) {
		return new WorkItemReport(item.workItemId(), ReportStatus.SUCCESS, null, F.objectNode().put("by", item.step()).put("inputs", item.dependencyResults().size()), null);
	}

	@BeforeEach
	void defaults() throws Exception {
		executors.behaviour = (executor, item) -> success(item);
		executors.liveness = executor -> "RUNNING";
		register("shared-tool-tightening.recipe.json");
		register("temperature-control.recipe.json");
	}

	private void register(final String example) throws Exception {
		recipes.register(json.readTree(read("/examples/" + example)), "test");
	}

	private static String read(final String resource) throws java.io.IOException {
		try (InputStream in = WorkflowManagerTest.class.getResourceAsStream(resource)) {
			return new String(in.readAllBytes());
		}
	}

	private WorkflowSession awaitEnd(final long sessionId) throws InterruptedException {
		for (int i = 0; i < 200; i++) {
			final SessionRecord s = engine.get(sessionId);
			if (!"running".equals(s.getStatus())) {
				return engine.state(s);
			}
			Thread.sleep(50);
		}
		throw new AssertionError("Session " + sessionId + " did not end");
	}

	private long start(final String recipe, final JsonNode parameters, final String dispatch) {
		return engine.start("LineController", new StartSessionRequestDTO(recipe, null, "unit-1", parameters, dispatch, null)).getId();
	}

	@Test
	void forkliftSessionCompletesInThirtyThreeTransitions() throws Exception {
		final long id = start("shared-tool-tightening", F.objectNode().put("line", "L2").put("product", "P1"), null);
		final WorkflowSession z = awaitEnd(id);
		assertEquals("completed", z.status().name(), z::failure);
		assertEquals(33, z.modelTransitionCount());
		assertTrue(z.extensions().isEmpty());
		// η(move) = agv: only wfe-agv satisfies it, and ε records it
		assertEquals("WfeAgv", executors.ranOn.get(id + "/move"));
		assertEquals("WfeAgv", z.executor("back"));
		// ξ|dep(evaluate) = {tighten, program}
		assertEquals(2, z.result("evaluate").get("inputs").asInt());

		mvc.perform(get("/workflowmanager/instance/sessions/" + id + "/trace").header("Authorization", "Bearer SYSTEM//Operator"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modelTransitions").value(33))
				.andExpect(jsonPath("$.transitionBound").value(33))
				.andExpect(jsonPath("$.withinModel").value(true));
	}

	@Test
	void bindFailureMovesToTheNextExecutorWithoutLeavingTheModel() throws Exception {
		executors.behaviour = (executor, item) -> "read".equals(item.step()) && !executor.equals("WfeB")
				? new WorkItemReport(item.workItemId(), ReportStatus.BIND_FAILED, null, null, "no provider authorized for " + executor)
				: success(item);
		final long id = start("temperature-control", F.objectNode().put("threshold", 23.0), null);
		final WorkflowSession z = awaitEnd(id);
		assertEquals("completed", z.status().name(), z::failure);
		assertEquals(11, z.modelTransitionCount());
		assertEquals("WfeB", executors.ranOn.get(id + "/read"));
	}

	@Test
	void fatalErrorIsFailInvokeAfterNineTransitions() throws Exception {
		executors.behaviour = (executor, item) -> "switch".equals(item.step())
				? new WorkItemReport(item.workItemId(), ReportStatus.FATAL_ERROR, null, null, "heating switch does not answer")
				: success(item);
		final long id = start("temperature-control", F.objectNode().put("threshold", 23.0), null);
		final WorkflowSession z = awaitEnd(id);
		assertEquals("failed", z.status().name());
		assertEquals(9, z.modelTransitionCount());
		assertEquals(Rule.S_FAIL_INVOKE, z.trace().get(z.trace().size() - 1).rule());
	}

	@Test
	void noExecutorWithTheCapabilityIsFailBind() throws Exception {
		recipes.register(json.readTree("""
				{"name":"needs-robot","version":"1.0.0","firstAction":"a","actions":[{"name":"a","steps":[
				 {"name":"weld","service":{"serviceDefinition":"weld"},"executor":{"capability":"robot"}}]}]}"""), "test");
		final WorkflowSession z = awaitEnd(start("needs-robot", null, null));
		assertEquals(Rule.S_FAIL_BIND, z.trace().get(z.trace().size() - 1).rule());
		assertEquals(2, z.modelTransitionCount()); // S-Enable, S-FailBind
	}

	@Test
	void pullModeExecutorsClaimAndOnlyTheHolderMayReport() throws Exception {
		final long id = start("temperature-control", F.objectNode().put("threshold", 23.0), "pull");
		for (final String step : List.of("read", "evaluate", "switch")) {
			WorkItem item = null;
			for (int i = 0; i < 100 && item == null; i++) {
				item = engine.claim("wfe-pull", new ClaimRequest(List.of(), List.of("*"), null)).orElse(null);
				if (item == null) {
					Thread.sleep(20);
				}
			}
			assertEquals(step, item.step());
			final WorkItem held = item;
			assertThrows(ForbiddenException.class, () -> engine.report("intruder", false, new WorkItemReport(held.workItemId(), ReportStatus.SUCCESS, null, null, null)));
			engine.report("wfe-pull", false, new WorkItemReport(item.workItemId(), ReportStatus.BOUND, F.objectNode().put("p", 1), null, null));
			engine.report("wfe-pull", false, success(item));
		}
		assertEquals(11, awaitEnd(id).modelTransitionCount());
	}

	@Test
	void unclaimedPullItemEndsInFailBind() throws Exception {
		final long id = start("temperature-control", F.objectNode().put("threshold", 23.0), "pull");
		Thread.sleep(200);
		final WorkflowSession z = awaitEndSlow(id);
		assertEquals(Rule.S_FAIL_BIND, z.trace().get(z.trace().size() - 1).rule());
	}

	private WorkflowSession awaitEndSlow(final long id) throws InterruptedException {
		for (int i = 0; i < 20; i++) {
			if (!"running".equals(engine.get(id).getStatus())) {
				break;
			}
			Thread.sleep(500);
		}
		return awaitEnd(id);
	}

	@Test
	void leasesAreFifoAndExpire() throws Exception {
		final var a = leases.reserve("L1", new LeaseRequest("NR-test", 1L, 0.6, null, null));
		final var b = leases.reserve("L2", new LeaseRequest("NR-test", 2L, 0.6, null, null));
		assertEquals(LeaseStatus.GRANTED, a.status());
		assertEquals(LeaseStatus.QUEUED, b.status());
		assertEquals(LeaseStatus.GRANTED, leases.renew(new LeaseRequest(null, null, null, null, a.token())).status());
		assertEquals(LeaseStatus.QUEUED, leases.poll(new LeaseRequest(null, null, null, b.ticket(), null)).status());
		Thread.sleep(900); // a is neither renewed nor released: it expires
		final var granted = leases.poll(new LeaseRequest(null, null, null, b.ticket(), null));
		assertEquals(LeaseStatus.GRANTED, granted.status());
		assertEquals(LeaseStatus.EXPIRED, leases.renew(new LeaseRequest(null, null, null, null, a.token())).status());
		assertEquals(LeaseStatus.RELEASED, leases.release(new LeaseRequest(null, null, null, null, granted.token())).status());
	}

	@Test
	void recipeQueryCanLeaveOutTheDocuments() throws Exception {
		mvc.perform(get("/workflowmanager/recipe/recipes").header("Authorization", "Bearer SYSTEM//Engineer"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries[0].recipe.actions").exists());
		mvc.perform(get("/workflowmanager/recipe/recipes?summary=true").header("Authorization", "Bearer SYSTEM//Engineer"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThan(0)))
				.andExpect(jsonPath("$.entries[0].name").exists())
				.andExpect(jsonPath("$.entries[0].version").exists())
				.andExpect(jsonPath("$.entries[0].recipe").doesNotExist());
	}

	@Test
	void recipesAreImmutableAndValidatedOverHttp() throws Exception {
		final String changed = read("/examples/temperature-control.recipe.json").replace("heating-switch", "other-switch");
		mvc.perform(post("/workflowmanager/recipe/register").header("Authorization", "Bearer SYSTEM//Engineer").contentType(MediaType.APPLICATION_JSON).content(changed))
				.andExpect(status().isConflict());
		mvc.perform(post("/workflowmanager/recipe/register").header("Authorization", "Bearer SYSTEM//Engineer").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"bad","version":"1.0.0","firstAction":"a","actions":[{"name":"a","steps":[
						 {"name":"s1","service":{"serviceDefinition":"x"},"dep":["s2"]},{"name":"s2","service":{"serviceDefinition":"x"}}]}]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.validation.verdict").value("REVISE"))
				.andExpect(jsonPath("$.validation.findings[0].code").value("D02"));
		mvc.perform(post("/workflowmanager/recipe/compile").header("Authorization", "Bearer SYSTEM//Engineer").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"shared-tool-tightening\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstActionName").value("provision"))
				.andExpect(jsonPath("$.actions[0].firstStepNames[0]").value("reserve"));
	}

	@Test
	void abortIsOwnerOnlyAndNotifiesExecutors() throws Exception {
		executors.behaviour = (executor, item) -> {
			try {
				Thread.sleep(400);
			} catch (final InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return success(item);
		};
		final long id = start("temperature-control", F.objectNode().put("threshold", 23.0), null);
		Thread.sleep(100);
		assertThrows(ForbiddenException.class, () -> engine.abort(id, "SomeoneElse", false, null));
		engine.abort(id, "LineController", false, "operator stop");
		final WorkflowSession z = awaitEnd(id);
		assertTrue(z.extensions().contains("abort"));
		assertFalse(executors.aborted.isEmpty());
	}
	//-------------------------------------------------------------------------------------------------
	// ERROR with a retry policy: rebind (another executor) and retry (the same executor after a backoff)

	private record Attempt(String executor, long atMillis) {
	}

	/** One step "poll"; ERROR for the first errors attempts (or always when errors < 0), then SUCCESS. */
	private List<Attempt> flaky(final String name, final String retryPolicy, final int errors) throws Exception {
		recipes.register(json.readTree("""
				{"name":"%s","version":"1.0.0","firstAction":"a","actions":[{"name":"a","steps":[
				 {"name":"poll","service":{"serviceDefinition":"poll"},"policy":{"retry":%s}}]}]}""".formatted(name, retryPolicy)), "test");
		final List<Attempt> attempts = Collections.synchronizedList(new ArrayList<>());
		final AtomicInteger n = new AtomicInteger();
		executors.behaviour = (executor, item) -> {
			attempts.add(new Attempt(executor, System.currentTimeMillis()));
			return errors < 0 || n.incrementAndGet() <= errors ? new WorkItemReport(item.workItemId(), ReportStatus.ERROR, null, null, "provider unreachable") : success(item);
		};
		return attempts;
	}

	private static long count(final WorkflowSession z, final Rule rule) {
		return z.trace().stream().filter(t -> t.rule() == rule).count();
	}

	@Test
	void retryRunsTheSameExecutorAgainAfterTheBackoff() throws Exception {
		final List<Attempt> attempts = flaky("flaky-retry", "{\"maxAttempts\":3,\"onError\":\"retry\",\"backoffSeconds\":0.3}", 2);
		final WorkflowSession z = awaitEnd(start("flaky-retry", null, null));
		assertEquals("completed", z.status().name(), z::failure);
		assertEquals(3, attempts.size());
		assertEquals(1, attempts.stream().map(Attempt::executor).distinct().count(), attempts::toString);
		for (int i = 1; i < attempts.size(); i++) {
			assertTrue(attempts.get(i).atMillis() - attempts.get(i - 1).atMillis() >= 300, attempts::toString);
		}
		assertEquals(2, count(z, Rule.X_RETRY));
		assertEquals(0, count(z, Rule.X_REBIND));
		assertTrue(z.extensions().contains("retry"));
	}

	@Test
	void retryEndsInFailInvokeWhenTheAttemptsAreUsedUp() throws Exception {
		final List<Attempt> attempts = flaky("flaky-exhausted", "{\"maxAttempts\":2,\"onError\":\"retry\",\"backoffSeconds\":0.1}", -1);
		final WorkflowSession z = awaitEnd(start("flaky-exhausted", null, null));
		assertEquals("failed", z.status().name());
		assertEquals(2, attempts.size());
		assertEquals(1, attempts.stream().map(Attempt::executor).distinct().count(), attempts::toString);
		assertEquals(Rule.S_FAIL_INVOKE, z.trace().get(z.trace().size() - 1).rule());
	}

	@Test
	void rebindStillMovesToAnotherExecutor() throws Exception {
		final List<Attempt> attempts = flaky("flaky-rebind", "{\"maxAttempts\":2,\"onError\":\"rebind\"}", 1);
		final WorkflowSession z = awaitEnd(start("flaky-rebind", null, null));
		assertEquals("completed", z.status().name(), z::failure);
		assertEquals(2, attempts.size());
		assertFalse(attempts.get(0).executor().equals(attempts.get(1).executor()), attempts::toString);
		assertEquals(1, count(z, Rule.X_REBIND));
	}

	@Test
	void bindFailureUnderARetryPolicyStillMovesToAnotherExecutor() throws Exception {
		recipes.register(json.readTree("""
				{"name":"bind-under-retry","version":"1.0.0","firstAction":"a","actions":[{"name":"a","steps":[
				 {"name":"poll","service":{"serviceDefinition":"poll"},"policy":{"retry":{"maxAttempts":3,"onError":"retry","backoffSeconds":0.1}}}]}]}"""), "test");
		final List<String> tried = Collections.synchronizedList(new ArrayList<>());
		executors.behaviour = (executor, item) -> {
			tried.add(executor);
			return tried.size() == 1 ? new WorkItemReport(item.workItemId(), ReportStatus.BIND_FAILED, null, null, "no provider for " + executor) : success(item);
		};
		final WorkflowSession z = awaitEnd(start("bind-under-retry", null, null));
		assertEquals("completed", z.status().name(), z::failure);
		assertEquals(2, tried.size());
		assertFalse(tried.get(0).equals(tried.get(1)), tried::toString);
		assertEquals(0, count(z, Rule.X_RETRY));
	}

	@Test
	void retryStaysOnItsExecutorEvenWhenThatExecutorIsBusier() throws Exception {
		recipes.register(json.readTree("""
				{"name":"sticky-retry","version":"1.0.0","firstAction":"a","actions":[{"name":"a","steps":[
				 {"name":"poll","service":{"serviceDefinition":"poll"},"policy":{"retry":{"maxAttempts":2,"onError":"retry","backoffSeconds":0.6}}}]}]}"""), "test");
		recipes.register(json.readTree("""
				{"name":"occupy","version":"1.0.0","firstAction":"a","actions":[{"name":"a","steps":[
				 {"name":"hold","service":{"serviceDefinition":"hold"}}]}]}"""), "test");
		final List<String> polls = Collections.synchronizedList(new ArrayList<>());
		final List<String> holds = Collections.synchronizedList(new ArrayList<>());
		executors.behaviour = (executor, item) -> {
			if ("hold".equals(item.step())) { // occupies its executor (DISPATCHED) while the retry waits
				holds.add(executor);
				sleep(2000);
				return success(item);
			}
			polls.add(executor);
			if (polls.size() == 1) {
				new Thread(() -> { sleep(150); start("occupy", null, null); }).start();
				return new WorkItemReport(item.workItemId(), ReportStatus.ERROR, null, null, "provider restarting");
			}
			return success(item);
		};
		final WorkflowSession z = awaitEnd(start("sticky-retry", null, null));
		assertEquals("completed", z.status().name(), z::failure);
		assertEquals(List.of(polls.get(0), polls.get(0)), polls);
		assertEquals(List.of(polls.get(0)), holds, "the occupying step must have taken the retrying executor, else the test proves nothing");
	}

	@Test
	void aRetryPolicyWithoutOnErrorRebinds() throws Exception {
		final List<Attempt> attempts = flaky("flaky-default", "{\"maxAttempts\":2}", 1);
		final WorkflowSession z = awaitEnd(start("flaky-default", null, null));
		assertEquals("completed", z.status().name(), z::failure);
		assertEquals(2, attempts.size());
		assertEquals(1, count(z, Rule.X_REBIND));
	}

	//-------------------------------------------------------------------------------------------------
	// executors that stop on their own or disappear

	/** One step "poll"; the first attempt ends with firstOutcome (null: nothing is reported), later attempts succeed. */
	private List<String> vanishing(final String name, final String policy, final Function<WorkItem, WorkItemReport> firstOutcome) throws Exception {
		recipes.register(json.readTree("""
				{"name":"%s","version":"1.0.0","firstAction":"a","actions":[{"name":"a","steps":[
				 {"name":"poll","service":{"serviceDefinition":"poll"}%s}]}]}""".formatted(name, policy)), "test");
		final List<String> tried = Collections.synchronizedList(new ArrayList<>());
		executors.behaviour = (executor, item) -> {
			tried.add(executor);
			return tried.size() == 1 ? firstOutcome.apply(item) : success(item);
		};
		return tried;
	}

	private static WorkItemReport boundOnly(final WorkItem item) {
		return new WorkItemReport(item.workItemId(), ReportStatus.BOUND, F.objectNode().put("provider", "p"), null, null);
	}

	private static final String REBIND = ",\"policy\":{\"retry\":{\"maxAttempts\":2,\"onError\":\"rebind\"}}";

	@Test
	void anExecutorThatAbortsOnItsOwnFailsTheSession() throws Exception {
		vanishing("self-abort", "", item -> new WorkItemReport(item.workItemId(), ReportStatus.ABORTED, null, null, "stopped at the executor"));
		final WorkflowSession z = awaitEnd(start("self-abort", null, null));
		assertEquals("failed", z.status().name());
		assertEquals(Rule.S_FAIL_INVOKE, z.trace().get(z.trace().size() - 1).rule());
		assertTrue(z.failure().contains("ABORTED"), z::failure);
	}

	@Test
	void aBoundStepOfAnExecutorThatForgotItFails() throws Exception {
		vanishing("forgotten", "", WorkflowManagerTest::boundOnly);
		executors.liveness = executor -> "UNKNOWN";
		final WorkflowSession z = awaitEnd(start("forgotten", null, null));
		assertEquals("failed", z.status().name());
		assertEquals(Rule.S_FAIL_INVOKE, z.trace().get(z.trace().size() - 1).rule());
	}

	@Test
	void aLostExecutorFollowsTheRetryPolicy() throws Exception {
		final List<String> tried = vanishing("forgotten-rebind", REBIND, WorkflowManagerTest::boundOnly);
		executors.liveness = executor -> "UNKNOWN";
		final WorkflowSession z = awaitEnd(start("forgotten-rebind", null, null));
		assertEquals("completed", z.status().name(), z::failure);
		assertEquals(2, tried.size());
		assertFalse(tried.get(0).equals(tried.get(1)), tried::toString);
		assertEquals(1, count(z, Rule.X_REBIND));
		final Map<String, Object> lost = lostOf("forgotten-rebind").value();
		assertEquals("rebind", lost.get("outcome"));
		assertEquals("notRunning", lost.get("cause"));
		assertEquals(tried.get(0), lost.get("executor"));
		assertEquals(true, lost.get("equipmentMayBeInUse"));
		assertEquals(true, lost.get("workRepeated"));
	}

	@Test
	void aWorkItemLostBeforeBindingGoesToTheNextExecutor() throws Exception {
		final List<String> tried = vanishing("lost-unbound", "", item -> null);
		executors.liveness = executor -> "UNKNOWN";
		final WorkflowSession z = awaitEnd(start("lost-unbound", null, null));
		assertEquals("completed", z.status().name(), z::failure);
		assertEquals(2, tried.size());
		assertFalse(tried.get(0).equals(tried.get(1)), tried::toString);
		assertTrue(z.extensions().isEmpty(), z.extensions()::toString);
		final Map<String, Object> lost = lostOf("lost-unbound").value();
		assertEquals("nextExecutor", lost.get("outcome"));
		assertEquals(false, lost.get("equipmentMayBeInUse"));
		assertEquals(false, lost.get("workRepeated"));
	}

	@Test
	void anUnreachableExecutorIsLostOnlyAfterTheGracePeriod() throws Exception {
		vanishing("unreachable", "", WorkflowManagerTest::boundOnly);
		executors.liveness = executor -> { throw new eu.arrowhead.common.exception.ExternalServerError("connection refused"); };
		final long begin = System.currentTimeMillis();
		final long id = start("unreachable", null, null);
		// the suspicion is published before the grace period ends
		while (!"suspect".equals(liveness().get("state"))) {
			assertTrue(System.currentTimeMillis() - begin < 5000);
			sleep(50);
		}
		final Map<?, ?> suspected = (Map<?, ?>) ((List<?>) liveness().get("suspectedExecutors")).get(0);
		assertEquals("unreachable", suspected.get("cause"));
		assertEquals("poll", ((Map<?, ?>) ((List<?>) suspected.get("workItems")).get(0)).get("step"));
		assertEquals("running", engine.get(id).getStatus());
		Mockito.verify(publisher, Mockito.atLeastOnce()).publishSourcesNow();
		final WorkflowSession z = awaitEnd(id);
		assertEquals("failed", z.status().name());
		assertTrue(System.currentTimeMillis() - begin >= 3000);
		final SysMonitorReading event = lostOf("unreachable");
		assertEquals("sessionFailed", event.value().get("outcome"));
		assertEquals("unreachable", event.value().get("cause"));
		assertTrue(java.time.Instant.parse((String) event.value().get("lastSeen")).isBefore(java.time.Instant.parse((String) event.value().get("suspectedSince"))));
		assertTrue((double) event.value().get("detectionSeconds") >= 3);
		assertEquals(0, ((List<?>) liveness().get("suspectedExecutors")).size());
		// the event is offered until the publisher confirms it
		assertTrue(observations.events().contains(event));
		observations.published(event);
		assertFalse(observations.events().contains(event));
	}

	@Test
	void aRunningStepOrARefusedStatusCallIsNotLost() throws Exception {
		vanishing("long-running", "", WorkflowManagerTest::boundOnly);
		final long running = start("long-running", null, null);
		sleep(5000);
		assertEquals("running", engine.get(running).getStatus());
		executors.liveness = executor -> { throw new ForbiddenException("no grant for get-status"); };
		sleep(5000);
		assertEquals("running", engine.get(running).getStatus());
		assertEquals("unchecked", liveness().get("state"));
		assertFalse(((List<?>) liveness().get("uncheckedExecutors")).isEmpty());
		engine.abort(running, "LineController", false, "test over");
	}

	private Map<String, Object> liveness() {
		return observations.state().get(0).value();
	}

	private SysMonitorReading lostOf(final String recipe) {
		for (int i = 0; i < 100; i++) {
			final var found = observations.events().stream().filter(e -> (recipe + "@1.0.0").equals(e.value().get("recipe"))).findFirst();
			if (found.isPresent()) {
				return found.get();
			}
			sleep(50);
		}
		throw new AssertionError("no workItemLost for " + recipe + ": " + observations.events());
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}
