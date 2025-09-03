module org.maxicp {
    exports org.maxicp;
    exports org.maxicp.cp;
    exports org.maxicp.modeling;
    exports org.maxicp.search;
    exports org.maxicp.state;
    exports org.maxicp.util;
    requires org.json;
    requires xcsp3.tools;
    requires java.xml;
    requires java.compiler;
}