/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator.init;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.Constants;
import eu.arrowhead.workflowconfigurator.WorkflowConfiguratorConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * Development only: workflow.standalone=true puts the common library into standalone mode before initialisation, so the
 * system neither registers in nor depends on a ServiceRegistry. The WorkflowManager and providers then come from workflow.manager.url and workflow.executor.static-providers.
 */
@Component
public class StandaloneModeSwitch {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Resource(name = Constants.ARROWHEAD_CONTEXT)
	private Map<String, Object> arrowheadContext;

	@Value(WorkflowConfiguratorConstants.$STANDALONE_WD)
	private boolean standalone;

	@PostConstruct
	void apply() {
		if (standalone) {
			arrowheadContext.put(Constants.SERVER_STANDALONE_MODE, true);
			logger.warn("Standalone mode: not registering in ServiceRegistry; development use only");
		}
	}

	public boolean isStandalone() {
		return standalone;
	}
}
