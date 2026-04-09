package io.quarkiverse.signals;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Identifies the signal parameter of a receiver method declared on a CDI bean.
 *
 * <p>
 * A receiver method is a non-private non-static method that has exactly one parameter annotated with {@code @Receives}. The
 * type of the annotated parameter determines the signal type. Qualifiers declared on the annotated parameter are used during
 * type-safe resolution.
 *
 * <pre>
 * &#064;Singleton
 * public class OrderHandlers {
 *
 *     void onOrderPlaced(&#064;Receives OrderPlaced order) {
 *         // handle the signal
 *     }
 *
 *     Uni&lt;String&gt; onUrgentOrder(&#064;Receives &#064;Urgent OrderPlaced order) {
 *         // handle and return a response
 *         return Uni.createFrom().item(order.id());
 *     }
 * }
 * </pre>
 *
 * <p>
 * The annotated parameter may also be of type {@link Receiver.SignalContext}, in which case the receiver has access to
 * the full signal context including metadata, qualifiers, and emission type:
 *
 * <pre>
 * void onOrder(&#064;Receives SignalContext&lt;OrderPlaced&gt; ctx) {
 *     OrderPlaced order = ctx.signal();
 *     Map&lt;String, Object&gt; meta = ctx.meta();
 * }
 * </pre>
 *
 * @see Signal
 * @see Receiver
 */
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Receives {

}
