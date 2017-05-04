package cz.startnet.utils.pgdiff.parsers.antlr.statements;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.parsers.antlr.GeneralLiteralSearch;
import cz.startnet.utils.pgdiff.parsers.antlr.QNameParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Common_constraintContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Constraint_commonContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Data_typeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Domain_constraintContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_argsContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_argumentsContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_callContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.IdentifierContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Owner_toContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Schema_qualified_nameContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Schema_qualified_name_nontypeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Table_column_defContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Table_column_definitionContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Table_referencesContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Table_unique_prkeyContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.VexContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParserBaseListener;
import cz.startnet.utils.pgdiff.parsers.antlr.exception.UnresolvedReferenceException;
import cz.startnet.utils.pgdiff.parsers.antlr.expr.ValueExpr;
import cz.startnet.utils.pgdiff.parsers.antlr.rulectx.Vex;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.IStatement;
import cz.startnet.utils.pgdiff.schema.PgColumn;
import cz.startnet.utils.pgdiff.schema.PgConstraint;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgFunction;
import cz.startnet.utils.pgdiff.schema.PgOptionContainer;
import cz.startnet.utils.pgdiff.schema.PgSchema;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

/**
 * Abstract Class contents common operations for parsing
 */
public abstract class ParserAbstract {
    protected final PgDatabase db;
    public ParserAbstract(PgDatabase db) {
        this.db = db;
    }

    /**
     * Parse object from context and return it
     *
     * @return parsed object
     */
    public abstract PgStatement getObject();

    protected String getDefSchemaName() {
        PgSchema s = db.getDefaultSchema();
        return s == null ? null : s.getName();
    }

    /**
     * Extracts raw text from context
     *
     * @param ctx
     *            context
     * @return raw string
     */
    public static String getFullCtxText(ParserRuleContext ctx) {
        Interval interval = new Interval(ctx.getStart().getStartIndex(),
                ctx.getStop().getStopIndex());
        return ctx.getStart().getInputStream().getText(interval);
    }

    protected PgColumn getColumn(Table_column_definitionContext colCtx, List<String> sequences,
            String defSchema) {
        PgColumn col = null;
        if (colCtx.column_name != null) {
            col = new PgColumn(colCtx.column_name.getText());
            col.setType(getFullCtxText(colCtx.datatype));
            addTypeAsDepcy(colCtx.datatype, col, getDefSchemaName());
            if (colCtx.collate_name != null) {
                col.setCollation(getFullCtxText(colCtx.collate_name.collation));
            }
            for (Constraint_commonContext column_constraint : colCtx.colmn_constraint) {
                if (column_constraint.constr_body().default_expr != null) {
                    col.setDefaultValue(getFullCtxText(column_constraint.constr_body().default_expr));
                    String sequence = getSequence(column_constraint.constr_body().default_expr);
                    if (sequence != null) {
                        sequences.add(sequence);
                    }

                    ValueExpr vex = new ValueExpr(defSchema);
                    vex.analyze(new Vex(column_constraint.constr_body().default_expr));
                    col.addAllDeps(vex.getDepcies());
                }

                Common_constraintContext comConstr = column_constraint.constr_body().common_constraint();
                if (comConstr != null && comConstr.null_value != null) {
                    col.setNullValue(comConstr.null_false == null);
                }
            }
        }

        return col;
    }

    protected String getSequence(VexContext default_expr) {
        SeqNameListener name = new SeqNameListener();
        ParseTreeWalker.DEFAULT.walk(name, default_expr);
        return name.getSeqName();
    }

    private static class SeqNameListener extends SQLParserBaseListener {

        private String seqName;

        @Override
        public void enterFunction_call(Function_callContext ctx) {
            GeneralLiteralSearch seq = new GeneralLiteralSearch();
            ParseTreeWalker.DEFAULT.walk(seq, ctx);
            if (seq.isFound()) {
                seqName = seq.getSeqName();
            }
        }
        public String getSeqName() {
            return seqName;
        }
    }

    public static void fillArguments(Function_argsContext function_argsContext,
            PgFunction function, String defSchemaName) {
        for (Function_argumentsContext argument : function_argsContext.function_arguments()) {
            PgFunction.Argument arg = new PgFunction.Argument();
            if (argument.argname != null) {
                arg.setName(argument.argname.getText());
            }
            arg.setDataType(getFullCtxText(argument.argtype_data));
            addTypeAsDepcy(argument.data_type(), function, defSchemaName);

            if (argument.function_def_value() != null) {
                arg.setDefaultExpression(getFullCtxText(argument
                        .function_def_value().def_value));
                for (GenericColumn objName : parseDefValues(argument
                        .function_def_value().def_value, defSchemaName)) {
                    function.addDep(objName);
                }
            }
            if (argument.arg_mode != null) {
                arg.setMode(argument.arg_mode.getText());
            }
            function.addArgument(arg);
        }
    }

    private static List<GenericColumn> parseDefValues(ParserRuleContext defExpression,
            final String defSchemaName) {
        final List<GenericColumn> funcSignature = new ArrayList<>();
        new ParseTreeWalker().walk(new SQLParserBaseListener() {
            @Override
            public void enterFunction_call(Function_callContext ctx) {
                List<IdentifierContext> ids = ctx.schema_qualified_name().identifier();
                String objName = QNameParser.getFirstName(ids);
                String schemaName = QNameParser.getSchemaName(ids, defSchemaName);
                funcSignature.add(new GenericColumn(schemaName, objName, DbObjType.FUNCTION));
            }
        }, defExpression);
        return funcSignature;
    }

