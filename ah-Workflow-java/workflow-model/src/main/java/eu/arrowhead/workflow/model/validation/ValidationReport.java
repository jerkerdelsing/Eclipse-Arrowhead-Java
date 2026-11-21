/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.validation;

import java.util.List;

public record ValidationReport(String recipe, Verdict verdict, List<Finding> findings) {

	public ValidationReport {
		findings = List.copyOf(findings);
	}

	public boolean accepted() {
		return verdict == Verdict.ACCEPT;
	}

	public static ValidationReport of(final String recipe, final List<Finding> findings) {
		Verdict verdict = Verdict.ACCEPT;
		for (final Finding f : findings) {
			verdict = Verdict.worst(verdict, f.outcome());
		}
		return new ValidationReport(recipe, verdict, findings);
	}
}
