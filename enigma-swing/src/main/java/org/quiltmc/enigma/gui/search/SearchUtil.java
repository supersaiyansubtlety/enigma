package org.quiltmc.enigma.gui.search;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.quiltmc.enigma.util.Pair;
import org.quiltmc.enigma.util.Regex;
import org.quiltmc.enigma.util.Utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.quiltmc.enigma.util.Regex.OR;
import static org.quiltmc.enigma.util.Regex.lookAhead;
import static org.quiltmc.enigma.util.Regex.lookBehind;
import static org.quiltmc.enigma.util.Regex.negativeLookBehind;

public class SearchUtil<T extends SearchEntry> implements Regex.Constructs, Regex.Expressions {
	private final Map<T, Entry<T>> entries = new HashMap<>();
	/**
	 * The number of times a {@link SearchEntry} has been {@link #hit(SearchEntry)}, mapped by the search entry's
	 * {@linkplain SearchEntry#getIdentifier() identifier}.
	 *
	 * <p> More hits result in higher {@linkplain Entry#getScore(String, int) entry scores}.
	 */
	private final Map<String, Integer> hitsById = new HashMap<>();
	private final Executor searchExecutor = Executors.newWorkStealingPool();

	public void add(T entry) {
		Entry<T> e = Entry.of(entry);
		this.entries.put(entry, e);
	}

	public void add(Entry<T> entry) {
		this.entries.put(entry.searchEntry, entry);
	}

	public void addAll(Collection<T> entries) {
		this.entries.putAll(entries.parallelStream().collect(Collectors.toMap(e -> e, Entry::of)));
	}

	public void remove(T entry) {
		this.entries.remove(entry);
	}

	public void clear() {
		this.entries.clear();
	}

	public void clearHits() {
		this.hitsById.clear();
	}

	public Stream<T> search(String term) {
		return this.entries.values().parallelStream()
				.map(e -> new Pair<>(e, e.getScore(term, this.hitsById.getOrDefault(e.searchEntry.getIdentifier(), 0))))
				.filter(e -> e.b() > 0)
				.sorted(Comparator.comparingDouble(o -> -o.b()))
				.map(e -> e.a().searchEntry)
				.sequential();
	}

	public SearchControl asyncSearch(String term, SearchResultConsumer<T> consumer, boolean onlyExactMatches) {
		final ImmutableMap<String, Integer> hitsById = ImmutableMap.copyOf(this.hitsById);
		Map<T, Entry<T>> entries = new HashMap<>(this.entries);
		float[] scores = new float[entries.size()];
		Lock scoresLock = new ReentrantLock();
		AtomicInteger size = new AtomicInteger();
		AtomicBoolean control = new AtomicBoolean(false);
		AtomicInteger elapsed = new AtomicInteger();

		for (Entry<T> value : entries.values()) {
			this.searchExecutor.execute(() -> {
				try {
					if (control.get()) {
						return;
					}

					// if onlyExactMatches is true, don't add any entries that don't have an exact match
					if (onlyExactMatches && value.searchEntry.getSearchableNames().stream().noneMatch(name -> name.equalsIgnoreCase(term))) {
						return;
					}

					// non-null default -> getOrDefault returns non-null
					@SuppressWarnings("DataFlowIssue")
					float score = value.getScore(term, hitsById.getOrDefault(value.searchEntry.getIdentifier(), 0));
					if (score <= 0) {
						return;
					}

					score = -score; // sort descending
					try {
						scoresLock.lock();
						if (control.get()) {
							return;
						}

						int dataSize = size.getAndIncrement();
						int index = Arrays.binarySearch(scores, 0, dataSize, score);
						if (index < 0) {
							index = ~index;
						}

						System.arraycopy(scores, index, scores, index + 1, dataSize - index);
						scores[index] = score;
						consumer.add(index, value.searchEntry);
					} finally {
						scoresLock.unlock();
					}
				} finally {
					elapsed.incrementAndGet();
				}
			});
		}

		return new SearchControl() {
			@Override
			public void stop() {
				control.set(true);
			}

			@Override
			public boolean isFinished() {
				return entries.size() == elapsed.get();
			}

			@Override
			public float getProgress() {
				return (float) elapsed.get() / entries.size();
			}
		};
	}

	public void hit(T entry) {
		if (this.entries.containsKey(entry)) {
			this.hitsById.compute(entry.getIdentifier(), (id, i) -> i == null ? 1 : i + 1);
		}
	}

