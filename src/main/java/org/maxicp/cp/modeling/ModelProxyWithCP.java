package org.maxicp.cp.modeling;

import org.maxicp.modeling.Model;
import org.maxicp.modeling.ModelProxyInstantiator;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ModelProxyWithCP extends ModelProxyInstantiator {
    default ConcreteCPModel cpInstantiate() { return instantiate(CPModelInstantiator.withTrailing); }
    default <R> R runCP(Function<ConcreteCPModel, R> fun) { return runAsConcrete(CPModelInstantiator.withTrailing, fun); }
    default void runCP(Consumer<ConcreteCPModel> fun) { runAsConcrete(CPModelInstantiator.withTrailing, fun); }
    default <R> R runCP(Supplier<R> fun) { return runAsConcrete(CPModelInstantiator.withTrailing, fun); }
    default void runCP(Runnable fun) { runAsConcrete(CPModelInstantiator.withTrailing, fun); }
    default void runCP(Model bm, Runnable fun) { runAsConcrete(CPModelInstantiator.withTrailing, bm, fun); }
    default <R> R runCP(Model bm, Function<ConcreteCPModel, R> fun) { return runAsConcrete(CPModelInstantiator.withTrailing, bm, fun); }
    default void runCP(Model bm, Consumer<ConcreteCPModel> fun) { runAsConcrete(CPModelInstantiator.withTrailing, bm, fun); }
    default <R> R runCP(Model bm, Supplier<R> fun) { return runAsConcrete(CPModelInstantiator.withTrailing, bm, fun); }
}
