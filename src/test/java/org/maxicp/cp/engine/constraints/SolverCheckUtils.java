package org.maxicp.cp.engine.constraints;

import be.uclouvain.solvercheck.core.data.Domain;
import be.uclouvain.solvercheck.core.data.PartialAssignment;
import be.uclouvain.solvercheck.core.task.Filter;
import be.uclouvain.solvercheck.core.task.StatefulFilter;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public final class SolverCheckUtils {

        /** A utility class has no public constructor. */
        private SolverCheckUtils() { }

        /**
         * Instanciates a wrapper for some constraint.
         *
         * @param factory a constraint factory used to instantiate the constraint
         *               based on the given solver and variables.
         * @return a filter encapsulating the constraint with the appropriate
         * consistency.
         */
        public static Filter statelessFilter(final ConstraintFactory factory) {
            return partialAssignment -> {
                CPSolver solver = CPFactory.makeSolver();

                CPIntVar[] vars = partial2Vars(solver, partialAssignment);

                try {
                    factory.get(solver, vars).post();
                    solver.fixPoint();
                } catch (InconsistencyException ex) {
                    return PartialAssignment.error(partialAssignment.size());
                }

                return vars2Partial(vars);
            };
        }

        /**
         * Instanciates a wrapper for some constraint.
         *
         * @param factory a constraint factory used to instantiate the constraint
         *               based on the given choco model and variables.s
         * @return a filter encapsulating choco's all different constraint with
         * the appropriate consistency.
         */
        public static StatefulFilter statefulFilter(final ConstraintFactory factory) {
            return new MaxiCPFilterAdapter(factory);
        }

        /**
         * Converts an array of choco IntVar into an equivalent SolverCheck
         * representation (a partial assignment).
         *
         * @param vars the choco int variables
         * @return a solvercheck partial assignment representing the same
         * information as the `vars` array.
         */
        public static PartialAssignment vars2Partial(final CPIntVar[] vars) {
            List<Domain> domains = Arrays.stream(vars)
                    .map(SolverCheckUtils::var2Dom)
                    .collect(Collectors.toList());

            return PartialAssignment.from(domains);
        }
        /**
         * Converts a choco IntVar into an equivalent SolverCheck representation
         * (a domain).
         *
         * @param var the choco int variable
         * @return a solvercheck domain representing the same information as the
         * `vars` array.
         */
        public static Domain var2Dom(final CPIntVar var) {
            return Domain.from(varValues(var));
        }

        /**
         * Returns the set of variables contained in the domain of the given
         * choco variable.
         *
         * @param var the choco variable
         * @return the set of values in the domain of `var`.
         */
        public static Set<Integer> varValues(final CPIntVar var) {
            HashSet<Integer> values = new HashSet<>();

            int[] vals = new int[var.size()];
            var.fillArray(vals);
            Arrays.stream(vals).forEach(values::add);

            return values;
        }

        /**
         * Converts a SolverCheck partial assignment into an equivalent minicp
         * representation.
         *
         * @param solver the solver model managing the intvars to produce.
         * @param partial the partial assignment to translate into choco
         * @return an array of choco int variables representing the same info as
         * `partial`.
         */
        public static CPIntVar[] partial2Vars(
                final CPSolver solver, final PartialAssignment partial) {

            return partial.stream().map(d -> dom2Var(solver, d)).toArray(CPIntVar[]::new);
        }

        /**
         * Converts a SolverCheck domain into an equivalent choco representation
         * (an IntVar).
         *
         * @param solver the solver managing the intvars to produce.
         * @param dom the domain to translate into choco
         * @return a choco int variables representing the same info as `dom`.
         */
        public static CPIntVar dom2Var(final CPSolver solver, final Domain dom) {
            return CPFactory.makeIntVar(solver, dom);
        }

    }
