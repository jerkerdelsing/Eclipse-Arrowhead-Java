/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.sysmonitor.SysMonitorPublisher;
import eu.arrowhead.common.sysmonitor.SysMonitorReading;
import eu.arrowhead.common.sysmonitor.SysMonitorSource;
import eu.arrowhead.workflowmanager.WorkflowManagerConstants;
import eu.arrowhead.workflowmanager.jpa.entity.WorkItemRecord;

/**
 * What the liveness check of the executors found, as sysMonitor observations:
 * <ul>
 * <li>executorLiveness (state): which executors are suspected, why and since when, with the work items they hold. It is
 * published in every cycle and at once when it changes, so that a subscriber need not wait for the grace period;</li>
 * <li>workItemLost (event): a work item was given up, why, how long the detection took, what follows, and whether the
 * equipment may still be in use: an executor that is only cut off may still be running the step.</li>
 * </ul>
 */
@Component
public class ExecutorObservations implements SysMonitorSource {

	public static final String TYPE_LIVENESS = "executorLiveness";
	public static final String TYPE_LOST = "workItemLost";

	/** Why an executor is suspected. */
	public enum Cause {
		notRunning, unreachable, unregistered
	}

	/** What follows for the step of a lost work item. */
	public enum Outcome {
		nextExecutor, rebind, retry, sessionFailed
	}

	private static final int MAX_EVENTS = 1000;
	private static final int MAX_WORK_ITEMS = 100;

	private record Suspected(String executor, Cause cause, Instant since, Instant lastSeen, Map<String, Object> workItem) {
	}

	private final Logger logger = LogManager.getLogger(this.getClass());

	private final ObjectProvider<SysMonitorPublisher> publisher;

	@Value(WorkflowManagerConstants.$LIVENESS_CHECK_SECONDS_WD)
	private long checkSeconds;

	@Value(WorkflowManagerConstants.$LIVENESS_LOST_AFTER_SECONDS_WD)
	private long lostAfterSeconds;

	private final Map<String, Suspected> suspected = new LinkedHashMap<>();
	/** work item -> when its executor last answered that it runs it */
	private final Map<String, Instant> seen = new HashMap<>();
	private final Set<String> unchecked = new TreeSet<>();
	private final Deque<SysMonitorReading> events = new ArrayDeque<>();
	private int held;
	private long lost;

	public ExecutorObservations(final ObjectProvider<SysMonitorPublisher> publisher) {
		this.publisher = publisher;
	}

	//=================================================================================================
	// from the engine

	/** The work items held at a check; suspicions of others are over. */
	public void held(final Collection<WorkItemRecord> items) {
		final Set<String> uids = new TreeSet<>();
		final Set<String> executors = new TreeSet<>();
		items.forEach(i -> {
			uids.add(i.getUid());
			executors.add(String.valueOf(i.getExecutorName()));
		});
		final boolean changed;
		synchronized (this) {
			held = uids.size();
			seen.keySet().retainAll(uids);
			changed = suspected.keySet().retainAll(uids) | unchecked.retainAll(executors);
		}
		publish(changed);
	}

	public void suspect(final WorkItemRecord item, final Cause cause, final long sinceMillis) {
		final boolean changed;
		synchronized (this) {
			final Suspected old = suspected.get(item.getUid());
			changed = old == null || old.cause() != cause;
			suspected.put(item.getUid(), new Suspected(item.getExecutorName(), cause, time(sinceMillis),
					old != null ? old.lastSeen() : seen.getOrDefault(item.getUid(), item.getUpdatedAt().toInstant().truncatedTo(ChronoUnit.MILLIS)), workItem(item)));
		}
		publish(changed);
	}

	/** The executor answered that it runs the work item. */
	public synchronized void running(final String uid) {
		seen.put(uid, Instant.now().truncatedTo(ChronoUnit.MILLIS));
	}

	/** The work item is not suspected any more. */
	public void clear(final String uid) {
		final boolean changed;
		synchronized (this) {
			changed = suspected.remove(uid) != null;
		}
		publish(changed);
	}

	/** Whether the executor answers the question at all (false: it refuses get-status, so it cannot be checked). */
	public void checked(final String executor, final boolean answers) {
		final boolean changed;
		synchronized (this) {
			changed = answers ? unchecked.remove(executor) : unchecked.add(executor);
		}
		publish(changed);
	}

