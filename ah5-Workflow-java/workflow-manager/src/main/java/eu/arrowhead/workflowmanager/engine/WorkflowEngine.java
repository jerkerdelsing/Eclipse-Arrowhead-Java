/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.engine;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.DataNotFoundException;
import eu.arrowhead.common.exception.ForbiddenException;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.workflow.model.Recipe;
import eu.arrowhead.workflow.model.Recipe.Step;
import eu.arrowhead.workflow.model.RecipeCodec;
import eu.arrowhead.workflow.model.session.Markings.SessionStatus;
import eu.arrowhead.workflow.model.session.Markings.StepMarking;
import eu.arrowhead.workflow.model.session.RuleViolationException;
import eu.arrowhead.workflow.model.session.SessionSnapshot;
import eu.arrowhead.workflow.model.session.WorkflowSession;
import eu.arrowhead.workflow.wire.AbortRequest;
import eu.arrowhead.workflow.wire.ClaimRequest;
import eu.arrowhead.workflow.wire.PutResultRequest;
import eu.arrowhead.workflow.wire.ReportStatus;
import eu.arrowhead.workflow.wire.WorkItem;
import eu.arrowhead.workflow.wire.WorkItemReport;
import eu.arrowhead.workflowmanager.WorkflowManagerConstants;
import eu.arrowhead.workflowmanager.api.dto.StartSessionRequestDTO;
import eu.arrowhead.workflowmanager.jpa.entity.RecipeRecord;
import eu.arrowhead.workflowmanager.jpa.entity.SessionRecord;
import eu.arrowhead.workflowmanager.jpa.entity.WorkItemRecord;
import eu.arrowhead.workflowmanager.jpa.entity.WorkItemRecord.State;
import eu.arrowhead.workflowmanager.jpa.repository.SessionRepository;
import eu.arrowhead.workflowmanager.jpa.repository.WorkItemRepository;
import eu.arrowhead.workflowmanager.service.ExecutorDirectory;
import eu.arrowhead.workflowmanager.service.ExecutorDirectory.ExecutorEndpoint;
import eu.arrowhead.workflowmanager.service.ExecutorGateway;
import eu.arrowhead.workflowmanager.service.LeaseService;
import eu.arrowhead.workflowmanager.service.ProductGateway;
import eu.arrowhead.workflowmanager.service.RecipeService;
import eu.arrowhead.workflowmanager.service.WorkflowConflictException;
import eu.arrowhead.workflowmanager.service.WorklogService;

/**
 * Runs workflow sessions with the transition rules of the Sensors paper (§5.9), held by {@link WorkflowSession}.
 *
 * <ul>
 * <li>A-Start, S-Enable, A-Finish and Z-Close are applied here ({@link WorkflowSession#advance()}).</li>
 * <li>An enabled step becomes a work item: dispatched to one executor (push) or published for claiming (pull). The
 * executor orchestrates θ(s) as consumer and reports BOUND (S-Bind), then SUCCESS (S-Done) or FATAL_ERROR
 * (S-FailInvoke).</li>
 * <li>BIND_FAILED or an unreachable executor moves the step to the next executor in asg(z, s); when none is left,
 * S-FailBind applies (vacuously when no executor satisfies η(s)).</li>
 * <li>ERROR re-binds only when the step has a retry policy (extension x-rebind); otherwise it is S-FailInvoke. With
 * onError = retry the same executor gets the step again after the backoff (extension x-retry): the attempt waits in
 * RETRY_WAIT, which survives a restart and is released by a delayed task or, failing that, the watchdog.</li>
 * </ul>
 * Every change is persisted as a snapshot of z with the trace. Access to one session is serialised by a lock; calls to
 * executors happen outside the lock.
 */
@Component
public class WorkflowEngine {

	private static final JsonNodeFactory F = JsonNodeFactory.instance;

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private SessionRepository sessions;

	@Autowired
	private WorkItemRepository items;

