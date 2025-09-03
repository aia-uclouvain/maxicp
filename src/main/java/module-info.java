module org.maxicp {
    exports org.maxicp;
    exports org.maxicp.cp;
    exports org.maxicp.cp.modeling;
    exports org.maxicp.cp.engine.constraints;
    exports org.maxicp.cp.engine.constraints.scheduling;
    exports org.maxicp.cp.engine.constraints.seqvar;
    exports org.maxicp.cp.engine.constraints.setvar;
    exports org.maxicp.cp.engine.core;
    exports org.maxicp.modeling;
    exports org.maxicp.search;
    exports org.maxicp.state;
    exports org.maxicp.util;

               // Add this line
    exports org.maxicp.modeling.algebra.bool;           // Add this line
    exports org.maxicp.util.exception;


    requires org.json;
    requires xcsp3.tools;
    requires java.xml;
    requires java.compiler;
}