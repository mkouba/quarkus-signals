package io.quarkiverse.signals;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

import io.smallrye.common.annotation.CheckReturnValue;
import io.smallrye.mutiny.Uni;

/**
 * A receiver handles signals of a particular type. Receivers are typically declared as methods annotated with
 * {@link Receives} on a CDI bean, and discovered at build time. They can also be registered programmatically
 * via {@link Receivers}.
 *
 * <p>
 * A receiver method example:
 *
 * <pre>
 * void onOrderPlaced(&#064;Receives OrderPlaced order) {
 *     // handle the signal
 * }
 * </pre>
 *
 * <p>
 * The receiver method may also return a value, which is used as the response for
 * {@linkplain Signal#request(Object, Class) request-reply} emissions:
 *
 * <pre>
 * String onOrderPlaced(&#064;Receives OrderPlaced order) {
 *     return order.id();
 * }
 * </pre>
 *
 * @param <SIGNAL> the type of the signal object
 * @param <RESPONSE> the type of the response, or {@link Void} for fire-and-forget receivers
 * @see Receives
 * @see Signal
 * @see Receivers
 */
public interface Receiver<SIGNAL, RESPONSE> {

    /**
     * @return the type of signal this receiver handles
     */
    Type signalType();

    /**
     * The qualifiers are used during type-safe resolution together with the {@linkplain #signalType() signal type}.
     *
     * @return the set of qualifiers, never {@code null}
     */
    Set<Annotation> qualifiers();

    /**
     * The response type is used during type-safe resolution for request emissions. Only receivers whose
     * response type is assignable to the requested type are considered.
     *
     * @return the response type, or {@code null} for fire-and-forget receivers
     * @see Signal#request(Object, Class)
     */
    Type responseType();

    /**
     * Determines how the receiver is executed.
     *
     * @return the execution model
     * @see ExecutionModel
     */
    ExecutionModel executionModel();

    /**
     * Invoked when a matching signal is emitted. The {@link SignalContext} provides access to the signal object,
     * metadata, qualifiers, and emission type.
     *
     * @param context the signal context
     * @return a {@link Uni} that completes with the response, or with {@code null} for fire-and-forget receivers
     */
    @CheckReturnValue
    Uni<RESPONSE> notify(SignalContext<SIGNAL> context);

    /**
     * Provides contextual information about an emitted signal, passed to {@link Receiver#notify(SignalContext)}.
     *
     * @param <T> the signal type
     */
    interface SignalContext<T> {

        /**
         * @return the metadata attached to the emission, never {@code null}
         * @see Signal#putMetadata(String, Object)
         * @see Signal#setMetadata(Map)
         */
        Map<String, Object> metadata();

        /**
         * @return the signal object
         */
        T signal();

        /**
         * @return the type of the signal object
         */
        Type signalType();

        /**
         * @return the expected response type, or {@code null} if the emission is not a request-reply
         * @see EmissionType#REQUEST
         */
        Type responseType();

        /**
         * @return the qualifiers specified at the emission point
         */
        Set<Annotation> qualifiers();

        /**
         * @return the type of the emission
         */
        EmissionType emissionType();

    }

    /**
     * Determines the threading model used to execute a receiver.
     */
    enum ExecutionModel {

        /**
         * The receiver is executed on a worker thread. This is the default for receiver methods with a blocking
         * signature (i.e., not returning {@link Uni}).
         */
        WORKER_THREAD,

        /**
         * The receiver is executed on a virtual thread.
         */
        VIRTUAL_THREAD,

        /**
         * The receiver is executed on the Vert.x event loop. This is the default for receiver methods returning
         * {@link Uni}.
         */
        EVENT_LOOP

    }

    /**
     * The type of emission that triggered the receiver.
     *
     * @see Signal#publishAndForget(Object)
     * @see Signal#sendAndForget(Object)
     * @see Signal#request(Object, Class)
     */
    enum EmissionType {

        /**
         * The signal was emitted via {@link Signal#publish(Object)} (multicast).
         */
        PUBLISH,

        /**
         * The signal was emitted via {@link Signal#request(Object, Class)} (unicast, request-reply).
         */
        REQUEST,

        /**
         * The signal was emitted via {@link Signal#send(Object)} (unicast, fire-and-forget).
         */
        SEND
    }

}
