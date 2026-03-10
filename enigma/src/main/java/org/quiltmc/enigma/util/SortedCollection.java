package org.quiltmc.enigma.util;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * A collection that keeps its elements sorted.
 *
 * <p> Element order is determined by the {@link Comparator} passed when creating the collection.<br>
 * Insertion order is maintained for elements whose
 * {@linkplain Comparator#compare(Object, Object) comparisons} are {@code 0}.
 *
 * @implNote {@code null} elements are <em>not</em> supported
 *
 * @param <T> the type of elements
 */
public class SortedCollection<T> implements Collection<T> {
	private final ArrayList<Equivalents> delegate = new ArrayList<>();

	private final Comparator<T> comparator;
	private final Function<Object, @Nullable T> castOrNull;

	private int size;

	public SortedCollection(Comparator<T> comparator, Function<Object, @Nullable T> castOrNull) {
		this.comparator = comparator;
		this.castOrNull = castOrNull;
	}

	@Override
	public int size() {
		return this.size;
	}

	@Override
	public boolean isEmpty() {
		return this.size == 0;
	}

	@Override
	public boolean contains(Object o) {
		final T element = this.castOrNull.apply(o);

		if (element == null) {
			return false;
		}

		final int found = Collections.binarySearch(this.delegate, new Equivalents(element));
		return found >= 0 && this.delegate.get(found).elements.stream().anyMatch(e -> e.equals(element));
	}

	@Override
	@NonNull
	public Iterator<T> iterator() {
		return new Itr();
	}

	@Override
	public Object @NonNull[] toArray() {
		return CollectionUtils.toArrayImpl(this);
	}

	@Override
	public <T1> T1 @NonNull[] toArray(T1 @NonNull[] a) {
		return CollectionUtils.toArrayImpl(this, a);
	}

	@Override
	public boolean add(T element) {
		Utils.requireNonNull(element, "element");

		final Equivalents newEquivalents = new Equivalents(element);
		final int found = Collections.binarySearch(this.delegate, newEquivalents);
		if (found < 0) {
			final int dest = -found - 1;
			this.delegate.add(dest, newEquivalents);
		} else {
			this.delegate.get(found).elements.add(element);
		}

		this.size++;
		return true;
	}

	@Override
	public boolean remove(Object o) {
		final T element = this.castOrNull.apply(o);

		if (element == null) {
			return false;
		}

		final int found = Collections.binarySearch(this.delegate, new Equivalents(element));
		if (found >= 0) {
			final List<T> elements = this.delegate.get(found).elements;
			if (elements.remove(element)) {
				// remove empty equivalents
				if (elements.isEmpty()) {
					this.delegate.remove(found);
				}

				this.size--;
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean containsAll(@NonNull Collection<?> collection) {
		for (final Object o : collection) {
			if (!this.contains(o)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean addAll(@NonNull Collection<? extends T> collection) {
		this.delegate.ensureCapacity(this.size + collection.size());

		for (final T element : collection) {
			this.add(element);
		}

		return true;
	}

	@Override
	public boolean removeAll(@NonNull Collection<?> collection) {
		boolean removed = false;
		for (final Object o : collection) {
			removed |= this.remove(o);
		}

		return removed;
	}

	@Override
	public boolean retainAll(@NonNull Collection<?> collection) {
		return this.removeIf(element -> !collection.contains(element));
	}

	@Override
	public void clear() {
		this.delegate.clear();
		this.size = 0;
	}

	private final class Equivalents implements Comparable<Equivalents> {
		final List<T> elements;

		private Equivalents(T element) {
			this.elements = new LinkedList<>();
			this.elements.add(element);
		}

		@Override
		public int compareTo(@NonNull Equivalents other) {
			return SortedCollection.this.comparator
				.compare(this.elements.get(0), other.elements.get(0));
		}
	}

	private class Itr implements Iterator<T> {
		final Iterator<Equivalents> outerIterator = SortedCollection.this.delegate.iterator();
		@Nullable
		InnerState<T> innerState;

		@Override
		public boolean hasNext() {
			return this.outerIterator.hasNext() || this.innerState != null && this.innerState.iterator.hasNext();
		}

		@Override
		public T next() {
			if (this.innerState == null || !this.innerState.iterator.hasNext()) {
				this.innerState = InnerState.of(this.outerIterator.next().elements);
			}

			return this.innerState.iterator.next();
		}

		@Override
		public void remove() {
			if (this.innerState == null) {
				throw new IllegalStateException("remove called before any call to next");
			}

			this.innerState.iterator.remove();
			SortedCollection.this.size--;
			if (this.innerState.equivalents.isEmpty()) {
				this.outerIterator.remove();
			}
		}

		record InnerState<T>(List<T> equivalents, Iterator<T> iterator) {
			static <T> InnerState<T> of(List<T> equivalents) {
				return new InnerState<>(equivalents, equivalents.iterator());
			}
		}
	}
}
