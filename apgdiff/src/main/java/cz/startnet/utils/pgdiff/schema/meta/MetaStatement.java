package cz.startnet.utils.pgdiff.schema.meta;

import java.io.Serializable;

import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.IStatement;
import cz.startnet.utils.pgdiff.schema.PgObjLocation;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class MetaStatement implements IStatement, Serializable {

    private static final long serialVersionUID = -3372437548966681543L;

    private final PgObjLocation object;
    private String comment = "";

    public MetaStatement(PgObjLocation object) {
        this.object = object;
    }

    public MetaStatement(GenericColumn column) {
        this(new PgObjLocation.Builder().setObject(column).build());
    }

    @Override
    public String getName() {
        return getBareName();
    }

    @Override
    public String getBareName() {
        return object.getObjName();
    }

    @Override
    public DbObjType getStatementType() {
        return object.getType();
    }

    public GenericColumn getGenericColumn() {
        return object.getObj();
    }

    public PgObjLocation getObject() {
        return object;
    }

    @Override
    public MetaStatement getParent() {
        throw new IllegalStateException("Unsupported operation");
    }

    @Override
    public String getQualifiedName() {
        return getGenericColumn().getQualifiedName();
    }

    @Override
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getObjLength() {
        return object.getObjLength();
    }

    public int getOffset() {
        return object.getOffset();
    }

    public String getFilePath() {
        return object.getFilePath();
    }

    public int getLineNumber() {
        return object.getLineNumber();
    }
}
