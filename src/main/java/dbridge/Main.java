package dbridge;

import dbridge.analysis.jdbc.FuncStackAnalyzer;
import dbridge.analysis.jdbc.JdbcDriver;
import dbridge.rewrite.SootDava;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Command-line entry point for DBridge.
 *
 * Usage:
 *   java dbridge.Main &lt;classpath&gt; &lt;className&gt; &lt;methodSubsignature&gt; &lt;outputFile&gt; [sourceFile]
 *
 * Example:
 *   java dbridge.Main target/classes dbridge.test.Example1 "int getTotalPartCount(int)" out/Example1.java
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length != 4 && args.length != 5) {
            System.err.println("Usage: java dbridge.Main <classpath> <className> <methodSubsignature> <outputFile> [sourceFile]");
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

        if (!driver.rewrite(fsa)) {
            System.err.println("Rewrite failed: expression is not SQL translatable.");
            System.exit(3);
        }

        String java = args.length == 5
                ? SootDava.emit(new String(Files.readAllBytes(Paths.get(args[4])), StandardCharsets.UTF_8), fsa.getBody())
                : dbridge.rewrite.JavaWriter.toJava(fsa.getBody());
        Files.write(Paths.get(outputFile), java.getBytes(StandardCharsets.UTF_8));
        System.out.println("Transformed Java written to " + outputFile);
        System.out.println(java);
    }
}
