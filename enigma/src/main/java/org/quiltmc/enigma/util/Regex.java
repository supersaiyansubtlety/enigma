package org.quiltmc.enigma.util;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Utilities for composing regular expressions.
 *
 * @see Pattern
 */
public final class Regex {
	private Regex() {
		throw new UnsupportedOperationException();
	}

	public static final String OR = "|";

	/**
	 * Wraps the passed {@code regex} in a positive look-ahead group.
	 *
	 * @see #lookBehind(String)
	 * @see #negativeLookahead(String)
	 */
	public static String lookAhead(String regex) {
		return "(?=" + regex + ")";
	}

	/**
	 * Wraps the passed {@code regex} in a negative look-ahead group.
	 *
	 * @see #lookAhead(String)
	 * @see #negativeLookBehind(String)
	 */
	public static String negativeLookahead(String regex) {
		return "(?!" + regex + ")";
	}

	/**
	 * Wraps the passed {@code regex} in a positive look-behind group.
	 *
	 * <p> The passed {@code regex} must be constant-length, otherwise {@link Pattern#compile(String)} will throw a
	 * {@link PatternSyntaxException}.
	 *
	 * @see #negativeLookBehind(String)
	 * @see #lookAhead(String)
	 */
	public static String lookBehind(String regex) {
		return "(?<=" + regex + ")";
	}

	/**
	 * Wraps the passed {@code regex} in a negative look-behind group.
	 *
	 * <p> The passed {@code regex} must be constant-length, otherwise {@link Pattern#compile(String)} will throw a
	 * {@link PatternSyntaxException}.
	 *
	 * @see #lookBehind(String)
	 * @see #negativeLookahead(String)
	 */
	public static String negativeLookBehind(String regex) {
		return "(?<!" + regex + ")";
	}

	public interface Constructs {
		// Unicode binary properties
		String LETTER = "\\p{IsLetter}";
		String UPPERCASE = "\\p{IsUppercase}";
		String LOWERCASE = "\\p{IsLowercase}";
		String DIGIT = "\\p{IsDigit}";
	}

	public interface Expressions {
		String NON_LETTER = "[^" + Constructs.LETTER + "]";
		String NON_DIGIT = "[^" + Constructs.DIGIT + "]";
	}
}