	@Autowired
	private RecipeService recipes;

	@Autowired
	private ExecutorDirectory directory;

	@Autowired
	private ExecutorGateway gateway;

	@Autowired
	private LeaseService leases;

	@Autowired
	private WorklogService worklog;

	@Autowired
	private ProductGateway product;

	@Autowired
	@Qualifier(WorkflowManagerConstants.WORKER)
	private TaskExecutor worker;

	@Value(WorkflowManagerConstants.$DEFAULT_DISPATCH_WD)
	private String defaultDispatch;

	@Value(WorkflowManagerConstants.$CLAIM_TIMEOUT_WD)
	private long claimTimeoutSeconds;

	@Value(WorkflowManagerConstants.$LEASE_RELEASE_ON_FAILURE_WD)
	private boolean releaseLeasesOnFailure;

	@Value(WorkflowManagerConstants.$WORKSTATION_ID_WD)
	private String workstationId;

	private final Map<Long, Object> locks = new ConcurrentHashMap<>();

	/** Loaded session: record, recipe, state z and ρ. */
	private record Ctx(SessionRecord record, Recipe recipe, WorkflowSession z, JsonNode parameters) {
	}

	//=================================================================================================
	// workflowInstance

	public SessionRecord start(final String owner, final StartSessionRequestDTO request) {
		if (request == null) {
			throw new InvalidParameterException("Request is missing");
		}
		final RecipeRecord recipeRecord;
		JsonNode parameters = request.parameters();
		String subject = request.subject();
		if (Utilities.isEmpty(request.recipe()) && !Utilities.isEmpty(request.product())) {
			final JsonNode order = product.retrieveProcessStep(request.product(), workstationId);
			final RecipeService.Registration registration = recipes.register(order.get("recipe"), request.product());
			if (registration.record() == null) {
				throw new InvalidParameterException("Recipe from product " + request.product() + " was not accepted: " + registration.report().findings());
			}
			recipeRecord = registration.record();
			if (parameters == null || parameters.isNull()) {
				parameters = order.get("parameters");
			}
			if (Utilities.isEmpty(subject) && order.hasNonNull("subject")) {
				subject = order.get("subject").asText();
			}
		} else {
			recipeRecord = recipes.resolveActive(request.recipe(), request.version());
		}
		final Recipe recipe = recipes.recipeOf(recipeRecord);
		final JsonNode rho = parameters == null || parameters.isNull() ? F.objectNode() : parameters;
		if (!rho.isObject()) {
			throw new InvalidParameterException("Session parameters must be a JSON object");
		}
		for (final Recipe.Parameter p : recipe.parameters()) {
			if (p.isRequired() && !rho.has(p.name())) {
				throw new InvalidParameterException("Session parameter '" + p.name() + "' is required by " + recipe.key());
			}
		}
		final String dispatch = !Utilities.isEmpty(request.dispatch()) ? request.dispatch()
				: recipe.dispatch() != null ? recipe.dispatch().name() : defaultDispatch;
		if (!Set.of("push", "pull", "event").contains(dispatch)) {
			throw new InvalidParameterException("Unknown dispatch mode: " + dispatch);
		}
		final WorkflowSession z = WorkflowSession.start(recipe);
		final SessionRecord record = sessions.saveAndFlush(new SessionRecord(recipeRecord.getId(), recipe.name(), recipe.version(), subject, owner, dispatch,
				RecipeCodec.write(rho), json(z.snapshot()), request.product()));
		worklog.log(record.getId(), recipe.key(), null, "Session started by " + owner + " (" + dispatch + ")", subject);
		worker.execute(() -> drive(record.getId()));
		return record;
	}

