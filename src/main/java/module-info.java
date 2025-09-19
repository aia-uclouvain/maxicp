module org.maxicp {
    exports org.maxicp;
    exports org.maxicp.util;
    exports org.maxicp.util.io;
    exports org.maxicp.util.algo;
    exports org.maxicp.util.exception;
    exports org.maxicp.state;
    exports org.maxicp.state.trail;
    exports org.maxicp.state.datastructures;
    exports org.maxicp.state.copy;
    exports org.maxicp.modeling;
    exports org.maxicp.modeling.constraints;
    exports org.maxicp.modeling.constraints.scheduling;
    exports org.maxicp.modeling.constraints.helpers;
    exports org.maxicp.modeling.constraints.seqvar;
    exports org.maxicp.modeling.concrete;
    exports org.maxicp.modeling.utils;
    exports org.maxicp.modeling.xcsp3;
    exports org.maxicp.modeling.symbolic;
    exports org.maxicp.modeling.algebra;
    exports org.maxicp.modeling.algebra.scheduling;
    exports org.maxicp.modeling.algebra.bool;
    exports org.maxicp.modeling.algebra.integer;
    exports org.maxicp.modeling.algebra.sequence;
    exports org.maxicp.search;
    exports org.maxicp.cp;
    exports org.maxicp.cp.modeling;
    exports org.maxicp.cp.engine.core;
    exports org.maxicp.cp.engine.constraints;
    exports org.maxicp.cp.engine.constraints.scheduling;
    exports org.maxicp.cp.engine.constraints.setvar;
    exports org.maxicp.cp.engine.constraints.seqvar;

    requires org.json;
    requires xcsp3.tools;
    requires java.xml;
    requires java.compiler;
}