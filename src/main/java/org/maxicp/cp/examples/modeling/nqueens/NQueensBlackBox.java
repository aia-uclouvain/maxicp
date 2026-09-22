package org.maxicp.cp.examples.modeling.nqueens;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.IntVar;
import org.maxicp.modeling.algebra.integer.IntExpression;
import org.maxicp.search.blackbox.ModelingBlackBox;

import java.util.List;

public class NQueensBlackBox {

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        int timeoutInSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 4 * 60;
        try (ModelDispatcher model = Factory.makeModelDispatcher()) {
            IntVar[] q = model.intVarArray(n, n);
            IntExpression[] diagL = model.intVarArray(n, i -> q[i].plus(i));
            IntExpression[] diagR = model.intVarArray(n, i -> q[i].minus(i));

            model.add(Factory.allDifferent(q));
            model.add(Factory.allDifferent(diagL));
            model.add(Factory.allDifferent(diagR));

            ModelingBlackBox.Result result = ModelingBlackBox.solve(model, q, timeoutInSeconds);
            System.out.println("status=" + result.status());
            if (result.solution().isPresent()) {
                List<Integer> solution = result.solution().get();
                System.out.println("solution=" + solution);
            }
        }
    }
}

