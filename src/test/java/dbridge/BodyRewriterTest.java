package dbridge;

import dbridge.analysis.jdbc.FuncStackAnalyzer;
import dbridge.analysis.jdbc.JdbcDriver;
import dbridge.rewrite.BodyRewriter;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BodyRewriterTest {

    private static String classPath() {
        String base = System.getProperty("user.dir");
        return base + File.separator + "target" + File.separator + "test-classes"
                + File.pathSeparator + base + File.separator + "target" + File.separator + "classes";
    }

    @Test
    public void testRewrite() {
        JdbcDriver driver = new JdbcDriver(classPath());
        FuncStackAnalyzer fsa = driver.analyze("dbridge.test.Example1", "int getTotalPartCount(int)");
        assertTrue(fsa.isSuccess());

        BodyRewriter rewriter = new BodyRewriter(fsa.getBody(), fsa.getLoopsSwallowed());
        rewriter.rewriteBody();

        String result = fsa.getBody().toString();
        assertTrue(result.contains("addBatch"), "should contain addBatch");
        assertTrue(result.contains("executeBatch"), "should contain executeBatch");
        assertTrue(result.contains("getMoreResults"), "should contain getMoreResults");
        assertTrue(result.contains("getResultSet"), "should contain getResultSet");
        assertTrue(result.contains("DBridgeConnection"), "should route connection through runtime");
    }
}
