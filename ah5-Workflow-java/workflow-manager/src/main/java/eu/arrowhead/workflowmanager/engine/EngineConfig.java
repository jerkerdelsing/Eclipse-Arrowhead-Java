/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import eu.arrowhead.workflowmanager.WorkflowManagerConstants;

@Configuration
public class EngineConfig {

	@Bean(WorkflowManagerConstants.WORKER)
	TaskExecutor workflowWorker(@Value(WorkflowManagerConstants.$WORKER_THREADS_WD) final int threads) {
		final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(threads);
		executor.setMaxPoolSize(threads);
		executor.setThreadNamePrefix("workflow-");
		executor.initialize();
		return executor;
	}
}
