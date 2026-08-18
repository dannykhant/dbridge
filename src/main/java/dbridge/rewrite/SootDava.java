package dbridge.rewrite;

import soot.Body;

/** Source-emission facade retained for the paper pipeline's Decompile step. */
public final class SootDava {
    private SootDava() { }

    public static String emit(String originalSource, Body transformedBody) {
        return SourceWriter.replaceMethod(originalSource, transformedBody);
    }
}
