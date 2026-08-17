package dbridge;

import dbridge.analysis.jdbc.FuncStackAnalyzer;
import dbridge.analysis.jdbc.JdbcDriver;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcDriverTest {

    private static String classPath() {
        String base = System.getProperty("user.dir");
        return base + File.separator + "target" + File.separator + "test-classes"
                + File.pathSeparator + base + File.separator + "target" + File.separator + "classes";
    }

    @Test
    public void testFoldDetection() {
        JdbcDriver driver = new JdbcDriver(classPath());
        FuncStackAnalyzer fsa = driver.analyze("dbridge.test.Example1", "int getTotalPartCount(int)");
        assertTrue(fsa.isSuccess(), "Analysis should succeed");
        assertEquals(1, fsa.getLoopsSwallowed().size(), "Exactly one loop should be swallowed");
    }
}
