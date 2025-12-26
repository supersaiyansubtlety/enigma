package org.quiltmc.enigma.gui.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;

public class SearchUtilTest {
	@ParameterizedTest
	@MethodSource("streamWordEndExpectations")
	void testWordEnd(WordEndExpectation expectation) {
		assertThat(SearchUtil.Entry.WORD_END.split(expectation.input), arrayContaining(expectation.split));
	}

	private static Stream<WordEndExpectation> streamWordEndExpectations() {
		return Stream.of(
			WordEndExpectation.of("single", "single"),
			WordEndExpectation.of("SINGLE", "SINGLE"),
			WordEndExpectation.of("UpperCamelCase", "Upper", "Camel", "Case"),
			WordEndExpectation.of("lowerCamelCase", "lower", "Camel", "Case"),
			WordEndExpectation.of("SCREAMUpperCamel", "SCREAM", "Upper", "Camel"),
			WordEndExpectation.of("lowerSCREAMCamel", "lower", "SCREAM", "Camel"),
			WordEndExpectation.of("lowerCamelSCREAM", "lower", "Camel", "SCREAM"),
			WordEndExpectation.of("lower_snake_case", "lower", "_", "snake", "_", "case"),
			WordEndExpectation.of("SCREAMING_SNAKE_CASE", "SCREAMING", "_", "SNAKE", "_", "CASE"),
			WordEndExpectation.of("class_932", "class", "_", "932"),
			WordEndExpectation.of("X11FontManager", "X", "11", "Font", "Manager")
		);
	}

	record WordEndExpectation(String input, String... split) {
		private static WordEndExpectation of(String input, String... split) {
			if (split.length < 1) {
				throw new IllegalArgumentException("split must not be empty!");
			}

			if (!String.join("", split).equals(input)) {
				throw new IllegalArgumentException("split must be concatenation of input!");
			}

			return new WordEndExpectation(input, split);
		}
	}
}
