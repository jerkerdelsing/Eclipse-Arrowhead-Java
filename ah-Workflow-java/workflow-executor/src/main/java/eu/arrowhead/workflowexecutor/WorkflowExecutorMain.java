/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowexecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.jpa.RefreshableRepositoryImpl;

@SpringBootApplication
@ComponentScan(Constants.BASE_PACKAGE)
@EntityScan("eu.arrowhead.workflowexecutor.jpa")
@EnableJpaRepositories(basePackages = "eu.arrowhead.workflowexecutor.jpa", repositoryBaseClass = RefreshableRepositoryImpl.class)
@EnableScheduling
public class WorkflowExecutorMain {

	public static void main(final String[] args) {
		SpringApplication.run(WorkflowExecutorMain.class, args);
	}

	protected WorkflowExecutorMain() {
	}
}
