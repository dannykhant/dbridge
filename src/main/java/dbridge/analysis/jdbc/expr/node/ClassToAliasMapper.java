package dbridge.analysis.jdbc.expr.node;

import java.util.HashMap;
import java.util.Map;

/** Maps entity class names to the aliases used by generated SQL. */
public final class ClassToAliasMapper {
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        ALIASES.put("Project", "p");
        ALIASES.put("Participant", "pt");
        ALIASES.put("Role", "r");
        ALIASES.put("Order", "o");
        ALIASES.put("DateDim", "d");
    }

    private ClassToAliasMapper() {
    }

    public static String getAlias(String className) {
        return ALIASES.containsKey(className) ? ALIASES.get(className) : "x";
    }
}
