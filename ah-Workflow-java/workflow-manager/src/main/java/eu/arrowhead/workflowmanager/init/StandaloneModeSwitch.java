/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.init;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.arrowhead.common.Constants;
import eu.arrowhead.workflowmanager.WorkflowManagerConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * Development only: workflow.standalone=true puts the common library into standalone mode before initialisation, so the
 * system neither registers in nor depends on a ServiceRegistry. Executors then come from workflow.executors.
 */
@Component
public class StandaloneModeSwitch {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Resource(name = Constants.ARROWHEAD_CONTEXT)
	private Map<String, Object> arrowheadContext;

	@Value(WorkflowManagerConstants.$STANDALONE_WD)
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
