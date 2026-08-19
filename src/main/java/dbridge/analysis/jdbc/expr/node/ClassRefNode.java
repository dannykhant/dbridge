package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;
import soot.jimple.ClassConstant;

/** A mapped entity/table reference. */
public class ClassRefNode extends Node implements SQLTranslatable {
    private final String className;

    public ClassRefNode(String className) {
        super(OpType.ClassRef);
        this.className = className;
    }

    public ClassRefNode(ClassConstant classConstant) {
        this(className(classConstant));
    }

    private static String className(ClassConstant classConstant) {
        String value = classConstant.getValue();
        int slash = value.lastIndexOf('/');
        return slash < 0 ? value : value.substring(slash + 1);
    }

    public String getClassName() {
        return className;
    }

    public String getAlias() {
        return ClassToAliasMapper.getAlias(className);
    }

    @Override
    public String toSQLQuery() throws QueryTranslationException {
        if (className == null || className.isEmpty()) {
            throw new QueryTranslationException("ClassRef has no class name");
        }
        return className.toLowerCase();
    }
}
