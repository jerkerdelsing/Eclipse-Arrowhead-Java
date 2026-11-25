/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.jpa.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import eu.arrowhead.common.jpa.RefreshableRepository;
import eu.arrowhead.workflowmanager.jpa.entity.RecipeRecord;

@Repository
public interface RecipeRepository extends RefreshableRepository<RecipeRecord, Long> {

	Optional<RecipeRecord> findByNameAndVersion(String name, String version);

	List<RecipeRecord> findAllByNameOrderByIdAsc(String name);

	List<RecipeRecord> findAllByOrderByIdAsc();
}
