package org.maxicp.modeling.xcsp3;

import org.maxicp.ModelDispatcher;
import org.maxicp.modeling.Factory;
import org.maxicp.modeling.algebra.VariableNotFixedException;
import org.maxicp.modeling.algebra.bool.*;
import org.maxicp.modeling.algebra.integer.*;
import org.maxicp.modeling.constraints.*;
import org.maxicp.modeling.symbolic.Objective;
import org.maxicp.search.DFSearch;
import org.maxicp.search.FDSModeling;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.blackbox.BlackBoxSearch;
import org.maxicp.search.blackbox.ModelingBlackBox;
import org.maxicp.util.ImmutableSet;
import org.maxicp.util.exception.NotImplementedException;
import org.maxicp.util.exception.NotYetImplementedException;
import org.xcsp.common.Condition;
import org.xcsp.common.Constants;
import org.xcsp.common.IVar;
import org.xcsp.common.Types;
import org.xcsp.common.predicates.XNode;
import org.xcsp.common.predicates.XNodeLeaf;
import org.xcsp.common.predicates.XNodeParent;
import org.xcsp.common.structures.Transition;
import org.xcsp.parser.callbacks.SolutionChecker;
import org.xcsp.parser.entries.XVariables;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class XCSP3Test {

    public static void main(String[] args) throws Exception {
        String path = args.length > 0
                ? args[0]
                : "data/XCSP3/2024_V3/MiniCOP/BlockModelingMini-kansas-10_c25.xml.lzma"; // LNS useful
        // "data/XCSP3/2024_V3/MiniCOP/RollerSplatMini-07a_c25.xml.lzma";
        // "data/XCSP3/2024_V3/MiniCOP/MetabolicNetwork-mini-49_c25.xml.lzma";
        // "data/XCSP3/2024_V3/MiniCOP/LowAutocorrelation-040-mini_c25.xml.lzma"; //
        // strange objective
        // "data/XCSP3/2024_V3/MiniCOP/LowAutocorrelation-060-mini_c25.xml.lzma"; //
        // strange objective
        // "data/XCSP3/2024_V3/MiniCOP/Fortress1-06-mini_c25.xml.lzma"; // LNS useful
        // "data/XCSP3/2024_V3/MiniCOP/Cutstock-02-0-mini_c25.xml.lzma"; // don't find
        // feasible
        // "data/XCSP3/2024_V3/MiniCOP/Coprime-table-13-mini_c25.xml.lzma"; // out of
        // memory
        // "data/XCSP3/2024_V3/MiniCOP/BusScheduling-r4-mini_c25.xml.lzma";
        // "data/XCSP3/2024_V3/MiniCOP/ChampionsLeagueMini-strict-2024-18_c25.xml.lzma";
        // // LNS useful
        // "data/XCSP3/2024_V3/MiniCOP/BlockModelingMini-karate-3_c25.xml.lzma"; // LNS
        // useful
        // "data/XCSP3/2024_V3/MiniCOP/AlteredStatesMini-15_c25.xml.lzma"; // LNS useful
        // "data/XCSP3/2024_V3/MiniCOP/AlteredStatesMini-bis-15_c25.xml.lzma"; // hard
        // to optimize, good LNS candidate
        // "data/XCSP3/2024_V3/MiniCOP/AlteredStatesMini-bis-02_c25.xml.lzma"; // OK
        // "data/XCSP3/2024_V3/MiniCOP/AlteredStatesMini-02_c25.xml"; // OK
        // "data/XCSP3/2024_V3/MiniCOP/AztecDiamondSym-mini-03_c24.xml"; // hard, even
        // to find a feasible solution
        // "data/XCSP3/2024_V3/MiniCOP/Charlotte-mini-06-0_c24.xml"; // hard to optimize
        // "data/XCSP3/2024_V3/MiniCOP/Clique-brock200-2_c24.xml"; // hard to optimize
        // "data/XCSP3/2024_V3/MiniCOP/Clique-c0125-9_c24.xml"; // hard to optimize
        // "data/XCSP3/2024_V3/MiniCOP/Drinking-mini-000050_c24.xml"; // OK
        // "data/XCSP3/2024_V3/MiniCOP/FoolSolitaire-table-0-2_c24.xml"; // hard to
        // optimize
        // "data/XCSP3/2024_V3/MiniCOP/GolombRuler-a3v18-07_c18.xml"; // OK
        // String path = "data/XCSP3/2024_V3/MiniCOP/LitPuzzle-10_c24.xml"; // OK
        // String path =
        // "data/XCSP3/2024_V3/MiniCOP/MaximumDensityOscillatingLife-mini-5-2_c24.xml";
        // // OK
        // String path = "data/XCSP3/2024_V3/MiniCOP/Pyramid-07-500_c24.xml"; // OK
        // String path = "data/XCSP3/2024_V3/MiniCOP/RotationPuzzle-2_c24.xml"; // OK
        // String path = "data/XCSP3/2024_V3/MiniCOP/SameQueensKnights-mini-05_c24.xml";
        // // OK
        // "data/XCSP3/2024_V3/MiniCOP/StillLife-05-05_c24.xml"; // OK
        // "data/XCSP3/2024_V3/MiniCOP/WordGolf-mini-4-ogd2008-50-0_c24.xml"; // hard to
        // find a feasible solution

        int timeoutInSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 4 * 60;
        XCSP3.runWithBlackBox(path, timeoutInSeconds);
    }

}
