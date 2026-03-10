package org.quiltmc.enigma.util;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * Utilities for working with {@link Collection}s and arrays.
 */
public final class CollectionUtils {
	private CollectionUtils() {
		throw new UnsupportedOperationException();
	}

	@SafeVarargs
	public static <T> Optional<T> findFirstNonNull(T... values) {
		for (final T value : values) {
			if (value != null) {
				return Optional.of(value);
			}
		}

		return Optional.empty();
	}

	/**
	 * @return {@code null} if the passed {@code array} is {@code null} or empty,
	 * or the last element of the {@code array} otherwise
	 */
	@Nullable
	public static <T> T getLastOrNull(@Nullable T[] array) {
		return array == null || array.length == 0 ? null : array[array.length - 1];
	}

	public static <T> Set<T> createIdentityHashSet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	public static <T> Set<T> createIdentityHashSet(int expectedMaxSize) {
		return Collections.newSetFromMap(new IdentityHashMap<>(expectedMaxSize));
	}

	/**
	 * A common implementation of {@link Collection#toArray()}.
	 */
	public static <T> Object @NonNull [] toArrayImpl(Collection<T> collection) {
		final int size = collection.size();
		final Object[] array = new Object[size];
		final Iterator<T> itr = collection.iterator();
		for (int i = 0; i < size; i++) {
			array[i] = itr.next();
		}

		return array;
	}

	/**
	 * A common implementation of {@link Collection#toArray(Object[])}.
	 *
	 * <p> Based on {@link HashMap}{@code ::prepareArray} and {@link HashMap}{@code ::keysToArray}.
	 */
	@SuppressWarnings("unchecked")
	public static <T, T1> T1 @NonNull[] toArrayImpl(Collection<T> collection, T1[] a) {
		final int size = collection.size();
		if (a.length < size) {
			a = (T1[]) java.lang.reflect.Array
				.newInstance(a.getClass().getComponentType(), size);
		} else if (a.length > size) {
			a[size] = null;
		}

		final Iterator<T> itr = collection.iterator();
		final Object[] objects = a;
		for (int i = 0; i < size; i++) {
			objects[i] = itr.next();
		}

		return a;
	}
}