	public SessionRecord abort(final long sessionId, final String requester, final boolean sysop, final String reason) {
		final List<Runnable> outbound = new ArrayList<>();
		final SessionRecord result;
		synchronized (lock(sessionId)) {
			final Ctx c = load(sessionId);
			if (!sysop && !c.record().getOwner().equals(requester)) {
				throw new ForbiddenException("Only the owner of session " + sessionId + " or a sysop may abort it");
			}
			if (!c.z().isRunning()) {
				throw new WorkflowConflictException("Session " + sessionId + " is already " + c.z().status());
			}
			c.z().abort(Utilities.isEmpty(reason) ? "aborted by " + requester : reason);
			afterFailure(c, outbound);
			result = save(c);
		}
		outbound.forEach(this::runQuietly);
		return result;
	}

	public SessionRecord get(final long sessionId) {
		return sessions.findById(sessionId).orElseThrow(() -> new DataNotFoundException("Session not found: " + sessionId));
	}

	public WorkflowSession state(final SessionRecord record) {
		return WorkflowSession.restore(recipes.recipeById(record.getRecipeId()), snapshot(record));
	}

	//=================================================================================================
	// workflowWorkItem

	/** Pull mode: the first open work item the requester can run (w ⊧ η(s), or it declares θ(s)). */
	public Optional<WorkItem> claim(final String requester, final ClaimRequest request) {
		final ClaimRequest req = request == null ? new ClaimRequest(null, null, null) : request;
		final ExecutorEndpoint claimant = new ExecutorEndpoint(requester, null, null, 0, null, new LinkedHashSet<>(req.capabilities()), new LinkedHashSet<>(req.services()),
				req.workstation(), "pull", "claim");
		for (final WorkItemRecord candidate : items.findAllByStateInOrderByIdAsc(List.of(State.OPEN.name()))) {
			synchronized (lock(candidate.getSessionId())) {
				final WorkItemRecord item = items.findById(candidate.getId()).orElseThrow();
				if (item.getState() != State.OPEN || excluded(item).contains(requester)) {
					continue;
				}
				final Ctx c = load(item.getSessionId());
				if (!c.z().isRunning() || c.z().marking(item.getStepName()) != StepMarking.enabled) {
					item.setState(State.ABORTED);
					items.saveAndFlush(item);
					continue;
				}
				if (!claimant.canRun(c.z().graph().step(item.getStepName()))) {
					continue;
				}
				item.setExecutorName(requester);
				item.setState(State.CLAIMED);
				items.saveAndFlush(item);
				worklog.log(c.record().getId(), c.recipe().key(), item.getStepName(), "Claimed by " + requester, null);
				return Optional.of(workItem(c, item));
			}
		}
		return Optional.empty();
	}