	public static final class Entry<T extends SearchEntry> {
		/**
		 * Matches the ends of words; used for {@linkplain Pattern#split(CharSequence) splitting}.
		 *
		 * <p> All matches are 0-length, so the concatenation of split words yields the un-split input.
		 *
		 * <p> Splitting examples:
		 * <table>
		 *     <tr><th>input</th><th>output</th></tr>
		 *     <tr><td>{@code MinecraftClientGame}</td><td>{@code Minecraft}, {@code Client}, {@code Game}</td></tr>
		 *     <tr><td>{@code HTTPInputStream}</td><td>{@code HTTP}, {@code Input}, {@code Stream}</td></tr>
		 *     <tr><td>{@code class_932}</td><td>{@code class}, {@code _}, {@code 932}</td></tr>
		 *     <tr><td>{@code X11FontManager}</td><td>{@code X}, {@code 11}, {@code Font}, {@code Manager}</td></tr>
		 *     <tr><td>{@code openHTTPConnection}</td><td>{@code open}, {@code HTTP}, {@code Connection}</td></tr>
		 *     <tr>
		 *         <td>{@code open_http_connection}</td>
		 *         <td>{@code open}, {@code _}, {@code http}, {@code _}, {@code connection}</td>
		 *     </tr>
		 * </table>
		 */
		@VisibleForTesting
		static final Pattern WORD_END = Pattern.compile(String.join(
				OR,
				// after letters
				lookBehind(LETTER) + lookAhead(NON_LETTER),
				// after non-letters
				lookBehind(NON_LETTER) + lookAhead(LETTER),
				// before uppercase to lowercase; leave last uppercase as start of next word
				lookAhead(UPPERCASE + LOWERCASE),
				// after lowercase to uppercase
				lookBehind(LOWERCASE) + lookAhead(UPPERCASE),
				// after digits
				lookBehind(DIGIT) + lookAhead(NON_DIGIT),
				// after any non-letter, non-digit character
				negativeLookBehind("[" + LETTER + DIGIT + "]")
		));

		public final T searchEntry;
		private final ImmutableList<ImmutableList<String>> searchableNameWords;

		private Entry(T searchEntry, ImmutableList<ImmutableList<String>> searchableNameWords) {
			this.searchEntry = searchEntry;
			this.searchableNameWords = searchableNameWords;
		}

		public float getScore(String term, int hits) {
			String ucTerm = term.toUpperCase(Locale.ROOT);
			float maxScore;

			// if exact match, make sure it's at the top of the list
			if (this.searchEntry.getSearchableNames().stream().anyMatch(name -> name.equalsIgnoreCase(term))) {
				maxScore = Float.MAX_VALUE / 2;
			} else {
				maxScore = (float) this.searchableNameWords.stream()
						.mapToDouble(nameWords -> getScoreFor(ucTerm, nameWords))
						.max()
						.orElse(0.0);
			}

			// modify by type
			return maxScore * (hits + 1) * this.searchEntry.getTypePriority();
		}

		/**
		 * Computes the score for the given <code>nameWords</code> against the given search term.
		 *
		 * @param term the search term (expected to be upper-case)
		 * @param nameWords the entry name, split at word boundaries (see {@link Entry#WORD_END})
		 *
		 * @return the computed score for the entry
		 */
		private static float getScoreFor(String term, List<String> nameWords) {
			int totalLength = nameWords.stream().mapToInt(String::length).sum();
			float scorePerChar = 1f / totalLength;

			// This map contains a snapshot of all the states the search has
			// been in. The keys are the remaining characters of the search
			// term, the values are the maximum scores for that remaining
			// search term part.
			Map<String, Float> snapshots = new HashMap<>();
			snapshots.put(term, 0f);

			// For each word, start at each existing snapshot, searching
			// for the next longest match, and calculate the new score for each
			// match length until the maximum. Then the new scores are put back
			// into the snapshot map.
			for (int iWord = 0; iWord < nameWords.size(); iWord++) {
				final String word = nameWords.get(iWord).toUpperCase(Locale.ROOT);
				final float posMultiplier = (nameWords.size() - iWord) * 0.3f;
				final Map<String, Float> newSnapshots = new HashMap<>();
				for (final Map.Entry<String, Float> snapshot : snapshots.entrySet()) {
					final String remaining = snapshot.getKey();
					final float score = snapshot.getValue();
					final int commonPrefixLength = Utils.getCommonPrefixLength(remaining, word);
					for (int i = 1; i <= commonPrefixLength; i++) {
						final float baseScore = scorePerChar * i;
						final float chainBonus = (i - 1) * 0.5f;
						merge(newSnapshots, Collections.singletonMap(remaining.substring(i), score + baseScore * posMultiplier + chainBonus), Math::max);
					}
				}

				merge(snapshots, newSnapshots, Math::max);
			}

			// Only return the score for when the search term was completely consumed.
			return snapshots.getOrDefault("", 0f);
		}

		private static <K, V> void merge(Map<K, V> target, Map<K, V> source, BiFunction<V, V, V> combiner) {
			source.forEach((k, v) -> {
				if (v != null) {
					target.merge(k, v, combiner);
				}
			});
		}

		public static <T extends SearchEntry> Entry<T> of(T e) {
			return new Entry<>(e, e
					.getSearchableNames()
					.parallelStream()
					.map(WORD_END::split)
					.map(ImmutableList::copyOf)
					.collect(toImmutableList())
			);
		}
	}

	@FunctionalInterface
	public interface SearchResultConsumer<T extends SearchEntry> {
		void add(int index, T entry);
	}

	public interface SearchControl {
		void stop();

		boolean isFinished();

		float getProgress();

		final class Empty implements SearchControl {
			public static final Empty INSTANCE = new Empty();

			private Empty() { }

			@Override
			public void stop() { }

			@Override
			public boolean isFinished() {
				return true;
			}

			@Override
			public float getProgress() {
				return 1;
			}
		}
	}
}
