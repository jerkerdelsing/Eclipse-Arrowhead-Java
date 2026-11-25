/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import eu.arrowhead.workflow.model.validation.Finding;
import eu.arrowhead.workflow.model.validation.RecipeValidator;
import eu.arrowhead.workflow.model.validation.ValidationReport;
import eu.arrowhead.workflow.model.validation.Verdict;

class RecipeValidatorTest {

	private final RecipeValidator validator = new RecipeValidator();

	private static Set<String> codes(final ValidationReport report) {
		return report.findings().stream().map(Finding::code).collect(Collectors.toSet());
	}

	private ValidationReport validate(final String json) {
		return validator.validate(RecipeCodec.parse(json));
	}

	@Test
	void examplesAreAccepted() {
		assertEquals(Verdict.ACCEPT, validator.validate(Examples.load(Examples.FORKLIFT)).verdict(), () -> validator.validate(Examples.load(Examples.FORKLIFT)).toString());
		assertEquals(Verdict.ACCEPT, validator.validate(Examples.load(Examples.TEMPERATURE)).verdict());
	}

	@Test
	void unknownFieldsAreRejectedByTheCodec() {
		assertThrows(IllegalArgumentException.class, () -> RecipeCodec.parse("{\"name\":\"x\",\"version\":\"1.0.0\",\"firstAction\":\"a\",\"actions\":[],\"bogus\":1}"));
	}

	@Test
	void cyclicActionGraphViolatesW1() {
		final ValidationReport r = validate("""
				{"name":"c","version":"1.0.0","firstAction":"a","actions":[
				 {"name":"a","next":"b","steps":[{"name":"s1","service":{"serviceDefinition":"x"}}]},
				 {"name":"b","next":"a","steps":[{"name":"s2","service":{"serviceDefinition":"x"}}]}]}""");
		assertEquals(Verdict.REVISE, r.verdict());
		assertTrue(codes(r).contains("S05"));
	}

	@Test
	void unreachableActionViolatesW1() {
		final ValidationReport r = validate("""
				{"name":"c","version":"1.0.0","firstAction":"a","actions":[
				 {"name":"a","steps":[{"name":"s1","service":{"serviceDefinition":"x"}}]},
				 {"name":"b","steps":[{"name":"s2","service":{"serviceDefinition":"x"}}]}]}""");
		assertTrue(codes(r).contains("S05"));
	}

	@Test
	void cyclicStepGraphViolatesW2() {
		final ValidationReport r = validate("""
				{"name":"c","version":"1.0.0","firstAction":"a","actions":[
				 {"name":"a","steps":[{"name":"s1","service":{"serviceDefinition":"x"},"next":["s2"]},
				                      {"name":"s2","service":{"serviceDefinition":"x"},"next":["s1"]}]}]}""");
		assertTrue(codes(r).contains("S07"));
	}

	@Test
	void nextAcrossActionsViolatesW3() {
		final ValidationReport r = validate("""
				{"name":"c","version":"1.0.0","firstAction":"a","actions":[
				 {"name":"a","next":"b","steps":[{"name":"s1","service":{"serviceDefinition":"x"},"next":["s2"]}]},
				 {"name":"b","steps":[{"name":"s2","service":{"serviceDefinition":"x"}}]}]}""");
		assertTrue(codes(r).contains("S06"));
	}

	@Test
	void depOnNonPrecedingStepViolatesW4() {
		// parallel siblings do not precede each other; a later action does not precede an earlier one
		final ValidationReport r = validate("""
				{"name":"c","version":"1.0.0","firstAction":"a","actions":[
				 {"name":"a","next":"b","steps":[{"name":"s1","service":{"serviceDefinition":"x"},"dep":["s2"]},
				                                {"name":"s2","service":{"serviceDefinition":"x"},"dep":["s3"]}]},
				 {"name":"b","steps":[{"name":"s3","service":{"serviceDefinition":"x"},"dep":["s1"]}]}]}""");
		assertEquals(2, r.findings().stream().filter(f -> f.code().equals("D02")).count());
	}

	@Test
	void quantityAboveOneIsOutsideTheModel() {
		final ValidationReport r = validate("""
				{"name":"c","version":"1.0.0","firstAction":"a","actions":[
				 {"name":"a","steps":[{"name":"s1","service":{"serviceDefinition":"x"},"policy":{"quantity":2}}]}]}""");
		assertTrue(codes(r).contains("M01"));
	}

	@Test
	void inputTemplateMustStayWithinDep() {
		final ValidationReport r = validate("""
				{"name":"c","version":"1.0.0","firstAction":"a","actions":[
				 {"name":"a","steps":[{"name":"s1","service":{"serviceDefinition":"x"},"next":["s2"]},
				                      {"name":"s2","service":{"serviceDefinition":"y","requiresResults":["x"]},"input":{"v":"${result.s1}","t":"${param.t}"}}]}]}""");
		assertTrue(codes(r).containsAll(Set.of("D03", "D04", "D05")));
		assertEquals(Verdict.CLARIFY, r.verdict());
	}

	@Test
	void leaseOrderingIsChecked() {
		final ValidationReport r = validate("""
				{"name":"c","version":"1.0.0","firstAction":"a","resources":[{"name":"tool","leaseDurationSeconds":10}],"actions":[
				 {"name":"a","steps":[
				   {"name":"use","service":{"serviceDefinition":"x"},"next":["res"],"policy":{"lease":{"resource":"tool","mode":"use"},"nominalDurationSeconds":20}},
				   {"name":"res","service":{"serviceDefinition":"r"},"policy":{"lease":{"resource":"tool","mode":"reserve"}}}]}]}""");
		assertTrue(codes(r).containsAll(Set.of("L02", "L04", "L05")), r::toString);
	}
}
