package io.github.nanoforged.launchredirector.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;


/**
 * Plugin interface for intercepting and modifying the relaunch configuration.
 * The execution Flow is:
 * preProcess ↓
 * process(per plugin by order) ↓
 * postProcess(per plugin by order)
 */
public interface ILaunchConfigPlugin {

    /*
     * called before ctx building, no order
     * for stateful plugin init, execute something without ctx, or tweak order by something etc.
     */
    default void preProcess(Map<String, Integer> plugins) {
        //NOP
    };

    /*
     * receive a copy of ctx, modify it, and be next plugin input
     */
    LaunchContext process(MutableLaunchContext ctx);

    /*
     * called after configuring ctx, receive a copy of final ctx
     * handle co-effect, logging etc.
     */
    default void postProcess(LaunchContext ctx){
        //NOP
    };

    /*
     * Higher priority plugins will be invoked later, this means more control of context.
     */
    default int getPriority() {
        try {
            return this.getClass().getAnnotation(Order.class).value();
        }catch(Exception e) {
            return 100;
        }
    }


    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Order {
        int value();
    }
}