	public void report(final String requester, final boolean sysop, final WorkItemReport report) {
		if (report == null || Utilities.isEmpty(report.workItemId()) || report.status() == null) {
			throw new InvalidParameterException("workItemId and status are required");
		}
		final WorkItemRecord found = items.findByUid(report.workItemId()).orElseThrow(() -> new DataNotFoundException("Work item not found: " + report.workItemId()));
		if (!sysop && !requester.equals(found.getExecutorName())) {
			throw new ForbiddenException("Work item " + report.workItemId() + " is held by another executor");
		}
		final long sessionId = found.getSessionId();
		final List<Runnable> outbound = new ArrayList<>();
		boolean redrive = false;
		long retryAfterMillis = -1;
		synchronized (lock(sessionId)) {
			final WorkItemRecord item = items.findById(found.getId()).orElseThrow();
			if (!item.getState().isActive()) {
				if (report.status() == ReportStatus.ABORTED || item.getState() == State.ABORTED) {
					return; // acknowledgement, or a late report of an aborted item
				}
				throw new WorkflowConflictException("Work item " + item.getUid() + " is " + item.getState());
			}
			if (item.getState() == State.RETRY_WAIT) {
				throw new WorkflowConflictException("Work item " + item.getUid() + " already ended with ERROR and waits for its retry");
			}
			final Ctx c = load(sessionId);
			if (!c.z().isRunning()) {
				item.setState(State.ABORTED);
				items.saveAndFlush(item);
				return;
			}
			final String step = item.getStepName();
			final Step st = c.z().graph().step(step);
			final String executor = item.getExecutorName();
			item.setMessage(report.message());
			switch (report.status()) {
			case BOUND -> {
				c.z().bind(step, executor, binding(report));
				item.setState(State.BOUND);
				worklog.log(sessionId, c.recipe().key(), step, "S-Bind by " + executor, report.binding() == null ? null : report.binding().toString());
			}
			case BIND_FAILED -> {
				item.setState(State.FAILED);
				worklog.log(sessionId, c.recipe().key(), step, "Orchestration for " + executor + " gave no provider", report.message());
				redrive = true; // next executor in asg(z, s), or S-FailBind
			}
			case SUCCESS -> {
				if (c.z().marking(step) == StepMarking.enabled) {
					c.z().bind(step, executor, binding(report));
				}
				JsonNode result = report.result();
				if ((result == null || result.isNull()) && item.getPendingResult() != null) {
					result = parse(item.getPendingResult());
				}
				c.z().done(step, result == null ? NullNode.getInstance() : result);
				item.setState(State.DONE);
				worklog.log(sessionId, c.recipe().key(), step, "S-Done by " + executor, null);
				redrive = true;
			}
			case ERROR -> {
				item.setState(State.FAILED);
				final Recipe.Retry retry = st.policyOrDefault().retry();
				if (retry != null && retry.onError() == Recipe.Retry.OnError.retry && item.getAttempt() < retry.maxAttemptsOrOne()) {
					// x-retry: same executor after the backoff; RETRY_WAIT keeps the step from being dispatched meanwhile
					item.setState(State.RETRY_WAIT);
					c.z().retry(step, "ERROR from " + executor + ": " + report.message());
					worklog.log(sessionId, c.recipe().key(), step, "x-retry on " + executor + " in " + retry.backoffMillis() + " ms after ERROR (attempt " + item.getAttempt() + ")",
							report.message());
					retryAfterMillis = retry.backoffMillis();
				} else if (retry != null && retry.onError() == Recipe.Retry.OnError.rebind && item.getAttempt() < retry.maxAttemptsOrOne()) {
					c.z().rebind(step, "ERROR from " + executor + ": " + report.message());
					worklog.log(sessionId, c.recipe().key(), step, "x-rebind after ERROR from " + executor + " (attempt " + item.getAttempt() + ")", report.message());
					redrive = true;
				} else {
					fail(c, step, "ERROR from " + executor + ": " + report.message());
				}
			}
			case FATAL_ERROR -> {
				item.setState(State.FAILED);
				fail(c, step, "FATAL_ERROR from " + executor + ": " + report.message());
			}
			case ABORTED -> item.setState(State.ABORTED);
			default -> throw new InvalidParameterException("Unknown status " + report.status());
			}
			items.saveAndFlush(item);
			if (!c.z().isRunning() && c.z().status() == SessionStatus.failed) {
				afterFailure(c, outbound);
			}
			save(c);
		}
		outbound.forEach(this::runQuietly);
		if (redrive) {
			worker.execute(() -> drive(sessionId));
		}
		if (retryAfterMillis >= 0) {
			final long itemId = found.getId();
			CompletableFuture.delayedExecutor(retryAfterMillis, TimeUnit.MILLISECONDS, worker::execute).execute(() -> releaseRetry(itemId));
		}
	}

	/** Ends the backoff of a RETRY_WAIT attempt (x-retry) and dispatches the step again. Idempotent. */
	void releaseRetry(final long itemId) {
		final Optional<WorkItemRecord> waiting = items.findById(itemId);
		if (waiting.isEmpty()) {
			return;
		}
		final long sessionId = waiting.get().getSessionId();
		synchronized (lock(sessionId)) {
			final WorkItemRecord item = items.findById(itemId).orElseThrow();
			if (item.getState() != State.RETRY_WAIT) {
				return;
			}
			item.setState(State.RETRIED);
			items.saveAndFlush(item);
		}
		drive(sessionId);
	}

