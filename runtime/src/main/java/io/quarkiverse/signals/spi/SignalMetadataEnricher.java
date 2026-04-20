package io.quarkiverse.signals.spi;

import io.quarkiverse.signals.Receiver.SignalContext;
import io.quarkiverse.signals.Signal;

/**
 * Enriches the metadata of a signal emission before any receiver is invoked.
 * <p>
 * Implementations must be CDI beans annotated with {@link io.smallrye.common.annotation.Identifier} to define a unique
 * identifier. The ordering of enrichers can be defined with {@link ComponentOrder}.
 * <p>
 * This SPI is called once per emission (i.e., per call to {@link Signal#publish(Object)}, {@link Signal#send(Object)}, or
 * {@link Signal#request(Object, Class)}), not per receiver.
 *
 * @see ComponentOrder
 */
public interface SignalMetadataEnricher {

    /**
     * Enriches the metadata of a signal emission.
     *
     * @param context the enrichment context
     */
    void enrich(EnrichmentContext context);

    /**
     * Provides contextual information about the signal emission being enriched.
     */
    interface EnrichmentContext {

        /**
         * @return the signal context
         */
        SignalContext<?> signalContext();

        /**
         * Puts a metadata entry. If an entry with the same key already exists, it is replaced.
         *
         * @param key the metadata key
         * @param value the metadata value
         */
        void putMetadata(String key, Object value);

    }

}
