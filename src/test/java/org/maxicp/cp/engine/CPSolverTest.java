/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.maxicp.cp.engine;


import org.junit.jupiter.params.provider.Arguments;
import org.maxicp.cp.engine.core.MaxiCP;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.state.copy.Copier;
import org.maxicp.state.trail.Trailer;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public abstract class CPSolverTest {

    public static Stream<CPSolver> getSolver() {
        return Stream.of(new MaxiCP(new Trailer()), new MaxiCP(new Copier()));
    }

    public static Stream<Arguments> solverSupplier() {
        return Stream.of(
                arguments(named(
                        new MaxiCP(new Trailer()).toString(),
                        (Supplier<CPSolver>) () -> new MaxiCP(new Trailer()))),
                arguments(named(
                        new MaxiCP(new Copier()).toString(),
                        (Supplier<CPSolver>) () -> new MaxiCP(new Copier()))));
    }

    /**
     * Gives a repeated set of suppliers of solvers
     * Each solver supplier is repeated nRepeat times. Hence, the length of the stream might be longer than nRepeat
     *
     * @param nRepeat number of occurrence for each {@link CPSolver}
     * @return stream of size nRepeat * number of solvers. Each element contains one supplier of a {@link CPSolver}
     */
    public static Stream<Supplier<CPSolver>> getRepeatedSolverSuppliers(int nRepeat) {
        Stream<Supplier<CPSolver>> trailerStream = Stream.generate((Supplier<Supplier<CPSolver>>) () -> () -> new MaxiCP(new Trailer())).limit(nRepeat);
        Stream<Supplier<CPSolver>> copyStream = Stream.generate((Supplier<Supplier<CPSolver>>) () -> () -> new MaxiCP(new Copier())).limit(nRepeat);
        return Stream.concat(trailerStream, copyStream);
    }
}
