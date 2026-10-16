package io.github.nanoforged.launchredirector.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/**
 * Plugin interface for intercepting and modifying the relaunch configuration. Each relaunch runs
 * {@code preProcess} (all plugins), then {@code process} (in order, each receiving the previous
 * plugin's result), then {@code postProcess} (all plugins). A plugin failure is contained by the
 * caller and never aborts the relaunch, so a plugin must not rely on its own failure to stop the
 * launch.
 */
public interface ILaunchConfigPlugin {

    /** Order used by plugins without an {@link Order} annotation. */
    int DEFAULT_PRIORITY = 100;

    /**
     * Runs before the context exists, for stateful initialization or for inspecting which other
     * plugins are present.
     *
     * @param plugins unmodifiable {@code plugin class name -> resolved priority} registry; the chain
     *                order is already fixed, so no entry here can change when a plugin executes
     */
    default void preProcess(Map<String, Integer> plugins) {
    }

    /**
     * Adjusts the mutable copy of the previous plugin's context.
     *
     * @param ctx a mutable copy owned by this call; mutations are not visible to other plugins
     * @return the context for the next plugin, never {@code null}
     */
    LaunchContext process(MutableLaunchContext ctx);

    /**
     * Runs after the whole chain, with the final immutable context. An observation point, not a
     * modification point.
     */
    default void postProcess(LaunchContext ctx) {
    }

    /**
     * The resolved execution order: plugins sort ascending, so a higher value runs later and sees
     * more context. Reads {@link Order#value()}, falling back to {@link #DEFAULT_PRIORITY}; ties are
     * broken by class name by the caller so the order is reproducible across runs.
     */
    default int getPriority() {
        Order order = this.getClass().getAnnotation(Order.class);
        return order == null ? DEFAULT_PRIORITY : order.value();
    }

    /** Declares an execution order. Higher values run later. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Order {
        int value();
    }
}