	//=================================================================================================
	// workflowContext

	public JsonNode parameters(final String requester, final boolean sysop, final String workItemId) {
		final WorkItemRecord item = heldItem(requester, sysop, workItemId);
		return parse(get(item.getSessionId()).getParameters());
	}

	/** ξ restricted to dep(s) of the step the requester holds; refused (409) when a result is missing (cf. Lemma 2). */
	public Map<String, JsonNode> results(final String requester, final boolean sysop, final String workItemId) {
		final WorkItemRecord item = heldItem(requester, sysop, workItemId);
		synchronized (lock(item.getSessionId())) {
			try {
				return load(item.getSessionId()).z().dependencyResults(item.getStepName());
			} catch (final RuleViolationException ex) {
				throw new WorkflowConflictException(ex.getMessage());
			}
		}
	}

	public void putResult(final String requester, final boolean sysop, final PutResultRequest request) {
		if (request == null) {
			throw new InvalidParameterException("Request is missing");
		}
		final WorkItemRecord item = heldItem(requester, sysop, request.workItemId());
		synchronized (lock(item.getSessionId())) {
			final WorkItemRecord current = items.findById(item.getId()).orElseThrow();
			current.setPendingResult(request.result() == null ? null : request.result().toString());
			items.saveAndFlush(current);
		}
	}

	//=================================================================================================
	// driving

