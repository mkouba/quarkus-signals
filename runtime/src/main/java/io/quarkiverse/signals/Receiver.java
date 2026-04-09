package io.quarkiverse.signals;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

import io.smallrye.mutiny.Uni;

/**
 * 
 * @param <T> the type of the signal object
 */
public interface Receiver<SIGNAL, RESPONSE> {

    /**
     * @param context
     * @return the uni
     */
    Uni<RESPONSE> notify(SignalContext<SIGNAL> context);

    /**
     * @return the execution model
     */
    ExecutionModel executionModel();

    enum ExecutionModel {

        WORKER_THREAD,
        VIRTUAL_THREAD,
        EVENT_LOOP

    }

    public interface SignalContext<T> {

        /**
         * 
         * @return the metadata
         */
        Map<String, Object> meta();

        /**
         * 
         * @return the signal object
         */
        T signal();

        /**
         * 
         * @return the type of the signal object
         */
        Type signalType();

        /**
         * 
         * @return the type of the response object or {@code null}
         * @see EmissionType#REQUEST
         */
        Type responseType();

        /**
         * 
         * @return the qualifiers
         */
        Set<Annotation> qualifiers();

        /**
         * @return the type of the emission
         */
        EmissionType emissionType();

    }

    enum EmissionType {
        PUBLISH,
        REQUEST,
        SEND
    }
}
