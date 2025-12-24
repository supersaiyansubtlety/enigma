package org.quiltmc.enigma.gui.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class SearchUtilTest {
	@ParameterizedTest
	@MethodSource("streamWordwiseExpectations")
	void testWordwiseSplit(WordwiseExpectation expectation) {
		assertThat(SearchUtil.Entry.wordwiseSplit(expectation.input), contains(expectation.output));
	}

	private static Stream<WordwiseExpectation> streamWordwiseExpectations() {
		return Stream.of(
			new WordwiseExpectation("UpperCamelCase", "Upper", "Camel", "Case"),
			new WordwiseExpectation("lowerCamelCase", "lower", "Camel", "Case"),
			new WordwiseExpectation("SCREAMUpperCamel", "SCREAM", "Upper", "Camel"),
			new WordwiseExpectation("lowerSCREAMCamel", "lower", "SCREAM", "Camel"),
			new WordwiseExpectation("lowerCamelSCREAM", "lower", "Camel", "SCREAM"),
			new WordwiseExpectation("lower_snake_case", "lower", "_", "snake", "_", "case"),
			new WordwiseExpectation("SCREAMING_SNAKE_CASE", "SCREAMING", "_", "SNAKE", "_", "CASE"),
			new WordwiseExpectation("class_932", "class", "_", "932"),
			new WordwiseExpectation("X11FontManager", "X", "11", "Font", "Manager")
		);
	}

	record WordwiseExpectation(String input, String... output) { }
}