	/** Applies the internal rules and dispatches every enabled step that has no active work item. */
	public void drive(final long sessionId) {
		final List<Runnable> outbound = new ArrayList<>();
		synchronized (lock(sessionId)) {
			final Ctx c = load(sessionId);
			if (!c.z().isRunning()) {
				return;
			}
			c.z().advance(c.parameters());
			if (c.z().isRunning()) {
				for (final String step : c.z().stepsWithMarking(StepMarking.enabled)) {
					if (activeItems(sessionId, step).isEmpty()) {
						prepareDispatch(c, step, outbound);
						if (!c.z().isRunning()) {
							break;
						}
					}
				}
			}
			if (c.z().status() == SessionStatus.failed) {
				afterFailure(c, outbound);
			}
			save(c);
		}
		outbound.forEach(this::runQuietly);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void resumeRunningSessions() {
		for (final SessionRecord s : sessions.findAllByStatus(SessionStatus.running.name())) {
			worklog.log(s.getId(), s.getRecipeName() + "@" + s.getRecipeVersion(), null, "Resumed after WorkflowManager start", null);
			worker.execute(() -> drive(s.getId()));
		}
	}

	/** Claim timeout (S-FailBind: nobody could bind the step) and step timeouts (extension x-timeout). */
	@Scheduled(fixedDelay = 1000, initialDelay = 5000)
	public void watchdog() {
		final ZonedDateTime now = Utilities.utcNow();
		final List<String> active = Arrays.stream(State.values()).filter(State::isActive).map(Enum::name).toList();
		for (final WorkItemRecord candidate : items.findAllByStateInOrderByIdAsc(active)) {
			final long age = Duration.between(candidate.getCreatedAt(), now).toMillis();
			final List<Runnable> outbound = new ArrayList<>();
			synchronized (lock(candidate.getSessionId())) {
				final WorkItemRecord item = items.findById(candidate.getId()).orElseThrow();
				if (!item.getState().isActive()) {
					continue;
				}
				final Ctx c = load(item.getSessionId());
				if (!c.z().isRunning()) {
					item.setState(State.ABORTED);
					items.saveAndFlush(item);
					continue;
				}
				final Step st = c.z().graph().step(item.getStepName());
				if (item.getState() == State.RETRY_WAIT) {
					final Recipe.Retry retry = st.policyOrDefault().retry();
					final long waited = Duration.between(item.getUpdatedAt(), now).toMillis();
					if (waited >= (retry == null ? 0 : retry.backoffMillis())) {
						item.setState(State.RETRIED);
						items.saveAndFlush(item);
						worker.execute(() -> drive(item.getSessionId()));
					}
					continue;
				}
				final Double timeout = st.policyOrDefault().timeoutSeconds();
				if (item.getState() == State.OPEN && age > claimTimeoutSeconds * 1000) {
					item.setState(State.FAILED);
					item.setMessage("No executor claimed the step within " + claimTimeoutSeconds + " s");
					items.saveAndFlush(item);
					c.z().failBind(item.getStepName(), item.getMessage());
				} else if (item.getState() != State.OPEN && timeout != null && age > timeout * 1000) {
					item.setState(State.FAILED);
					item.setMessage("Step timeout of " + timeout + " s exceeded");
					items.saveAndFlush(item);
					c.z().timeout(item.getStepName(), item.getMessage());
				} else {
					continue;
				}
				afterFailure(c, outbound);
				save(c);
			}
			outbound.forEach(this::runQuietly);
		}
	}

	public int runningSessions() {
		return (int) sessions.countByStatus(SessionStatus.running.name());
	}

	//=================================================================================================
	// assistant methods

	private void prepareDispatch(final Ctx c, final String step, final List<Runnable> outbound) {
		final long sessionId = c.record().getId();
		final Step st = c.z().graph().step(step);
		final List<WorkItemRecord> previous = items.findAllBySessionIdAndStepNameOrderByIdAsc(sessionId, step);
		final Set<String> excluded = previous.stream().filter(i -> i.getState() == State.FAILED && i.getExecutorName() != null).map(WorkItemRecord::getExecutorName)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		final String action = c.z().graph().actionOf(step);
		final Recipe.DispatchMode actionMode = c.z().graph().action(action).dispatch();
		final String mode = actionMode != null ? actionMode.name() : c.record().getDispatchMode();
		final String uid = UUID.randomUUID().toString();
		if (!"push".equals(mode)) {
			// pull, and event mode (EventHandler notification is not wired; subscribers claim like pull executors)
			items.saveAndFlush(new WorkItemRecord(uid, sessionId, action, step, null, attemptOf(previous), State.OPEN, String.join(",", excluded)));
			worklog.log(sessionId, c.recipe().key(), step, "S-Enable: work item published for claiming", null);
			return;
		}
		final List<ExecutorEndpoint> candidates = directory.candidates(st, excluded);
		if (candidates.isEmpty()) {
			final String reason = excluded.isEmpty() ? "No executor satisfies η(s)/θ(s): asg(z, s) = ∅" : "Orchestration gave ⊥ for every executor in asg(z, s): " + excluded;
			c.z().failBind(step, reason);
			worklog.log(sessionId, c.recipe().key(), step, "S-FailBind", reason);
			return;
		}
		// x-retry: the same executor again, if it is still a candidate (otherwise the least loaded one)
		final Optional<String> retryOn = previous.isEmpty() || previous.get(previous.size() - 1).getState() != State.RETRIED ? Optional.empty()
				: Optional.ofNullable(previous.get(previous.size() - 1).getExecutorName());
		final ExecutorEndpoint executor = retryOn.flatMap(n -> candidates.stream().filter(e -> e.name().equals(n)).findFirst()).orElseGet(() -> leastLoaded(candidates));
		final WorkItemRecord item = items.saveAndFlush(new WorkItemRecord(uid, sessionId, action, step, executor.name(), attemptOf(previous), State.DISPATCHED,
				String.join(",", excluded)));
		final WorkItem payload = workItem(c, item);
		worklog.log(sessionId, c.recipe().key(), step, "Dispatched to " + executor.name() + " (attempt " + item.getAttempt() + ")", null);
		outbound.add(() -> sendStart(sessionId, item.getUid(), executor, payload));
	}

	private static int attemptOf(final List<WorkItemRecord> previous) {
		return previous.size() + 1;
	}

	private void sendStart(final long sessionId, final String uid, final ExecutorEndpoint executor, final WorkItem payload) {
		try {
			gateway.start(executor, payload);
		} catch (final RuntimeException ex) {
			logger.warn("Executor {} did not accept work item {}: {}", executor.name(), uid, ex.getMessage());
			synchronized (lock(sessionId)) {
				items.findByUid(uid).filter(i -> i.getState() == State.DISPATCHED).ifPresent(i -> {
					i.setState(State.FAILED);
					i.setMessage("Executor unreachable: " + ex.getMessage());
					items.saveAndFlush(i);
				});
			}
			directory.invalidate();
			worker.execute(() -> drive(sessionId));
		}
	}

	private ExecutorEndpoint leastLoaded(final List<ExecutorEndpoint> candidates) {
		final Map<String, Long> load = items.findAllByStateInOrderByIdAsc(List.of(State.DISPATCHED.name(), State.BOUND.name(), State.CLAIMED.name())).stream()
				.filter(i -> i.getExecutorName() != null).collect(Collectors.groupingBy(WorkItemRecord::getExecutorName, Collectors.counting()));
		return candidates.stream().min(Comparator.comparingLong(e -> load.getOrDefault(e.name(), 0L))).orElseThrow();
	}

	private void fail(final Ctx c, final String step, final String reason) {
		if (c.z().marking(step) == StepMarking.running) {
			c.z().failInvoke(step, reason);
			worklog.log(c.record().getId(), c.recipe().key(), step, "S-FailInvoke", reason);
		} else {
			c.z().failBind(step, reason);
			worklog.log(c.record().getId(), c.recipe().key(), step, "S-FailBind", reason);
		}
	}

	/** The session failed: abort the other active work items. Leases expire (§6.5) unless compensation is configured. */
	private void afterFailure(final Ctx c, final List<Runnable> outbound) {
		final long sessionId = c.record().getId();
		for (final WorkItemRecord item : items.findAllBySessionIdOrderByIdAsc(sessionId)) {
			if (!item.getState().isActive()) {
				continue;
			}
			final boolean notify = item.getState() != State.OPEN && item.getState() != State.RETRY_WAIT && item.getExecutorName() != null;
			item.setState(State.ABORTED);
			items.saveAndFlush(item);
			if (notify) {
				final String executorName = item.getExecutorName();
				final String uid = item.getUid();
				outbound.add(() -> directory.byName(executorName).ifPresent(e -> gateway.abort(e, new AbortRequest(uid, sessionId, c.z().failure()))));
			}
		}
		if (releaseLeasesOnFailure) {
			final int released = leases.releaseSession(sessionId);
			if (released > 0) {
				c.z().markExtension("compensation");
				worklog.log(sessionId, c.recipe().key(), null, "Compensation: released " + released + " lease(s)", null);
			}
		}
		worklog.log(sessionId, c.recipe().key(), null, "Session failed", c.z().failure());
	}

	private Ctx load(final long sessionId) {
		final SessionRecord record = sessions.findById(sessionId).orElseThrow(() -> new DataNotFoundException("Session not found: " + sessionId));
		final Recipe recipe = recipes.recipeById(record.getRecipeId());
		return new Ctx(record, recipe, WorkflowSession.restore(recipe, snapshot(record)), parse(record.getParameters()));
	}

	private SessionRecord save(final Ctx c) {
		final SessionRecord record = c.record();
		final String before = record.getStatus();
		record.setState(json(c.z().snapshot()));
		record.setStatus(c.z().status().name());
		record.setExtensions(c.z().extensions().isEmpty() ? null : String.join(",", c.z().extensions()));
		record.setMessage(c.z().failure());
		final SessionRecord saved = sessions.saveAndFlush(record);
		if (SessionStatus.running.name().equals(before) && c.z().status() == SessionStatus.completed) {
			worklog.log(record.getId(), c.recipe().key(), null, "Z-Close: session completed in " + c.z().modelTransitionCount() + " transitions", null);
			if (!Utilities.isEmpty(record.getProductSystem())) {
				final Map<String, Object> payload = new LinkedHashMap<>();
				payload.put("workstationId", workstationId);
				payload.put("sessionId", record.getId());
				payload.put("recipe", c.recipe().key());
				payload.put("status", c.z().status().name());
				payload.put("results", c.z().results());
				worker.execute(() -> storeAtProduct(record.getId(), record.getProductSystem(), payload));
			}
		}
		return saved;
	}

	private void storeAtProduct(final long sessionId, final String productSystem, final Map<String, Object> payload) {
		try {
			product.storeProcessStepResult(productSystem, payload);
			worklog.log(sessionId, null, null, "Results stored at product " + productSystem, null);
		} catch (final RuntimeException ex) {
			worklog.log(sessionId, null, null, "Unable to store results at product " + productSystem, ex.getMessage());
		}
	}

	private WorkItem workItem(final Ctx c, final WorkItemRecord item) {
		final Step st = c.z().graph().step(item.getStepName());
		Double leaseDuration = null;
		if (st.policyOrDefault().lease() != null) {
			leaseDuration = c.recipe().resources().stream().filter(r -> r.name().equals(st.policyOrDefault().lease().resource())).findFirst()
					.map(Recipe.Resource::leaseDurationSeconds).orElse(null);
		}
		return new WorkItem(item.getUid(), c.record().getId(), c.recipe().key(), item.getActionName(), item.getStepName(), item.getAttempt(), st.service(), st.executor(),
				st.params(), st.input(), st.policy(), leaseDuration, c.parameters(), c.z().dependencyResults(item.getStepName()));
	}

	private WorkItemRecord heldItem(final String requester, final boolean sysop, final String workItemId) {
		final WorkItemRecord item = items.findByUid(workItemId).orElseThrow(() -> new DataNotFoundException("Work item not found: " + workItemId));
		if (!sysop && !requester.equals(item.getExecutorName())) {
			throw new ForbiddenException("Work item " + workItemId + " is held by another executor");
		}
		if (!item.getState().isActive()) {
			throw new WorkflowConflictException("Work item " + workItemId + " is " + item.getState());
		}
		return item;
	}

	private List<WorkItemRecord> activeItems(final long sessionId, final String step) {
		return items.findAllBySessionIdAndStepNameOrderByIdAsc(sessionId, step).stream().filter(i -> i.getState().isActive()).toList();
	}

	private static Set<String> excluded(final WorkItemRecord item) {
		return item.getExcluded() == null || item.getExcluded().isBlank() ? Set.of() : Set.of(item.getExcluded().split(","));
	}

	private static JsonNode binding(final WorkItemReport report) {
		return report.binding() == null || report.binding().isNull() ? F.objectNode().put("reported", "implicit") : report.binding();
	}

	private Object lock(final long sessionId) {
		return locks.computeIfAbsent(sessionId, id -> new Object());
	}

	private void runQuietly(final Runnable r) {
		try {
			r.run();
		} catch (final RuntimeException ex) {
			logger.warn("Outbound call failed: {}", ex.getMessage());
		}
	}

	private static SessionSnapshot snapshot(final SessionRecord record) {
		try {
			return RecipeCodec.mapper().readValue(record.getState(), SessionSnapshot.class);
		} catch (final JsonProcessingException ex) {
			throw new IllegalStateException("Corrupt session state of session " + record.getId(), ex);
		}
	}

	private static String json(final Object value) {
		return RecipeCodec.write(value);
	}

	private static JsonNode parse(final String json) {
		if (json == null) {
			return F.objectNode();
		}
		try {
			return RecipeCodec.mapper().readTree(json);
		} catch (final JsonProcessingException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
