/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import eu.arrowhead.common.Utilities;
import eu.arrowhead.workflow.model.RecipeCodec;
import eu.arrowhead.workflow.model.session.Markings.SessionStatus;
import eu.arrowhead.workflow.model.session.SessionSnapshot;
import eu.arrowhead.workflow.model.session.WorkflowSession;
import eu.arrowhead.workflowmanager.api.dto.RecipeResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.SessionResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.TraceResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.WorkItemResponseDTO;
import eu.arrowhead.workflowmanager.api.dto.WorklogEntryDTO;
import eu.arrowhead.workflowmanager.jpa.entity.RecipeRecord;
import eu.arrowhead.workflowmanager.jpa.entity.SessionRecord;
import eu.arrowhead.workflowmanager.jpa.entity.WorkItemRecord;
import eu.arrowhead.workflowmanager.jpa.entity.Worklog;

final class DTOConverter {

	private DTOConverter() {
		throw new UnsupportedOperationException();
	}

	/** The list entry without the recipe document (workflowRecipe.query with summary=true). */
	static RecipeResponseDTO recipeSummary(final RecipeRecord r) {
		return new RecipeResponseDTO(r.getId(), r.getName(), r.getVersion(), r.getStatus(), r.getRegisteredBy(), null, time(r.getCreatedAt()));
	}

	static RecipeResponseDTO recipe(final RecipeRecord r) {
		try {
			return new RecipeResponseDTO(r.getId(), r.getName(), r.getVersion(), r.getStatus(), r.getRegisteredBy(), RecipeCodec.mapper().readTree(r.getDocument()),
					time(r.getCreatedAt()));
		} catch (final JsonProcessingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static SessionResponseDTO session(final SessionRecord s, final WorkflowSession z) {
		final SessionSnapshot snap = z.snapshot();
		final Map<String, String> steps = new LinkedHashMap<>();
		snap.stepMarking().forEach((k, v) -> steps.put(k, v.name()));
		final Map<String, String> actions = new LinkedHashMap<>();
		snap.actionMarking().forEach((k, v) -> actions.put(k, v.name()));
		return new SessionResponseDTO(s.getId(), s.getRecipeName(), s.getRecipeVersion(), s.getSubject(), s.getOwner(), s.getStatus(), s.getDispatchMode(), z.failure(),
				List.copyOf(z.extensions()), steps, actions, z.modelTransitionCount(), z.graph().transitionBound(), s.getProductSystem(), time(s.getCreatedAt()),
				time(s.getUpdatedAt()));
	}

	static TraceResponseDTO trace(final SessionRecord s, final WorkflowSession z) {
		final SessionSnapshot snap = z.snapshot();
		final boolean withinModel = z.extensions().isEmpty();
		return new TraceResponseDTO(s.getId(), z.status().name(), snap.trace(), snap.results(), snap.binding(), snap.executorAssignment(), snap.runner(),
				z.modelTransitionCount(), z.graph().transitionBound(), withinModel && (z.status() != SessionStatus.completed || z.modelTransitionCount() == z.graph().transitionBound()));
	}

	static WorkItemResponseDTO workItem(final WorkItemRecord i) {
		return new WorkItemResponseDTO(i.getUid(), i.getSessionId(), i.getActionName(), i.getStepName(), i.getExecutorName(), i.getAttempt(), i.getState().name(),
				i.getMessage(), time(i.getCreatedAt()), time(i.getUpdatedAt()));
	}

	static WorklogEntryDTO worklog(final Worklog w) {
		return new WorklogEntryDTO(w.getId(), time(w.getCreatedAt()), w.getSessionId(), w.getRecipe(), w.getStepName(), w.getMessage(), w.getDetail());
	}

	private static String time(final java.time.ZonedDateTime t) {
		return t == null ? null : Utilities.convertZonedDateTimeToUTCString(t);
	}
}
