package dbridge.analysis.jdbc.expr.node;

import dbridge.analysis.jdbc.expr.OpType;
import dbridge.analysis.jdbc.expr.exceptions.QueryTranslationException;

/** A column reference, retaining the declaring and type class payloads. */
public class FieldRefNode extends Node implements SQLTranslatable {
    private final String baseClass;
    private final String fieldName;
    private final String typeClass;

    public FieldRefNode(String baseClass, String fieldName, String typeClass) {
        super(OpType.FieldRef);
        this.baseClass = baseClass;
        this.fieldName = fieldName;
        this.typeClass = typeClass;
    }

    public String getBaseClass() { return baseClass; }
    public String getFieldName() { return fieldName; }
    public String getTypeClass() { return typeClass; }

    public ClassRefNode getTypeClassRef() {
        return new ClassRefNode(typeClass);
    }

    @Override
    public String toSQLQuery() throws QueryTranslationException {
        if (baseClass == null || fieldName == null) {
            throw new QueryTranslationException("FieldRef has no class or field name");
        }
        return ClassToAliasMapper.getAlias(baseClass).toLowerCase() + "." + fieldName.toLowerCase();
    }
}
