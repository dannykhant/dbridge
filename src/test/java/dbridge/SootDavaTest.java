package dbridge;

import dbridge.analysis.jdbc.FuncStackAnalyzer;
import dbridge.analysis.jdbc.JdbcDriver;
import dbridge.rewrite.BodyRewriter;
import dbridge.rewrite.SootDava;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SootDavaTest {

    private static String classPath() {
        String base = System.getProperty("user.dir");
        return base + File.separator + "target" + File.separator + "test-classes"
                + File.pathSeparator + base + File.separator + "target" + File.separator + "classes";
    }

    @Test
    public void testSourceOutput() throws Exception {
        JdbcDriver driver = new JdbcDriver(classPath());
        FuncStackAnalyzer fsa = driver.analyze("dbridge.test.Example1", "int getTotalPartCount(int)");
        assertTrue(fsa.isSuccess());

        BodyRewriter rewriter = new BodyRewriter(fsa.getBody(), fsa.getLoopsSwallowed());
        rewriter.rewriteBody();

        String java = SootDava.emit(new String(Files.readAllBytes(Paths.get("src/test/java/dbridge/test/Example1.java")), StandardCharsets.UTF_8), fsa.getBody());
        assertTrue(java.contains("class Example1"));
        assertTrue(java.contains("while"));
        assertTrue(java.contains("executeBatch"));
        assertTrue(java.contains("addBatch"));
        assertTrue(java.contains("getResultSet"));
        assertTrue(java.contains("return"));
    }
}
