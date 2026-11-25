/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import eu.arrowhead.workflowmanager.jpa.entity.Worklog;
import eu.arrowhead.workflowmanager.jpa.repository.WorklogRepository;

@Service
public class WorklogService {

	private final Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	private WorklogRepository repository;

	public void log(final Long sessionId, final String recipe, final String step, final String message, final String detail) {
		try {
			repository.saveAndFlush(new Worklog(sessionId, recipe, step, message, detail));
		} catch (final RuntimeException ex) {
			logger.warn("Unable to write worklog: {}", ex.getMessage());
		}
	}
}
