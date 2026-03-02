///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//JAVA_OPTIONS --enable-native-access=ALL-UNNAMED

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/// Proof: call-c-from-java
/// Source: content/language/call-c-from-java.yaml
void main() throws Throwable {

    try (Arena arena = Arena.ofConfined()) {
        // Use a system library to prove FFM compiles and links
        SymbolLookup stdlib = Linker.nativeLinker().defaultLookup();
        Optional<MemorySegment> segment = stdlib.find("strlen");
        MemorySegment foreignFuncAddr = segment.get();
        FunctionDescriptor strlen_sig =
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
        MethodHandle strlenMethod =
            Linker.nativeLinker().downcallHandle(foreignFuncAddr, strlen_sig);
        var ret = (long) strlenMethod.invokeExact(arena.allocateFrom("Bambi"));
        System.out.println("Return value " + ret);
    }
}
