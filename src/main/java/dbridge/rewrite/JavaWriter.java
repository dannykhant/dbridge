package dbridge.rewrite;

import soot.Body;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a transformed Soot method body to disk. Jimple is the verified output;
 * a Dava (Java source) decompilation pass can be layered on top.
 */
public class JavaWriter {

    private JavaWriter() {
    }

    /**
     * Write the transformed body as Jimple to {@code targetFile}.
     */
    public static void writeJimple(Body body, Path targetFile) throws IOException {
        Files.write(targetFile, body.toString().getBytes(StandardCharsets.UTF_8));
    }
}
