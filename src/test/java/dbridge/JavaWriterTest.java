package dbridge;

import dbridge.analysis.jdbc.FuncStackAnalyzer;
import dbridge.analysis.jdbc.JdbcDriver;
import dbridge.rewrite.BodyRewriter;
import dbridge.rewrite.JavaWriter;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaWriterTest {

    private static String classPath() {
        String base = System.getProperty("user.dir");
        return base + File.separator + "target" + File.separator + "test-classes"
                + File.pathSeparator + base + File.separator + "target" + File.separator + "classes";
    }

    @Test
    public void testJavaOutput() {
        JdbcDriver driver = new JdbcDriver(classPath());
        FuncStackAnalyzer fsa = driver.analyze("dbridge.test.Example1", "int getTotalPartCount(int)");
        assertTrue(fsa.isSuccess());

        BodyRewriter rewriter = new BodyRewriter(fsa.getBody(), fsa.getLoopsSwallowed());
        rewriter.rewriteBody();

        String java = JavaWriter.toJava(fsa.getBody());
        assertTrue(java.contains("while"), "should contain a while loop");
        assertTrue(java.contains("executeBatch"), "should contain executeBatch");
        assertTrue(java.contains("addBatch"), "should contain addBatch");
        assertTrue(java.contains("getMoreResults"), "should contain getMoreResults");
        assertTrue(java.contains("getResultSet"), "should contain getResultSet");
        assertTrue(java.contains("return"), "should contain a return statement");
    }
}
