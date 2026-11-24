/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowconfigurator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import eu.arrowhead.common.Constants;
import eu.arrowhead.common.jpa.RefreshableRepositoryImpl;

@SpringBootApplication
@ComponentScan(basePackages = Constants.BASE_PACKAGE,
		// replaced by ConfiguratorSecurityConfig, which leaves the plain-HTTP console connector on HTTP
		excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = eu.arrowhead.common.security.DefaultSecurityConfig.class))
@EntityScan("eu.arrowhead.workflowconfigurator.jpa")
@EnableJpaRepositories(basePackages = "eu.arrowhead.workflowconfigurator.jpa", repositoryBaseClass = RefreshableRepositoryImpl.class)
@EnableScheduling
public class WorkflowConfiguratorMain {

	public static void main(final String[] args) {
		SpringApplication.run(WorkflowConfiguratorMain.class, args);
	}

	protected WorkflowConfiguratorMain() {
	}
}
