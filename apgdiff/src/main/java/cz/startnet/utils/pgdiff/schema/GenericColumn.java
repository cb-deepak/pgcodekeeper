package cz.startnet.utils.pgdiff.schema;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffUtils;
import ru.taximaxim.codekeeper.apgdiff.log.Log;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class GenericColumn implements Serializable {

    @Deprecated
    // TODO detect these by separating their tokens from identifiers in parser
    // TODO might be fixed by handling these in AbstractExpr
    private static final Collection<String> SYS_COLUMNS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "oid", "tableoid", "xmin", "cmin", "xmax", "cmax", "ctid"
            )));

    private static final long serialVersionUID = -5032985077177033449L;
    // SONAR-OFF
    public final String schema;
    public final String table;
    public final String column;
    public final DbObjType type;
    // SONAR-ON

    public GenericColumn(String schema, String table, String column, DbObjType type) {
        this.schema = schema;
        this.table = table;
        this.column = column;
        this.type = type;
    }

    public GenericColumn(String schema, String object, DbObjType type) {
        this(schema, object, null, type);
    }

    public GenericColumn(String schema, DbObjType type) {
        this(schema, null, type);
    }

    public PgStatement getStatement(PgDatabase db) {
        return doGetStatement(db, type);
    }

    // debug method with selective logging
    private PgStatement doGetStatementLog(PgDatabase db, DbObjType type) {
        PgStatement st = doGetStatement(db, type);
        if (st != null) {
            return st;
        }

        if (isBuiltin() || "postgis".equals(schema) || type == DbObjType.FUNCTION) {
            return null;
        }

        // special case otherwise log is spammed by view columns
        if (type == DbObjType.COLUMN) {
            // look up relation (any)
            PgStatement rel = doGetStatement(db, DbObjType.TABLE);
            if (rel != null) {
                if (rel.getStatementType() != DbObjType.TABLE) {
                    // return silently if non-table
                    return null;
                }

                if (rel instanceof AbstractPgTable
                        && !((AbstractPgTable) rel).getInherits().isEmpty()) {
                    // or if inherited column
                    return null;
                }
            }
        }

        // not a silent failure, log
        Log.log(Log.LOG_WARNING, "Could not find statement for reference: " + this);
        return null;
    }

    private boolean isBuiltin() {
        return (type == DbObjType.TYPE && ApgdiffConsts.SYS_TYPES.contains(table))
                || ApgdiffUtils.isPgSystemSchema(schema) || SYS_COLUMNS.contains(column)
                || (table != null && table.startsWith("pg_"));
    }

    private PgStatement doGetStatement(PgDatabase db, DbObjType type) {
        if (type == null) {
            return null;
        }

        switch (type) {
        case DATABASE: return db;
        case SCHEMA: return db.getSchema(schema);
        case EXTENSION: return db.getExtension(schema);
        case FOREIGN_DATA_WRAPPER: return db.getForeignDW(schema);
        case SERVER: return db.getServer(schema);
        case CAST: return db.getCast(schema);
        case ASSEMBLY: return db.getAssembly(schema);
        case USER: return db.getUser(schema);
        case ROLE: return db.getRole(schema);
        default: break;
        }

        AbstractSchema s = db.getSchema(schema);
        if (s == null) {
            return null;
        }

        switch (type) {
        case TYPE: return getType(s);
        case DOMAIN: return s.getDomain(table);
        case SEQUENCE: return s.getSequence(table);
        case FUNCTION:
        case PROCEDURE:
        case AGGREGATE: return resolveFunctionCall(s);
        case OPERATOR: return resolveOperatorCall(s);
        case TABLE: return getRelation(s);
        case VIEW: return s.getView(table);

        case FTS_PARSER: return s.getFtsParser(table);
        case FTS_TEMPLATE: return s.getFtsTemplate(table);
        case FTS_DICTIONARY: return s.getFtsDictionary(table);
        case FTS_CONFIGURATION: return s.getFtsConfiguration(table);
        case INDEX: return s.getIndexByName(table);
        // handled in getStatement, left here for consistency
        case COLUMN:
            AbstractTable t = s.getTable(table);
            return t == null ? null : t.getColumn(column);
        default: break;
        }

        PgStatementContainer sc = s.getStatementContainer(table);
        switch (type) {
        case CONSTRAINT:
            return sc == null ? null : sc.getConstraint(column);
        case TRIGGER:
            return sc == null ? null : sc.getTrigger(column);
        case RULE:
            return sc == null ? null : sc.getRule(column);
        case POLICY:
            return sc == null ? null : sc.getPolicy(column);

        default: throw new IllegalStateException("Unhandled DbObjType: " + type);
        }
    }

    private PgStatement getRelation(AbstractSchema s) {
        PgStatement st = s.getTable(table);
        if (st != null) {
            return st;
        }
        st = s.getView(table);
        if (st != null) {
            return st;
        }
        st = s.getSequence(table);
        if (st != null) {
            return st;
        }
        // matviews, foreign tables probably go here (they have relkind values in pg_class)
        // indices and composite types are also pg_class relations
        // but they should never be reffered to as tables (or other "selectable" relations)
        return null;
    }

    private PgStatement getType(AbstractSchema s) {
        PgStatement st = s.getType(table);
        if (st != null) {
            return st;
        }
        st = s.getDomain(table);
        if (st != null) {
            return st;
        }
        // every "selectable" relation can be used as a type
        // getRelation should only look for "selectable" relations
        return getRelation(s);
    }

    private AbstractFunction resolveFunctionCall(AbstractSchema schema) {
        if (schema.isPostgres()) {
            return schema.getFunction(table);
        }

        for (AbstractFunction f : schema.getFunctions()) {
            if (f.getBareName().equals(table)) {
                return f;
            }
        }
        return null;
    }

    private PgOperator resolveOperatorCall(AbstractSchema schema) {
        PgOperator oper = null;
        if (table.indexOf('(') != -1) {
            oper = schema.getOperator(table);
        }
        if (oper != null) {
            return oper;
        }

        int found = 0;
        for (PgOperator o : schema.getOperators()) {
            if (o.getBareName().equals(table)) {
                ++found;
                oper = o;
            }
        }
        return found == 1 ? oper : null;
    }

    public String getObjName() {
        if (column != null) {
            return column;
        } else if (table != null) {
            return table;
        } else if (schema != null) {
            return schema;
        }

        return "";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((column == null) ? 0 : column.hashCode());
        result = prime * result + ((schema == null) ? 0 : schema.hashCode());
        result = prime * result + ((table == null) ? 0 : table.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        boolean eq = false;

        if (this == obj) {
            eq = true;
        } else if (obj instanceof GenericColumn) {
            GenericColumn col = (GenericColumn) obj;
            eq = Objects.equals(schema, col.schema)
                    && Objects.equals(table, col.table)
                    && Objects.equals(column, col.column)
                    && type == col.type;
        }

        return eq;
    }

    public String getQualifiedName() {
        return appendQualifiedName(new StringBuilder()).toString();
    }

    protected StringBuilder appendQualifiedName(StringBuilder sb) {
        if (type == DbObjType.CAST) {
            sb.append(schema);
            return sb;
        }

        if (schema != null) {
            sb.append(PgDiffUtils.getQuotedName(schema));
        }
        if (table != null) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            switch (type) {
            case FUNCTION:
            case PROCEDURE:
            case AGGREGATE:
                sb.append(table);
                break;
            default:
                sb.append(PgDiffUtils.getQuotedName(table));
            }
        }
        if (column != null) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(PgDiffUtils.getQuotedName(column));
        }
        return sb;
    }

    @Override
    public String toString() {
        return appendQualifiedName(new StringBuilder())
                .append(" (").append(type).append(')')
                .toString();
    }
}