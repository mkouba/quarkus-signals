package io.quarkiverse.signals;

import java.lang.annotation.Annotation;
import java.util.Map;

import jakarta.enterprise.util.TypeLiteral;

import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.Uni;

/**
 * Allows the application to emit signals of a particular type and have them delivered to matching receivers.
 * <p>
 * A {@code Signal} may be injected:
 *
 * <pre>
 * &#064;Inject
 * Signal&lt;OrderPlaced&gt; orderPlaced;
 * </pre>
 *
 * Any combination of qualifiers may be specified at the injection point:
 *
 * <pre>
 * &#064;Inject
 * &#064;Urgent
 * Signal&lt;OrderPlaced&gt; urgentOrderPlaced;
 * </pre>
 *
 * <p>
 * Unlike CDI events, all matching receivers are always executed asynchronously.
 * <p>
 * A signal can be emitted in three ways:
 * <ul>
 * <li>{@link #publish(Object)} &mdash; delivers the signal to <em>all</em> matching receivers (multicast).</li>
 * <li>{@link #send(Object)} &mdash; delivers the signal to a <em>single</em> matching receiver, selected in round-robin order
 * (unicast, fire-and-forget).</li>
 * <li>{@link #request(Object, Class)} &mdash; delivers the signal to a <em>single</em> matching receiver and returns the
 * response (unicast, request-reply).</li>
 * </ul>
 *
 * For an injected {@code Signal}:
 * <ul>
 * <li>the <em>specified type</em> is the type parameter specified at the injection point, and</li>
 * <li>the <em>specified qualifiers</em> are the qualifiers specified at the injection point.</li>
 * </ul>
 *
 * <p>
 * The type-safe resolution of receivers follows the rules defined for CDI events in the
 * <a href="https://jakarta.ee/specifications/cdi/4.1/jakarta-cdi-spec-4.1#observer_resolution">CDI specification</a>,
 * with one notable difference: a receiver that declares no qualifiers is <em>not</em> automatically resolved for signals with
 * a matching type. In CDI, an observer method with no qualifiers implicitly has only {@code @Any} and therefore matches all
 * events of a matching type. In Signals, a receiver with no qualifiers implicitly has both {@code @Default} and {@code @Any},
 * and therefore only matches signals that also carry the {@code @Default} qualifier. In other words, a receiver must explicitly
 * declare a qualifier to match a qualified signal, and an unqualified receiver only matches unqualified signals.
 *
 * @param <T> the type of the signal object
 * @see Receives
 * @see Receiver
 */
public interface Signal<T> {

    /**
     * Obtains a child {@code Signal} for the given additional required qualifiers.
     *
     * @param qualifiers the additional specified qualifiers
     * @return the child {@code Signal}
     */
    Signal<T> select(Annotation... qualifiers);

    /**
     * Obtains a child {@code Signal} for the given required type and additional required qualifiers.
     *
     * @param <U> the specified type
     * @param subtype a {@link Class} representing the required type
     * @param qualifiers the additional specified qualifiers
     * @return the child {@code Signal}
     */
    <U extends T> Signal<U> select(Class<U> subtype, Annotation... qualifiers);

    /**
     * Obtains a child {@code Signal} for the given required type and additional required qualifiers.
     *
     * @param <U> the specified type
     * @param subtype a {@link TypeLiteral} representing the required type
     * @param qualifiers the additional specified qualifiers
     * @return the child {@code Signal}
     */
    <U extends T> Signal<U> select(TypeLiteral<U> subtype, Annotation... qualifiers);

    /**
     * Obtains a child {@code Signal} with the given metadata entry, replacing any previously added entry for the given key
     * entries.
     * <p>
     * <p>
     * The metadata entries will be used for all emissions from the returned signal instance.
     *
     * @param key
     * @param value
     * @return the child {@code Signal}
     */
    Signal<T> putMetadata(String key, Object value);

    /**
     * Obtains a child {@code Signal} with the given metadata, replacing any previously added metadata entries.
     * <p>
     * The metadata entries will be used for all emissions from the returned signal instance.
     *
     * @param metadata
     * @return the child {@code Signal}
     */
    Signal<T> setMetadata(Map<String, Object> metadata);

    /**
     * Sends a signal to <em>all</em> receivers matching the specified signal type and qualifiers (multicast).
     * <p>
     * All receivers are executed asynchronously.
     *
     * @param signal the signal object
     * @see Receives
     */
    default void publish(T signal) {
        multicast().emitAndForget(signal);
    }

