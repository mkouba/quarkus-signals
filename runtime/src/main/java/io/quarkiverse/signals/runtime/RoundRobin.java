package io.quarkiverse.signals.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Selects elements in round-robin order from an immutable sequence.
 * <p>
 * Thread-safe: an {@link AtomicInteger} drives the position, so concurrent
 * {@link #next()} calls rotate through the elements without locking.
 * <p>
 * The sequence itself is immutable; {@link #add(Object)} and {@link #remove(Object)}
 * return a new copy (copy-on-write), so a holder can swap instances via a volatile field.
 *
 * @param <T> the element type
 */
class RoundRobin<T> implements Iterable<T> {

    private static final Object[] EMPTY_ARRAY = new Object[0];

    private final AtomicInteger pos;
    private final Object[] elements;

    RoundRobin() {
        this(0, EMPTY_ARRAY);
    }

    @SafeVarargs
    RoundRobin(T... elements) {
        this(0, Arrays.copyOf(elements, elements.length, Object[].class));
    }

    private RoundRobin(int pos, Object[] elements) {
        this.pos = new AtomicInteger(pos);
        this.elements = elements;
    }

    /**
     * Returns the next element in round-robin order, cycling back to the first element
     * after the last one has been returned.
     *
     * @return the next element, or {@code null} when the sequence is empty
     */
    @SuppressWarnings("unchecked")
    T next() {
        if (elements.length == 0) {
            return null;
        } else if (elements.length == 1) {
            return (T) elements[0];
        } else {
            return (T) elements[Integer.remainderUnsigned(pos.getAndIncrement(), elements.length)];
        }
    }

    /**
     * Returns a new sequence with {@code element} appended at the tail.
     *
     * @param element the element to add
     * @return the new sequence
     */
    RoundRobin<T> add(T element) {
        int len = elements.length;
        Object[] copy = Arrays.copyOf(elements, len + 1);
        copy[len] = element;
        return new RoundRobin<>(pos.get(), copy);
    }

    /**
     * Returns a new sequence with the first occurrence of {@code element} removed.
     * <p>
     * If the sequence does not contain {@code element}, {@code this} is returned.
     *
     * @param element the element to remove
     * @return the new sequence, or {@code this} if unchanged
     */
    RoundRobin<T> remove(T element) {
        int len = elements.length;
        for (int i = 0; i < len; i++) {
            if (Objects.equals(element, elements[i])) {
                if (len > 1) {
                    Object[] copy = new Object[len - 1];
                    System.arraycopy(elements, 0, copy, 0, i);
                    System.arraycopy(elements, i + 1, copy, i, len - i - 1);
                    return new RoundRobin<>(pos.get() % copy.length, copy);
                } else {
                    return new RoundRobin<>();
                }
            }
        }
        return this;
    }

    int size() {
        return elements.length;
    }

    boolean isEmpty() {
        return elements.length == 0;
    }

    @SuppressWarnings("unchecked")
    List<T> elements() {
        return (List<T>) List.of(elements);
    }

    /**
     * Returns an iterator over all elements in insertion order (not cycling).
     */
    @Override
    public Iterator<T> iterator() {
        int len = elements.length;
        if (len == 0) {
            return Collections.emptyIterator();
        } else if (len == 1) {
            return new SingletonIter();
        } else {
            return new Iter();
        }
    }

    @SuppressWarnings("unchecked")
    private class SingletonIter implements Iterator<T> {
        boolean hasNext = true;

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public T next() {
            if (hasNext) {
                hasNext = false;
                return (T) elements[0];
            }
            throw new NoSuchElementException();
        }
    }

    @SuppressWarnings("unchecked")
    private class Iter implements Iterator<T> {
        int cursor;

        @Override
        public boolean hasNext() {
            return cursor < elements.length;
        }

        @Override
        public T next() {
            if (cursor >= elements.length) {
                throw new NoSuchElementException();
            }
            return (T) elements[cursor++];
        }
    }
}
