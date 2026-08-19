package dbridge.analysis.jdbc.expr.node;

/** A one-row relation used to evaluate constant expressions. */
public class ConstTableNode extends ClassRefNode {
    public ConstTableNode() {
        super("ConstTable");
    }
}
