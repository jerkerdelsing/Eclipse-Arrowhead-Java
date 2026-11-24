/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.validation;

/** Validator outcome. Decision rule (intent paper): reject wins, then clarify, then revise, else accept. */
public enum Verdict {
	ACCEPT, REVISE, CLARIFY, REJECT;

	int rank() {
		return switch (this) {
		case ACCEPT -> 0;
		case REVISE -> 1;
		case CLARIFY -> 2;
		case REJECT -> 3;
		};
	}

	public static Verdict worst(final Verdict a, final Verdict b) {
		return a.rank() >= b.rank() ? a : b;
	}
}
