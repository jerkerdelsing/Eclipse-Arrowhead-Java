/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model.validation;

/**
 * One validator finding. {@code code} is stable across the Java, Go and Rust implementations (conformance vectors);
 * {@code basis} names the rule in the sources, e.g. "W4" (Sensors paper) or "intent V9".
 */
public record Finding(String code, Verdict outcome, String basis, String location, String message) {
}