    protected List<PgConstraint> getConstraint(Table_column_defContext colCtx) {
        List<PgConstraint> result = new ArrayList<>();
        // колоночные констрайнты добавляются в тип колонки, особенности апгдиффа
        if (colCtx.tabl_constraint != null) {
            result.add(getTableConstraint(colCtx.tabl_constraint));
        }
        return result;
    }

    protected PgConstraint getTableConstraint(Constraint_commonContext ctx) {
        String constrName = ctx.constraint_name == null ? "" : ctx.constraint_name.getText();
        PgConstraint constr = new PgConstraint(constrName, getFullCtxText(ctx));

        if (ctx.constr_body().FOREIGN() != null) {
            Table_referencesContext tblRef = ctx.constr_body().table_references();

            List<IdentifierContext> ids = tblRef.reftable.identifier();
            String tableName = QNameParser.getFirstName(ids);
            String schemaName = QNameParser.getSchemaName(ids, getDefSchemaName());
            GenericColumn ftable = new GenericColumn(schemaName, tableName, DbObjType.TABLE);
            constr.setForeignTable(ftable);
            constr.addDep(ftable);

            for (Schema_qualified_nameContext name : tblRef.column_references().names_references().name) {
                String colName = QNameParser.getFirstName(name.identifier());
                constr.addForeignColumn(colName);
                constr.addDep(new GenericColumn(schemaName, tableName, colName, DbObjType.COLUMN));
            }
        }
        if (ctx.constr_body().table_unique_prkey() != null) {
            setPrimaryUniq(ctx.constr_body().table_unique_prkey(), constr);
        }
        constr.setDefinition(getFullCtxText(ctx.constr_body()));
        return constr;
    }

    /**
     * Вычитать PrimaryKey или Unique со списком колонок
     */
    private void setPrimaryUniq(Table_unique_prkeyContext ctx,
            PgConstraint constr) {
        constr.setUnique(ctx.UNIQUE() != null);
        constr.setPrimaryKey(ctx.PRIMARY() != null);
        for (Schema_qualified_nameContext name : ctx.column_references()
                .names_references().name) {
            constr.addColumn(QNameParser.getFirstName(name.identifier()));
        }
    }

    protected PgConstraint parseDomainConstraint(Domain_constraintContext constr) {
        if (constr.common_constraint().check_boolean_expression() != null) {
            String constr_name = "";
            if (constr.name != null) {
                constr_name = QNameParser.getFirstName(constr.name.identifier());
            }
            PgConstraint constraint = new PgConstraint(constr_name,
                    getFullCtxText(constr));
            constraint.setDefinition(getFullCtxText(constr.common_constraint()));
            return constraint;
        }
        return null;
    }

    public static <T extends IStatement> T getSafe(Function <String, T> getter,
            IdentifierContext ctx) {
        return getSafe(getter, ctx.getText(), ctx.getStart());
    }

    public static <T extends IStatement> T getSafe(Function <String, T> getter,
            String name, Token errToken) {
        T statement = getter.apply(name);
        if (statement == null) {
            throw new UnresolvedReferenceException(errToken);
        }
        return statement;
    }

    public PgSchema getSchemaSafe(List<IdentifierContext> ids, PgSchema defaultSchema) {
        IdentifierContext schemaCtx = QNameParser.getSchemaNameCtx(ids);
        return schemaCtx == null ? defaultSchema : getSafe(db::getSchema, schemaCtx);
    }

    /**
     * Заполняет владельца
     * @param ctx контекст парсера с владельцем
     * @param st объект
     */
    protected void fillOwnerTo(Owner_toContext ctx, PgStatement st) {
        if (db.getArguments().isIgnorePrivileges()) {
            return;
        }
        if (ctx != null) {
            st.setOwner(ctx.name.getText());
        }
    }

    static void addTypeAsDepcy(Data_typeContext ctx, PgStatement st, String schema) {
        Schema_qualified_name_nontypeContext qname = ctx.predefined_type().schema_qualified_name_nontype();
        if (qname != null) {
            IdentifierContext schemaCtx = qname.identifier();
            st.addDep(new GenericColumn(schemaCtx == null ? schema : schemaCtx.getText(),
                    qname.identifier_nontype().getText(), DbObjType.TYPE));
        }
    }

    public static void fillStorageParams(String[] options, PgOptionContainer optionContainer,
            boolean isToast) {
        for (String pair : options) {
            int sep = pair.indexOf('=');
            String option, value;
            if (sep == -1) {
                option = pair;
                value = "";
            } else {
                option = pair.substring(0, sep);
                value = pair.substring(sep + 1);
            }
            if (optionContainer.getStatementType() != DbObjType.COLUMN
                    && !PgDiffUtils.isValidId(value, false, false)) {
                // only quote non-ids, do not quote columns
                // pg_dump behavior
                value = PgDiffUtils.quoteString(value);
            }
            fillStorageParams (value, option, isToast, optionContainer);
        }
    }

    public static void fillStorageParams (String value, String option, boolean isToast,
            PgOptionContainer optionContainer){
        String quotedOption = PgDiffUtils.getQuotedName(option);
        if (isToast) {
            quotedOption = "toast."+ option;
        }
        optionContainer.addOption(quotedOption, value);
    }
}
