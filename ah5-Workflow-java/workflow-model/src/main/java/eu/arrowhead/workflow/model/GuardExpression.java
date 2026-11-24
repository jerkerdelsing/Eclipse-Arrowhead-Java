/*******************************************************************************
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package eu.arrowhead.workflow.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Condition of a guarded transition s → s' (extension x-guard, outside the proven model). The language is deliberately
 * small and has no side effects, so the WorkflowManager can evaluate it from ρ and ξ alone, and Go and Rust can
 * implement the same grammar:
 *
 * <pre>
 * expr    := or
 * or      := and ( "||" and )*
 * and     := unary ( "&amp;&amp;" unary )*
 * unary   := "!" unary | compare
 * compare := operand ( ( "==" | "!=" | "&lt;" | "&lt;=" | "&gt;" | "&gt;=" ) operand )?
 * operand := "(" expr ")" | reference | number | string | "true" | "false" | "null"
 * reference := "${param." name path "}" | "${result." step path "}"      path := ( "." segment )*
 * string  := '…' | "…"   (backslash escapes the next character)
 * </pre>
 *
 * A lone operand is true when it is boolean true, a non-zero number, a non-empty string, array or object. A missing
 * reference is null. Numbers compare numerically; strings compare lexicographically; == and != compare JSON values
 * (numbers by value). Ordering a number with a non-number is false.
 */
public final class GuardExpression {

	private static final JsonNodeFactory F = JsonNodeFactory.instance;
	private static final int MAX_LENGTH = 2000;

	private final String source;
	private final Node root;
	private final Set<String> steps = new LinkedHashSet<>();
	private final Set<String> parameters = new LinkedHashSet<>();

	private GuardExpression(final String source) {
		if (source == null || source.isBlank()) {
			throw new IllegalArgumentException("empty condition");
		}
		if (source.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("condition longer than " + MAX_LENGTH + " characters");
		}
		this.source = source;
		final Parser parser = new Parser(tokenize(source));
		this.root = parser.expression();
		if (!parser.atEnd()) {
			throw new IllegalArgumentException("unexpected '" + parser.peek().text + "' at " + parser.peek().position);
		}
	}

	/** @throws IllegalArgumentException with the position of the first error */
	public static GuardExpression parse(final String source) {
		return new GuardExpression(source);
	}

	public String source() {
		return source;
	}

	/** Steps whose results ξ the condition reads. */
	public Set<String> referencedSteps() {
		return Collections.unmodifiableSet(steps);
	}

	public Set<String> referencedParameters() {
		return Collections.unmodifiableSet(parameters);
	}

	public boolean evaluate(final JsonNode rho, final Map<String, JsonNode> xi) {
		return truthy(root.eval(rho == null ? F.objectNode() : rho, xi == null ? Map.of() : xi));
	}

	//=================================================================================================
	// evaluation

	private interface Node {
		JsonNode eval(JsonNode rho, Map<String, JsonNode> xi);
	}

	private static boolean truthy(final JsonNode v) {
		if (v == null || v.isNull() || v.isMissingNode()) {
			return false;
		}
		if (v.isBoolean()) {
			return v.booleanValue();
		}
		if (v.isNumber()) {
			return v.decimalValue().signum() != 0;
		}
		if (v.isTextual()) {
			return !v.textValue().isEmpty();
		}
		return v.size() > 0;
	}

	private static boolean equal(final JsonNode a, final JsonNode b) {
		final boolean an = a == null || a.isNull() || a.isMissingNode();
		final boolean bn = b == null || b.isNull() || b.isMissingNode();
		if (an || bn) {
			return an && bn;
		}
		if (a.isNumber() && b.isNumber()) {
			return a.decimalValue().compareTo(b.decimalValue()) == 0;
		}
		return a.equals(b);
	}

