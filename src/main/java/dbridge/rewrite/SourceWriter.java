package dbridge.rewrite;

import soot.Body;

/** Replaces one method while retaining the original class source verbatim elsewhere. */
public final class SourceWriter {
    private SourceWriter() { }

    public static String replaceMethod(String source, Body body) {
        String method = JavaWriter.toJava(body);
        String name = body.getMethod().getName();
        int declaration = declaration(source, name);
        if (declaration < 0) throw new IllegalArgumentException("method not found: " + name);
        int open = source.indexOf('{', declaration);
        if (open < 0) throw new IllegalArgumentException("method body not found: " + name);
        int close = matchingBrace(source, open);
        int start = source.lastIndexOf('\n', declaration) + 1;
        return source.substring(0, start) + method + source.substring(close + 1);
    }

    private static int declaration(String source, String name) {
        String marker = name + "(";
        for (int at = source.indexOf(marker); at >= 0; at = source.indexOf(marker, at + marker.length())) {
            int line = source.lastIndexOf('\n', at) + 1;
            String prefix = source.substring(line, at);
            if (prefix.matches("(?s).*\\b(public|protected|private|static|final|synchronized)\\b.*")) return at;
        }
        return -1;
    }

    private static int matchingBrace(String source, int open) {
        int depth = 0;
        boolean string = false, character = false, lineComment = false, blockComment = false, escaped = false;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i), next = i + 1 < source.length() ? source.charAt(i + 1) : 0;
            if (lineComment) { if (c == '\n') lineComment = false; continue; }
            if (blockComment) { if (c == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (!string && !character && c == '/' && next == '/') { lineComment = true; i++; continue; }
            if (!string && !character && c == '/' && next == '*') { blockComment = true; i++; continue; }
            if (!character && c == '"' && !escaped) string = !string;
            else if (!string && c == '\'' && !escaped) character = !character;
            else if (!string && !character) {
                if (c == '{') depth++;
                if (c == '}' && --depth == 0) return i;
            }
            escaped = c == '\\' && !escaped;
            if (c != '\\') escaped = false;
        }
        throw new IllegalArgumentException("unclosed method body");
    }
}
