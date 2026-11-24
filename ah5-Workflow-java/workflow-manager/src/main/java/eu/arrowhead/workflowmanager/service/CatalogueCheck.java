/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.http.ArrowheadHttpService;
import eu.arrowhead.dto.ServiceInstanceListResponseDTO;
import eu.arrowhead.dto.ServiceInstanceLookupRequestDTO;
import eu.arrowhead.workflow.model.Recipe.Step;
import eu.arrowhead.workflow.model.RecipeGraph;
import eu.arrowhead.workflow.model.validation.Finding;
import eu.arrowhead.workflow.model.validation.RecipeCheck;
import eu.arrowhead.workflow.model.validation.Verdict;
import eu.arrowhead.workflow.wire.WorkflowServices;
import eu.arrowhead.workflowmanager.WorkflowManagerConstants;
import eu.arrowhead.workflowmanager.init.StandaloneModeSwitch;

/**
 * Catalogue check (intent I1): every θ(s) is registered in the ServiceRegistry, and at least one known executor can run
 * the step; otherwise S-FailBind is certain. Findings are CLARIFY, because providers may register later.
 */
@Component
public class CatalogueCheck implements RecipeCheck {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private ArrowheadHttpService arrowheadHttpService;

	@Autowired
	private ExecutorDirectory directory;

	@Autowired
	private StandaloneModeSwitch standalone;

	@Value(WorkflowManagerConstants.$CATALOGUE_CHECK_WD)
	private boolean enabled;

	@Value(WorkflowManagerConstants.$CATALOGUE_CHECK_SERVICES_WD)
	private boolean servicesEnabled;

	@Override
	public void check(final RecipeGraph graph, final List<Finding> findings) {
		if (!enabled) {
			return;
		}
		final boolean pullExecutorsExist = directory.all().stream().anyMatch(ExecutorDirectory.ExecutorEndpoint::isPull);
		final Set<String> checked = new HashSet<>();
		for (final Step s : graph.steps().values()) {
			if (!pullExecutorsExist && directory.all().stream().noneMatch(e -> e.canRun(s))) {
				findings.add(new Finding("C02", Verdict.CLARIFY, "η(s) / asg(z, s)", "step " + s.name(), "No known executor can run this step"));
			}
			final String sd = s.serviceDefinition();
			if (!servicesEnabled || standalone.isStandalone() || sd == null || WorkflowServices.INTERNAL_LEASE_SERVICE.equals(sd) || !checked.add(sd)) {
				continue;
			}
			try {
				final ServiceInstanceListResponseDTO response = arrowheadHttpService.consumeService(Constants.SERVICE_DEF_SERVICE_DISCOVERY, Constants.SERVICE_OP_LOOKUP,
						ServiceInstanceListResponseDTO.class, new ServiceInstanceLookupRequestDTO.Builder().serviceDefinitionName(sd).build());
				if (response == null || response.entries() == null || response.entries().isEmpty()) {
					findings.add(new Finding("C01", Verdict.CLARIFY, "intent I1", "step " + s.name(), "Service '" + sd + "' is not registered in the ServiceRegistry"));
				}
			} catch (final RuntimeException ex) {
				logger.warn("Catalogue lookup of {} failed: {}", sd, ex.getMessage());
			}
		}
	}
}
