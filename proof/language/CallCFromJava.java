///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//JAVA_OPTIONS --enable-native-access=ALL-UNNAMED
import java.lang.foreign.*;

/// Proof: call-c-from-java
/// Source: content/language/call-c-from-java.yaml
void main() throws Throwable {
    try (var arena = Arena.ofConfined()) {
        var stdlib = Linker.nativeLinker().defaultLookup();
        var foreignFuncAddr = stdlib.find("strlen").orElseThrow();
        var strlenSig =
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
        var strlenMethod =
            Linker.nativeLinker().downcallHandle(foreignFuncAddr, strlenSig);
        var ret = (long) strlenMethod.invokeExact(arena.allocateFrom("Bambi"));
        assert ret == 5 : "Expected strlen(\"Bambi\") == 5, got " + ret;
    }
}