	private static Integer order(final JsonNode a, final JsonNode b) {
		if (a != null && b != null && a.isNumber() && b.isNumber()) {
			return a.decimalValue().compareTo(b.decimalValue());
		}
		if (a != null && b != null && a.isTextual() && b.isTextual()) {
			return a.textValue().compareTo(b.textValue());
		}
		return null;
	}

	private static JsonNode bool(final boolean b) {
		return F.booleanNode(b);
	}

	//=================================================================================================
	// lexer

	private enum Kind {
		REF, NUMBER, STRING, WORD, OP, LPAREN, RPAREN
	}

	private record Token(Kind kind, String text, int position) {
	}

	private static List<Token> tokenize(final String s) {
		final List<Token> out = new ArrayList<>();
		int i = 0;
		while (i < s.length()) {
			final char c = s.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '$' && i + 1 < s.length() && s.charAt(i + 1) == '{') {
				final int end = s.indexOf('}', i);
				if (end < 0) {
					throw new IllegalArgumentException("unterminated reference at " + i);
				}
				out.add(new Token(Kind.REF, s.substring(i + 2, end).trim(), i));
				i = end + 1;
			} else if (c == '\'' || c == '"') {
				final StringBuilder sb = new StringBuilder();
				int j = i + 1;
				while (j < s.length() && s.charAt(j) != c) {
					if (s.charAt(j) == '\\' && j + 1 < s.length()) {
						j++;
					}
					sb.append(s.charAt(j++));
				}
				if (j >= s.length()) {
					throw new IllegalArgumentException("unterminated string at " + i);
				}
				out.add(new Token(Kind.STRING, sb.toString(), i));
				i = j + 1;
			} else if (Character.isDigit(c) || (c == '-' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)) && numberMayStart(out))) {
				int j = i + 1;
				while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.' || s.charAt(j) == 'e' || s.charAt(j) == 'E'
						|| ((s.charAt(j) == '-' || s.charAt(j) == '+') && (s.charAt(j - 1) == 'e' || s.charAt(j - 1) == 'E')))) {
					j++;
				}
				out.add(new Token(Kind.NUMBER, s.substring(i, j), i));
				i = j;
			} else if (Character.isLetter(c)) {
				int j = i + 1;
				while (j < s.length() && Character.isLetter(s.charAt(j))) {
					j++;
				}
				out.add(new Token(Kind.WORD, s.substring(i, j), i));
				i = j;
			} else if (c == '(') {
				out.add(new Token(Kind.LPAREN, "(", i++));
			} else if (c == ')') {
				out.add(new Token(Kind.RPAREN, ")", i++));
			} else {
				final String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
				if (List.of("&&", "||", "==", "!=", "<=", ">=").contains(two)) {
					out.add(new Token(Kind.OP, two, i));
					i += 2;
				} else if (c == '<' || c == '>' || c == '!') {
					out.add(new Token(Kind.OP, String.valueOf(c), i++));
				} else {
					throw new IllegalArgumentException("unexpected character '" + c + "' at " + i);
				}
			}
		}
		return out;
	}

	/** A '-' starts a negative number unless it follows an operand. */
	private static boolean numberMayStart(final List<Token> before) {
		if (before.isEmpty()) {
			return true;
		}
		final Kind k = before.get(before.size() - 1).kind;
		return k == Kind.OP || k == Kind.LPAREN;
	}

	//=================================================================================================
	// parser

	private final class Parser {

		private final List<Token> tokens;
		private int at;

		Parser(final List<Token> tokens) {
			this.tokens = tokens;
		}

		boolean atEnd() {
			return at >= tokens.size();
		}

		Token peek() {
			return tokens.get(at);
		}

		private boolean acceptOp(final String op) {
			if (!atEnd() && peek().kind == Kind.OP && peek().text.equals(op)) {
				at++;
				return true;
			}
			return false;
		}

		Node expression() {
			Node left = and();
			while (acceptOp("||")) {
				final Node l = left;
				final Node r = and();
				left = (rho, xi) -> bool(truthy(l.eval(rho, xi)) || truthy(r.eval(rho, xi)));
			}
			return left;
		}

		private Node and() {
			Node left = unary();
			while (acceptOp("&&")) {
				final Node l = left;
				final Node r = unary();
				left = (rho, xi) -> bool(truthy(l.eval(rho, xi)) && truthy(r.eval(rho, xi)));
			}
			return left;
		}

		private Node unary() {
			if (acceptOp("!")) {
				final Node inner = unary();
				return (rho, xi) -> bool(!truthy(inner.eval(rho, xi)));
			}
			return compare();
		}

		private Node compare() {
			final Node left = operand();
			if (!atEnd() && peek().kind == Kind.OP && List.of("==", "!=", "<", "<=", ">", ">=").contains(peek().text)) {
				final String op = tokens.get(at++).text;
				final Node right = operand();
				return switch (op) {
				case "==" -> (rho, xi) -> bool(equal(left.eval(rho, xi), right.eval(rho, xi)));
				case "!=" -> (rho, xi) -> bool(!equal(left.eval(rho, xi), right.eval(rho, xi)));
				default -> (rho, xi) -> {
					final Integer c = order(left.eval(rho, xi), right.eval(rho, xi));
					if (c == null) {
						return bool(false);
					}
					return bool(switch (op) {
					case "<" -> c < 0;
					case "<=" -> c <= 0;
					case ">" -> c > 0;
					default -> c >= 0;
					});
				};
				};
			}
			return left;
		}

		private Node operand() {
			if (atEnd()) {
				throw new IllegalArgumentException("condition ends where an operand is expected");
			}
			final Token t = tokens.get(at++);
			switch (t.kind) {
			case LPAREN: {
				final Node inner = expression();
				if (atEnd() || peek().kind != Kind.RPAREN) {
					throw new IllegalArgumentException("missing ')' for '(' at " + t.position);
				}
				at++;
				return inner;
			}
			case NUMBER: {
				final JsonNode n;
				try {
					n = F.numberNode(new BigDecimal(t.text));
				} catch (final NumberFormatException ex) {
					throw new IllegalArgumentException("bad number '" + t.text + "' at " + t.position);
				}
				return (rho, xi) -> n;
			}
			case STRING: {
				final JsonNode s = F.textNode(t.text);
				return (rho, xi) -> s;
			}
			case WORD: {
				final JsonNode w = switch (t.text) {
				case "true" -> F.booleanNode(true);
				case "false" -> F.booleanNode(false);
				case "null" -> F.nullNode();
				default -> throw new IllegalArgumentException("unknown word '" + t.text + "' at " + t.position + " (use ${param.x} or ${result.step.x})");
				};
				return (rho, xi) -> w;
			}
			case REF:
				return reference(t);
			default:
				throw new IllegalArgumentException("unexpected '" + t.text + "' at " + t.position);
			}
		}

		private Node reference(final Token t) {
			final String[] parts = t.text.split("\\.");
			if (parts.length < 2 || !(parts[0].equals("param") || parts[0].equals("result")) || !parts[1].matches("[A-Za-z][A-Za-z0-9_-]*")) {
				throw new IllegalArgumentException("reference ${" + t.text + "} at " + t.position + " must be ${param.<name>…} or ${result.<step>…}");
			}
			final boolean param = parts[0].equals("param");
			final String name = parts[1];
			(param ? parameters : steps).add(name);
			final String[] path = java.util.Arrays.copyOfRange(parts, 2, parts.length);
			return (rho, xi) -> {
				JsonNode v = param ? rho.get(name) : xi.get(name);
				for (final String segment : path) {
					v = v == null ? null : v.isArray() && segment.matches("\\d+") ? v.get(Integer.parseInt(segment)) : v.get(segment);
				}
				return v == null ? F.nullNode() : v;
			};
		}
	}
}
