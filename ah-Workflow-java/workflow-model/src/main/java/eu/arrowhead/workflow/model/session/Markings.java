/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.session;

/** Markings and status of the session state z = (M, M_A, β, ε, ξ, status), Sensors paper §5.3. */
public final class Markings {

	private Markings() {
		throw new UnsupportedOperationException();
	}

	/**
	 * M(s); ordered inactive < enabled < running < completed (Lemma 1(i)). {@code skipped} belongs to the x-guard
	 * extension: a step none of whose incoming transitions is taken; like completed it is final.
	 */
	public enum StepMarking {
		inactive, enabled, running, completed, skipped;

		/** completed or skipped: the step will not change any more. */
		public boolean isResolved() {
			return this == completed || this == skipped;
		}
	}

	/** M_A(α); ordered inactive < active < completed. */
	public enum ActionMarking {
		inactive, active, completed
	}

	public enum SessionStatus {
		running, completed, failed
	}

	/** Rules of §5.9, plus the labels of the extensions of §6.6 (prefixed X_), which are outside the proven model. */
	public enum Rule {
		A_START("A-Start", true),
		S_ENABLE("S-Enable", true),
		S_BIND("S-Bind", true),
		S_DONE("S-Done", true),
		A_FINISH("A-Finish", true),
		Z_CLOSE("Z-Close", true),
		S_FAIL_BIND("S-FailBind", true),
		S_FAIL_INVOKE("S-FailInvoke", true),
		X_REBIND("x-rebind", false),
		X_ABORT("x-abort", false),
		X_TIMEOUT("x-timeout", false),
		X_SKIP("x-skip", false);

		private final String label;
		private final boolean model;

		Rule(final String label, final boolean model) {
			this.label = label;
			this.model = model;
		}

		public String label() {
			return label;
		}

		public boolean isModelRule() {
			return model;
		}
	}
}
