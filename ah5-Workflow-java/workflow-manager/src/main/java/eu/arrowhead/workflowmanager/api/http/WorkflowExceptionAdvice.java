/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflowmanager.api.http;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import eu.arrowhead.dto.ErrorMessageDTO;
import eu.arrowhead.dto.enums.ExceptionType;
import eu.arrowhead.workflow.model.session.RuleViolationException;
import eu.arrowhead.workflowmanager.service.WorkflowConflictException;

/** 409 for state conflicts: a rule premise that does not hold, or a refused context read (cf. Lemma 2). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkflowExceptionAdvice {

	@ExceptionHandler({ WorkflowConflictException.class, RuleViolationException.class })
	public ResponseEntity<ErrorMessageDTO> conflict(final RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessageDTO(ex.getMessage(), HttpStatus.CONFLICT.value(), ExceptionType.INVALID_PARAMETER, null));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorMessageDTO> badRequest(final IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessageDTO(ex.getMessage(), HttpStatus.BAD_REQUEST.value(), ExceptionType.INVALID_PARAMETER, null));
	}
}