	public void lost(final WorkItemRecord item, final boolean bound, final String recipe, final Cause cause, final String reason, final long sinceMillis, final Outcome outcome) {
		final Instant now = Instant.now();
		final Instant since = time(sinceMillis);
		final Suspected old;
		synchronized (this) {
			old = suspected.remove(item.getUid());
			seen.remove(item.getUid());
			held = Math.max(0, held - 1);
			lost++;
		}
		final Instant lastSeen = old == null ? since : old.lastSeen();
		final Map<String, Object> value = new LinkedHashMap<>(workItem(item));
		value.put("bound", bound);
		value.put("recipe", recipe);
		value.put("executor", item.getExecutorName());
		value.put("cause", cause.name());
		value.put("detail", reason);
		value.put("lastSeen", lastSeen.toString());
		value.put("suspectedSince", since.toString());
		value.put("lostAt", now.truncatedTo(ChronoUnit.MILLIS).toString());
		value.put("detectionSeconds", (now.toEpochMilli() - lastSeen.toEpochMilli()) / 1000.0);
		// the equipment is used only after S-Bind; nobody can tell whether a bound step still runs there
		value.put("equipmentMayBeInUse", bound);
		value.put("outcome", outcome.name());
		value.put("workRepeated", bound && (outcome == Outcome.rebind || outcome == Outcome.retry));
		synchronized (this) {
			if (publisher.getIfAvailable() == null) {
				return;
			}
			if (events.size() >= MAX_EVENTS) {
				events.removeFirst();
				logger.warn("More than {} unpublished workItemLost events; the oldest is dropped", MAX_EVENTS);
			}
			events.addLast(SysMonitorReading.of(TYPE_LOST, value, null, false));
		}
		publish(true);
	}

	//=================================================================================================
	// SysMonitorSource

	@Override
	public Set<String> observationTypes() {
		return new TreeSet<>(Set.of(TYPE_LIVENESS, TYPE_LOST));
	}

	@Override
	public synchronized List<SysMonitorReading> state() {
		final Map<String, Map<String, Object>> executors = new TreeMap<>();
		int listed = 0;
		for (final Suspected s : suspected.values()) {
			final Map<String, Object> e = executors.computeIfAbsent(s.executor(), name -> {
				final Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("executor", name);
				entry.put("cause", s.cause().name());
				entry.put("since", s.since().toString());
				entry.put("workItemCount", 0);
				entry.put("workItems", new ArrayList<Map<String, Object>>());
				return entry;
			});
			e.put("workItemCount", (int) e.get("workItemCount") + 1);
			if (listed++ < MAX_WORK_ITEMS) {
				@SuppressWarnings("unchecked")
				final List<Map<String, Object>> workItems = (List<Map<String, Object>>) e.get("workItems");
				final Map<String, Object> entry = new LinkedHashMap<>(s.workItem());
				entry.put("lastSeen", s.lastSeen().toString());
				workItems.add(entry);
			}
		}
		final Map<String, Object> value = new LinkedHashMap<>();
		value.put("state", checkSeconds <= 0 ? "disabled" : !suspected.isEmpty() ? "suspect" : unchecked.isEmpty() ? "ok" : "unchecked");
		value.put("heldWorkItems", held);
		value.put("suspectedExecutors", new ArrayList<>(executors.values()));
		value.put("uncheckedExecutors", List.copyOf(unchecked));
		value.put("lostWorkItems", lost);
		value.put("checkSeconds", checkSeconds);
		value.put("lostAfterSeconds", lostAfterSeconds);
		return List.of(SysMonitorReading.of(TYPE_LIVENESS, value, "1", suspected.isEmpty() && unchecked.isEmpty()));
	}

	@Override
	public synchronized List<SysMonitorReading> events() {
		return List.copyOf(events);
	}

	@Override
	public synchronized void published(final SysMonitorReading event) {
		events.remove(event);
	}

	//=================================================================================================
	// assistant methods

	private void publish(final boolean changed) {
		if (changed) {
			publisher.ifAvailable(SysMonitorPublisher::publishSourcesNow);
		}
	}

	private static Map<String, Object> workItem(final WorkItemRecord item) {
		final Map<String, Object> result = new LinkedHashMap<>();
		result.put("workItemId", item.getUid());
		result.put("sessionId", item.getSessionId());
		result.put("action", item.getActionName());
		result.put("step", item.getStepName());
		result.put("attempt", item.getAttempt());
		result.put("bound", item.getState() == WorkItemRecord.State.BOUND);
		return result;
	}

	private static Instant time(final long millis) {
		return Instant.ofEpochMilli(millis);
	}
}
