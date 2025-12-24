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
			new WordwiseExpectation("MinecraftClientGame", "Minecraft", "Client", "Game"),
			new WordwiseExpectation("HTTPInputStream", "HTTP", "Input", "Stream"),
			new WordwiseExpectation("class_932", "class", "_", "932"),
			new WordwiseExpectation("X11FontManager", "X", "11", "Font", "Manager"),
			new WordwiseExpectation("openHTTPConnection", "open", "HTTP", "Connection"),
			new WordwiseExpectation("open_http_connection", "open", "_", "http", "_", "connection")
		);
	}

	record WordwiseExpectation(String input, String... output) { }
}
