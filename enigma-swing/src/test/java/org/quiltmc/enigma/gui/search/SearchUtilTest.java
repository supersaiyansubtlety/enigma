package org.quiltmc.enigma.gui.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;

public class SearchUtilTest {
	@ParameterizedTest
	@MethodSource("streamWordwiseExpectations")
	void testWordwiseSplit(WordwiseExpectation expectation) {
		assertThat(SearchUtil.Entry.WORD_END.split(expectation.input), arrayContaining(expectation.output));
	}

	private static Stream<WordwiseExpectation> streamWordwiseExpectations() {
		return Stream.of(
			WordwiseExpectation.of("single", "single"),
			WordwiseExpectation.of("SINGLE", "SINGLE"),
			WordwiseExpectation.of("UpperCamelCase", "Upper", "Camel", "Case"),
			WordwiseExpectation.of("lowerCamelCase", "lower", "Camel", "Case"),
			WordwiseExpectation.of("SCREAMUpperCamel", "SCREAM", "Upper", "Camel"),
			WordwiseExpectation.of("lowerSCREAMCamel", "lower", "SCREAM", "Camel"),
			WordwiseExpectation.of("lowerCamelSCREAM", "lower", "Camel", "SCREAM"),
			WordwiseExpectation.of("lower_snake_case", "lower", "_", "snake", "_", "case"),
			WordwiseExpectation.of("SCREAMING_SNAKE_CASE", "SCREAMING", "_", "SNAKE", "_", "CASE"),
			WordwiseExpectation.of("class_932", "class", "_", "932"),
			WordwiseExpectation.of("X11FontManager", "X", "11", "Font", "Manager")
		);
	}

	record WordwiseExpectation(String input, String... output) {
		private static WordwiseExpectation of(String input, String... output) {
			if (output.length < 1) {
				throw new IllegalArgumentException("output must not be empty!");
			}

			if (!String.join("", output).equals(input)) {
				throw new IllegalArgumentException("output must be concatenation of input!");
			}

			return new WordwiseExpectation(input, output);
		}
	}
}
