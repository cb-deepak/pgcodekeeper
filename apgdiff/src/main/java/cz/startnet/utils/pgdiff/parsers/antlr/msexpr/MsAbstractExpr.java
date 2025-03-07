package cz.startnet.utils.pgdiff.parsers.antlr.msexpr;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;

import cz.startnet.utils.pgdiff.parsers.antlr.TSQLParser.Data_typeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.TSQLParser.Full_column_nameContext;
import cz.startnet.utils.pgdiff.parsers.antlr.TSQLParser.IdContext;
import cz.startnet.utils.pgdiff.parsers.antlr.TSQLParser.Qualified_nameContext;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.PgObjLocation;
import cz.startnet.utils.pgdiff.schema.PgObjLocation.LocationType;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffUtils;
import ru.taximaxim.codekeeper.apgdiff.log.Log;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public abstract class MsAbstractExpr {

    private final String schema;
    private final MsAbstractExpr parent;
    private final Set<PgObjLocation> depcies;

    public Set<PgObjLocation> getDepcies() {
        return Collections.unmodifiableSet(depcies);
    }

    public MsAbstractExpr(String schema) {
        this(schema, null, new LinkedHashSet<>());
    }

    protected MsAbstractExpr(MsAbstractExpr parent) {
        this(parent.schema, parent, parent.depcies);
    }

    private MsAbstractExpr(String schema, MsAbstractExpr parent, Set<PgObjLocation> depcies) {
        this.schema = schema;
        this.parent = parent;
        this.depcies = depcies;
    }

    protected MsAbstractExprWithNmspc<?> findCte(String cteName) {
        return parent == null ? null : parent.findCte(cteName);
    }

    protected boolean hasCte(String cteName) {
        return findCte(cteName) != null;
    }

    /**
     * @param schema optional schema qualification of name, may be null
     * @param name alias of the referenced object, lower-case for case-insensitive search
     *             call {@link #findReference(String, String)} to lower-case automatically
     * @param column optional referenced column alias, may be null
     * @return a pair of (Alias, Dealiased name) where Alias is the given name.
     *          Dealiased name can be null if the name is internal to the query
     *          and is not a reference to external table.<br>
     *          null if the name is not found
     */
    protected Entry<String, GenericColumn> findReferenceRecursive(String schema, String name) {
        return parent == null ? null : parent.findReferenceRecursive(schema, name);
    }

    protected final Entry<String, GenericColumn> findReference(String schema, String name) {
        return findReferenceRecursive(schema, name.toLowerCase(Locale.ROOT));
    }

    protected GenericColumn addObjectDepcy(Qualified_nameContext qualifiedName, DbObjType type) {
        IdContext nameCtx = qualifiedName.name;
        String relationName = nameCtx.getText();
        IdContext schemaCtx = qualifiedName.schema;
        String schemaName;
        if (schemaCtx == null) {
            schemaName = schema;
        }  else {
            schemaName = schemaCtx.getText();
            addDepcy(new GenericColumn(schemaName, DbObjType.SCHEMA), schemaCtx);
        }

        GenericColumn depcy = new GenericColumn(schemaName, relationName, type);
        addDepcy(depcy, nameCtx);
        return depcy;
    }

    protected void addTypeDepcy(Data_typeContext dt) {
        Qualified_nameContext name = dt.qualified_name();
        if (name != null && name.schema != null
                && !ApgdiffUtils.isMsSystemSchema(name.schema.getText())) {
            addObjectDepcy(name, DbObjType.TYPE);
        }
    }

    protected void addDepcy(GenericColumn depcy, ParserRuleContext ctx) {
        if (!ApgdiffUtils.isMsSystemSchema(depcy.schema)) {
            PgObjLocation dep = new PgObjLocation.Builder()
                    .setObject(depcy)
                    .setCtx(ctx)
                    .build();
            depcies.add(dep);
        }
    }

    protected void addAliasReference(GenericColumn depcy, ParserRuleContext ctx) {
        depcies.add(new PgObjLocation.Builder()
                .setObject(depcy)
                .setCtx(ctx)
                .setLocationType(LocationType.LOCAL_REF)
                .setAlias(ctx.getText())
                .build());
    }

    protected void addVariable(GenericColumn depcy, ParserRuleContext ctx) {
        depcies.add(new PgObjLocation.Builder()
                .setObject(depcy)
                .setCtx(ctx)
                .setLocationType(LocationType.VARIABLE)
                .setAlias(ctx.getText())
                .build());
    }

    protected void addColumnDepcy(Full_column_nameContext fcn) {
        Qualified_nameContext tableName = fcn.qualified_name();
        if (tableName == null) {
            return;
        }

        IdContext schemaCtx = tableName.schema;

        String schemaName = null;
        if (schemaCtx != null) {
            schemaName = schemaCtx.getText();
            addDepcy(new GenericColumn(schemaName, DbObjType.SCHEMA), schemaCtx);
        }

        IdContext relationCtx = tableName.name;
        String relationName = relationCtx.getText();

        IdContext columnCtx = fcn.id();
        String columnName = columnCtx.getText();

        Entry<String, GenericColumn> ref = findReference(schemaName, relationName);
        if (ref != null) {
            GenericColumn table = ref.getValue();
            if (table != null) {
                if (relationName.equals(table.table)) {
                    addDepcy(table, relationCtx);
                } else {
                    addAliasReference(table, relationCtx);
                }

                addDepcy(new GenericColumn(table.schema, table.table,
                        columnName, DbObjType.COLUMN), columnCtx);
            }
        } else {
            log(Log.LOG_WARNING, "Unknown column reference: "
                    + schemaName + ' ' + relationName + ' ' + columnName);
        }
    }

    protected void log(int level, String msg) {
        // debug method
        // Log.log(level, msg);
    }
}