module api.boot {
    exports io.byteops.boot;

    requires api.modify;
    requires com.google.gson;
    requires java.instrument;
    requires org.objectweb.asm.tree;
}