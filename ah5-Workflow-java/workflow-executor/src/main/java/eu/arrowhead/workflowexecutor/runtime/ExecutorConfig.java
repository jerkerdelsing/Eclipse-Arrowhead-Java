/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor.runtime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.arrowhead.workflowexecutor.WorkflowExecutorConstants;

@Configuration
public class ExecutorConfig {

	@Bean(name = WorkflowExecutorConstants.WORKER, destroyMethod = "shutdownNow")
	ExecutorService executorWorker(@Value(WorkflowExecutorConstants.$THREADS_WD) final int threads) {
		return Executors.newFixedThreadPool(threads, r -> {
			final Thread t = new Thread(r, "step-runner");
			t.setDaemon(true);
			return t;
		});
	}
}
