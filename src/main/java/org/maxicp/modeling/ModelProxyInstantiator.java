package org.maxicp.modeling;

import org.maxicp.modeling.concrete.ConcreteModel;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Maintains the current model and proxies calls to it.
 */
public interface ModelProxyInstantiator extends ModelProxy {

    interface ModelInstantiator<T extends ConcreteModel> {
        T instantiate(Model m);
    }

    default <T extends ConcreteModel> T instantiate(ModelInstantiator<T> instantiator) {
        return setModel(instantiator.instantiate(getModel()));
    }

    default <T extends ConcreteModel, R> R runAsConcrete(ModelInstantiator<T> instantiator, Function<T, R> fun) {
        return runAsConcrete(instantiator, getModel(), fun);
    }

    default <T extends ConcreteModel> void runAsConcrete(ModelInstantiator<T> instantiator, Consumer<T> fun) {
        runAsConcrete(instantiator, getModel(), fun);
    }

    default <T extends ConcreteModel, R> R runAsConcrete(ModelInstantiator<T> instantiator, Supplier<R> fun) {
        return runAsConcrete(instantiator, getModel(), fun);
    }

    default <T extends ConcreteModel> void runAsConcrete(ModelInstantiator<T> instantiator, Runnable fun) {
        runAsConcrete(instantiator, getModel(), fun);
    }

    default <T extends ConcreteModel> void runAsConcrete(ModelInstantiator<T> instantiator, Model bm, Runnable fun) {
        T m = instantiator.instantiate(bm);
        runWithModel(m, () -> {
            fun.run();
            return null;
        });
    }

    default <T extends ConcreteModel, R> R runAsConcrete(ModelInstantiator<T> instantiator, Model bm, Function<T, R> fun) {
        T m = instantiator.instantiate(bm);
        return runWithModel(m, () -> fun.apply(m));
    }

    default <T extends ConcreteModel> void runAsConcrete(ModelInstantiator<T> instantiator, Model bm, Consumer<T> fun) {
        T m = instantiator.instantiate(bm);
        runWithModel(m, () -> fun.accept(m));
    }

    default <T extends ConcreteModel, R> R runAsConcrete(ModelInstantiator<T> instantiator, Model bm, Supplier<R> fun) {
        T m = instantiator.instantiate(bm);
        return runWithModel(m, fun);
    }
}
