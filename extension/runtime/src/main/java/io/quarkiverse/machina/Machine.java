package io.quarkiverse.machina;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 * {
 *     &#64;code
 *     &#64;Workflow
 *     public interface MyWorkflow {
 *         MyOutput run(@Signal("input") String one, MyInput two);
 *
 *         @OutputClasses({ MyOutput.class })
 *         &#64;OutputSignals({ "signal1", "signal2" })
 *         ProcessorResult run2();
 *
 *     }
 * }
 * </pre>
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Machine {
}