    /**
     * Sends a signal to a <em>single</em> receiver matching the specified signal type, response type and qualifiers, and
     * returns the response (unicast, request-reply).
     * <p>
     * If multiple receivers match, one is selected in round-robin order.
     *
     * @param <R> the response type
     * @param signal the signal object
     * @param responseType the expected response type
     * @return a {@link Uni} that completes with the receiver's response
     * @see Receives
     */
    @CheckReturnValue
    default <R> Uni<R> request(T signal, Class<R> responseType) {
        return unicast().request(responseType).emit(signal);
    }

    /**
     * Sends a signal to a <em>single</em> receiver matching the specified signal type, response type and qualifiers, and
     * returns the response (unicast, request-reply).
     * <p>
     * If multiple receivers match, one is selected in round-robin order.
     *
     * @param <R> the response type
     * @param signal the signal object
     * @param responseType a {@link TypeLiteral} representing the expected response type
     * @return a {@link Uni} that completes with the receiver's response
     * @see Receives
     */
    @CheckReturnValue
    default <R> Uni<R> request(T signal, TypeLiteral<R> responseType) {
        return unicast().request(responseType).emit(signal);
    }

    /**
     * Sends a signal to a <em>single</em> receiver matching the specified signal type and qualifiers
     * (unicast, fire-and-forget).
     * <p>
     * If multiple receivers match, one is selected in round-robin order.
     *
     * @param signal the signal object
     * @see Receives
     */
    default void send(T signal) {
        unicast().emitAndForget(signal);
    }

    /**
     * Returns a unicast emission builder for sending a signal to a single receiver.
     * <p>
     * Use this to attach metadata or configure a request-reply emission before sending.
     *
     * @return unicast emission builder
     */
    Unicast<T> unicast();

    /**
     * Returns a multicast emission builder for sending a signal to all matching receivers.
     * <p>
     * Use this to attach metadata before sending.
     *
     * @return multicast emission builder
     */
    Multicast<T> multicast();

    /**
     * Base interface for signal emissions. Provides methods to emit the signal and attach metadata.
     *
     * @param <SIGNAL> the signal type
     * @param <R> the result type
     * @param <E> the self type for fluent chaining
     */
    interface Emission<SIGNAL, R, E extends Emission<SIGNAL, R, E>> {

        /**
         * Emits the signal to matching receivers.
         *
         * @param signal the signal object
         * @return a {@link Uni} that completes when the emission is processed
         */
        @CheckReturnValue
        Uni<R> emit(SIGNAL signal);

    }

    /**
     * A unicast emission that delivers the signal to a single matching receiver.
     *
     * @param <SIGNAL> the signal type
     */
    interface Unicast<SIGNAL> extends Emission<SIGNAL, Void, Unicast<SIGNAL>> {

        /**
         * Emits the signal to a single matching receiver (fire-and-forget).
         *
         * @param signal the signal object
         */
        void emitAndForget(SIGNAL signal);

        /**
         * Configures this emission as a request-reply with the given response type.
         *
         * @param <RESPONSE> the response type
         * @param responseType the expected response type
         * @return a {@link Request} emission builder
         */
        <RESPONSE> Request<SIGNAL, RESPONSE> request(Class<RESPONSE> responseType);

        /**
         * Configures this emission as a request-reply with the given response type.
         *
         * @param <RESPONSE> the response type
         * @param responseType a {@link TypeLiteral} representing the expected response type
         * @return a {@link Request} emission builder
         */
        <RESPONSE> Request<SIGNAL, RESPONSE> request(TypeLiteral<RESPONSE> responseType);

    }

    /**
     * A request-reply emission that delivers the signal to a single matching receiver and returns the response.
     *
     * @param <SIGNAL> the signal type
     * @param <RESPONSE> the response type
     */
    interface Request<SIGNAL, RESPONSE> extends Emission<SIGNAL, RESPONSE, Request<SIGNAL, RESPONSE>> {

    }

    /**
     * A multicast emission that delivers the signal to all matching receivers.
     *
     * @param <SIGNAL> the signal type
     */
    interface Multicast<SIGNAL> extends Emission<SIGNAL, Void, Multicast<SIGNAL>> {

        /**
         * Emits the signal to all matching receivers (fire-and-forget).
         *
         * @param signal the signal object
         */
        void emitAndForget(SIGNAL signal);

    }
}
