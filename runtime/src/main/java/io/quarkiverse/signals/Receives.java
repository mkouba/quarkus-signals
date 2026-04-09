package io.quarkiverse.signals;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Identifies the signal parameter of a receiver method.
 * 
 * @see Signal
 */
@Retention(RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Receives {

}
