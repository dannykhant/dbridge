package dbridge;

import dbridge.analysis.jdbc.FuncStackAnalyzer;
import dbridge.analysis.jdbc.JdbcDriver;
import dbridge.rewrite.BodyRewriter;
import dbridge.rewrite.JavaWriter;

import java.nio.file.Paths;

/**
 * Command-line entry point for DBridge.
 *
 * Usage:
 *   java dbridge.Main &lt;classpath&gt; &lt;className&gt; &lt;methodSubsignature&gt; &lt;outputFile&gt;
 *
 * Example:
 *   java dbridge.Main target/classes dbridge.test.Example1 "int getTotalPartCount(int)" out/Example1.jimple
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: java dbridge.Main <classpath> <className> <methodSubsignature> <outputFile>");
            System.exit(1);
        }
        String classPath = args[0];
        String className = args[1];
        String methodSubsignature = args[2];
        String outputFile = args[3];

        JdbcDriver driver = new JdbcDriver(classPath);
        FuncStackAnalyzer fsa = driver.analyze(className, methodSubsignature);
        if (!fsa.isSuccess()) {
            System.err.println("Analysis failed: no foldable JDBC loop found (or preconditions not met).");
            System.exit(2);
        }

        System.out.println("Swallowed loops: " + fsa.getLoopsSwallowed().size());

        BodyRewriter rewriter = new BodyRewriter(fsa.getBody(), fsa.getLoopsSwallowed());
        rewriter.rewriteBody();

        JavaWriter.writeJimple(fsa.getBody(), Paths.get(outputFile));
        System.out.println("Transformed body written to " + outputFile);
    }
}
