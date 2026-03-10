package org.quiltmc.enigma.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import org.hamcrest.Matchers;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.Iterator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SortedCollectionTest {
	private static final ImmutableList<Integer> VALUES = ImmutableList.of(
			1, 5, 3, 3, 7, 5, 4, 8, 9, 0, 3, 2
	);

	private static SortedCollection<Integer> createSortedInts() {
		return new SortedCollection<>(
			Integer::compare,
			o -> o instanceof Integer integer ? integer : null
		);
	}

	@Test
	void sorts() {
		final SortedCollection<Integer> sorted = createSortedInts();

		for (int i = 0; i < VALUES.size(); i++) {
			sorted.add(VALUES.get(i));
			final Integer[] expectation = VALUES.subList(0, i + 1)
				.stream()
				.sorted()
				.toArray(Integer[]::new);

			assertThat(sorted, Matchers.contains(expectation));
		}
	}

	@Test
	void maintainsInsertionOrder() {
		final SortedCollection<InsertedValue> sorted = new SortedCollection<>(
				InsertedValue.VALUE_COMPARATOR,
				InsertedValue::castOrNull
		);

		for (int i = 0; i < VALUES.size(); i++) {
			sorted.add(new InsertedValue(VALUES.get(i), i));

			@SuppressWarnings("DataFlowIssue")
			final InsertedValue[] expectation = Streams.mapWithIndex(
					VALUES.subList(0, i + 1).stream(),
					(value, index) -> new InsertedValue(value, (int) index)
				)
				.sorted(InsertedValue.EXPECTATION_COMPARATOR)
				.toArray(InsertedValue[]::new);

			assertThat(sorted, Matchers.contains(expectation));
		}
	}

	@Test
	void remove() {
		final SortedCollection<Integer> sorted = createSortedInts();
		sorted.addAll(VALUES);

		int expectedSize = sorted.size();
		for (final int value : VALUES) {
			assertTrue(sorted.remove(value));

			assertEquals(--expectedSize, sorted.size());
		}
	}

	@Test
	void iteratorRemove() {
		final SortedCollection<Integer> sorted = createSortedInts();
		sorted.addAll(VALUES);

		final Iterator<Integer> itr = sorted.iterator();
		int expectedSize = sorted.size();
		while (itr.hasNext()) {
			itr.next();
			itr.remove();

			assertEquals(--expectedSize, sorted.size());
		}
	}

	private record InsertedValue(int value, int inserted) {
		static final Comparator<InsertedValue> VALUE_COMPARATOR = Comparator.comparingInt(InsertedValue::value);
		static final Comparator<InsertedValue> EXPECTATION_COMPARATOR = VALUE_COMPARATOR
			.thenComparingInt(InsertedValue::inserted);

		@Nullable
		static InsertedValue castOrNull(Object o) {
			return o instanceof InsertedValue insertedValue ? insertedValue : null;
		}
	}
}
