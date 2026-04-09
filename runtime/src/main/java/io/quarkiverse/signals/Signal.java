package io.quarkiverse.signals;

import java.lang.annotation.Annotation;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.util.TypeLiteral;

/**
 * 
 * @param <T> the type of the signal object
 */
public interface Signal<T> {

    /**
     * @param qualifiers
     * @return the child signal
     */
    Signal<T> select(Annotation... qualifiers);

    /**
     * Sends a signal to all receivers matching the specified signal type and qualifiers.
     * <p>
     * Receivers are notified asynchronously. This does not block the current thread.
     * 
     * @param signal
     * @see Receives
     */
    default void publish(T signal) {
        multicast().send(signal);
    }

    /**
     * Sends a signal to a single receiver matching the specified signal type and qualifiers.
     * 
     * @param notification
     * @return the response
     * @see Receives
     */
    default <R> Uni<R> request(T notification, Class<R> responseType) {
        return unicast().request(responseType).send(notification);
    }

    /**
     * Sends a signal to a single receiver matching the specified signal type and qualifiers.
     * 
     * @param signal
     * @return the response
     * @see Receives
     */
    default <R> Uni<R> request(T signal, TypeLiteral<R> responseType) {
        return unicast().request(responseType).send(signal);
    }

    /**
     * Sends a signal to a single receiver matching the specified signal type and qualifiers.
     * 
     * @param notification
     * @return the response
     * @see Receives
     */
    default void send(T signal) {
        unicast().send(signal);
    }

    /**
     * Sends a signal to a single receiver.
     * 
     * @return unicast configuration
     */
    Unicast<T> unicast();

    /**
     * Sends a signal to a matching group of receivers.
     * 
     * @return multicast configuration
     */
    Multicast<T> multicast();

    interface Emission<SIGNAL, R, E extends Emission<SIGNAL, R, E>> {

        Uni<R> emit(SIGNAL signal);

        E withMeta(String key, Object value);

    }

    interface Unicast<SIGNAL> extends Emission<SIGNAL, Void, Unicast<SIGNAL>> {

        default void send(SIGNAL signal) {
            emit(signal).subscribe().with(v -> {
            });
        }

        <RESPONSE> Request<SIGNAL, RESPONSE> request(Class<RESPONSE> responseType);

        <RESPONSE> Request<SIGNAL, RESPONSE> request(TypeLiteral<RESPONSE> responseType);

    }

    interface Request<SIGNAL, RESPONSE> extends Emission<SIGNAL, RESPONSE, Request<SIGNAL, RESPONSE>> {

        default Uni<RESPONSE> send(SIGNAL signal) {
            return emit(signal);
        }

    }

    interface Multicast<SIGNAL> extends Emission<SIGNAL, Void, Multicast<SIGNAL>> {

        default void send(SIGNAL signal) {
            emit(signal).subscribe().with(v -> {
            });
        }

    }
}
