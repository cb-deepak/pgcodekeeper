package cz.startnet.utils.pgdiff.parsers.antlr.statements;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.antlr.v4.runtime.Token;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.parsers.antlr.AntlrParser;
import cz.startnet.utils.pgdiff.parsers.antlr.AntlrTask;
import cz.startnet.utils.pgdiff.parsers.antlr.AntlrUtils;
import cz.startnet.utils.pgdiff.parsers.antlr.QNameParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Character_stringContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_function_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Data_typeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_actions_commonContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_argumentsContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_bodyContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_column_name_typeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_defContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.IdentifierContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Identifier_nontypeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Schema_qualified_name_nontypeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Set_statement_valueContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Storage_parameter_optionContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Transform_for_typeContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.VexContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.With_storage_parameterContext;
import cz.startnet.utils.pgdiff.parsers.antlr.expr.launcher.FuncProcAnalysisLauncher;
import cz.startnet.utils.pgdiff.parsers.antlr.expr.launcher.VexAnalysisLauncher;
import cz.startnet.utils.pgdiff.schema.AbstractPgFunction;
import cz.startnet.utils.pgdiff.schema.Argument;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgFunction;
import cz.startnet.utils.pgdiff.schema.PgProcedure;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;
import ru.taximaxim.codekeeper.apgdiff.utils.Pair;

public class CreateFunction extends ParserAbstract {

    private final List<Object> errors;
    private final Queue<AntlrTask<?>> antlrTasks;
    private final Create_function_statementContext ctx;


    public CreateFunction(Create_function_statementContext ctx, PgDatabase db,
            List<Object> errors, Queue<AntlrTask<?>> antlrTasks) {
        super(db);
        this.ctx = ctx;
        this.errors = errors;
        this.antlrTasks = antlrTasks;
    }

    @Override
    public void parseObject() {
        List<IdentifierContext> ids = ctx.function_parameters().schema_qualified_name().identifier();
        String name = QNameParser.getFirstName(ids);
        AbstractPgFunction function = ctx.PROCEDURE() != null ? new PgProcedure(name)
                : new PgFunction(name);

        fillFunction(function);

        if (ctx.ret_table != null) {
            function.setReturns(getFullCtxText(ctx.ret_table));
            for (Function_column_name_typeContext ret_col : ctx.ret_table.function_column_name_type()) {
                addPgTypeDepcy(ret_col.data_type(), function);
                function.addReturnsColumn(ret_col.identifier().getText(), getTypeName(ret_col.data_type()));
            }
        } else if (ctx.rettype_data != null) {
            function.setReturns(getTypeName(ctx.rettype_data));
            addPgTypeDepcy(ctx.rettype_data, function);
        }
        addSafe(getSchemaSafe(ids), function, ids, parseArguments(ctx.function_parameters().function_args()));
    }

    private void fillFunction(AbstractPgFunction function) {
        Function_defContext funcDef = null;
        Float cost = null;
        String language = null;
        for (Function_actions_commonContext action : ctx.function_actions_common()) {
            if (action.WINDOW() != null) {
                function.setWindow(true);
            } else if (action.IMMUTABLE() != null) {
                function.setVolatileType("IMMUTABLE");
            } else if (action.STABLE() != null) {
                function.setVolatileType("STABLE");
            } else if (action.STRICT() != null || action.RETURNS() != null) {
                function.setStrict(true);
            } else if (action.DEFINER() != null) {
                function.setSecurityDefiner(true);
            } else if (action.LEAKPROOF() != null) {
                function.setLeakproof(action.NOT() == null);
            } else if (action.LANGUAGE() != null) {
                language = action.lang_name.getText();
            } else if (action.COST() != null) {
                cost = Float.parseFloat(action.execution_cost.getText());
            } else if (action.ROWS() != null) {
                float f = Float.parseFloat(action.result_rows.getText());
                if (0.0f != f) {
                    function.setRows(Float.parseFloat(action.result_rows.getText()));
                }
            } else if (action.AS() != null) {
                funcDef = action.function_def();
                function.setBody(db.getArguments(), getFullCtxText(funcDef));
            } else if (action.TRANSFORM() != null) {
                for (Transform_for_typeContext transform : action.transform_for_type()) {
                    function.addTransform(ParserAbstract.getFullCtxText(transform.data_type()));
                }
            } else if (action.SAFE() != null) {
                function.setParallel("SAFE");
            } else if (action.RESTRICTED() != null) {
                function.setParallel("RESTRICTED");
            } else if (action.SET() != null) {
                setConfigParams(action, function);
            } else if (action.SUPPORT() != null) {
                List<IdentifierContext> suppFuncIds = action.schema_qualified_name().identifier();
                function.setSupportFunc(getFullCtxText(suppFuncIds));
                addDepSafe(function, suppFuncIds, DbObjType.FUNCTION, true);
            }
        }

        List<Pair<String, GenericColumn>> funcArgs = fillArguments(function);

        Function_bodyContext body = ctx.function_body();
        if (body != null) {
            function.setInStatementBody(true);
            String bodyText = getFullCtxText(body);
            function.setBody(db.getArguments(), bodyText);
            if (language == null) {
                language = "sql";
            }
            analyzeFunctionBody(function, body, funcArgs);
        } else if (funcDef != null && funcDef.symbol == null
                && ("SQL".equalsIgnoreCase(language) || "PLPGSQL".equalsIgnoreCase(language))) {
            analyzeFunctionDefinition(function, language, funcDef.definition, funcArgs);
        }

        With_storage_parameterContext storage = ctx.with_storage_parameter();
        if (storage != null) {
            for (Storage_parameter_optionContext option : storage.storage_parameter().storage_parameter_option()) {
                if ("isStrict".equalsIgnoreCase(option.getText())) {
                    function.setStrict(true);
                } else if ("isCachable".equalsIgnoreCase(option.getText())) {
                    function.setVolatileType("IMMUTABLE");
                }
            }
        }

        function.setLanguageCost(language, cost);
    }

    private void setConfigParams(Function_actions_commonContext action, AbstractPgFunction function) {
        IdentifierContext scope = action.config_scope;
        String par;
        if (scope != null) {
            par = PgDiffUtils.getQuotedName(
                    scope.getText() + '.' + action.config_param.getText());
        } else {
            par = PgDiffUtils.getQuotedName(action.config_param.getText());
        }

        if (action.FROM() != null) {
            function.addConfiguration(par, AbstractPgFunction.FROM_CURRENT);
        } else {
            Set_statement_valueContext set = action.set_statement_value();
            if (set.DEFAULT() != null) {
                function.addConfiguration(par, "DEFAULT");
            } else {
                StringBuilder sb = new StringBuilder();
                for (VexContext val : set.vex()) {
                    sb.append(getFullCtxText(val)).append(", ");
                }
                sb.setLength(sb.length() - 2);
                function.addConfiguration(par, sb.toString());
            }
        }
    }

    private void analyzeFunctionDefinition(AbstractPgFunction function, String language,
            Character_stringContext definition, List<Pair<String, GenericColumn>> funcArgs) {
        Pair<String, Token> pair = unquoteQuotedString(definition);
        String def = pair.getFirst();
        Token start = pair.getSecond();

        // Parsing the function definition and adding its result context for analysis.
        // Adding contexts of function arguments for analysis.

        String name = "function definition of " + function.getBareName();
        List<Object> err = new ArrayList<>();

        if ("SQL".equalsIgnoreCase(language)) {
            AntlrParser.submitAntlrTask(antlrTasks,
                    () -> AntlrParser.makeBasicParser(SQLParser.class, def,
                            name, err, start).sql(),
                    ctx -> {
                        errors.addAll(err);
                        FuncProcAnalysisLauncher launcher = new FuncProcAnalysisLauncher(
                                function, ctx, fileName, funcArgs);
                        launcher.setOffset(start);
                        db.addAnalysisLauncher(launcher);
                    });
        } else if ("PLPGSQL".equalsIgnoreCase(language)) {
            AntlrParser.submitAntlrTask(antlrTasks,
                    () -> {
                        SQLParser parser = AntlrParser.makeBasicParser(SQLParser.class, def, name, err, start);
                        AntlrUtils.removeIntoStatements(parser);
                        return parser.plpgsql_function();
                    },
                    ctx -> {
                        errors.addAll(err);
                        FuncProcAnalysisLauncher launcher = new FuncProcAnalysisLauncher(
                                function, ctx, fileName, funcArgs);
                        launcher.setOffset(start);
                        db.addAnalysisLauncher(launcher);
                    });
        }
    }

    private void analyzeFunctionBody(AbstractPgFunction function, Function_bodyContext body,
            List<Pair<String, GenericColumn>> funcArgs) {
        // finalizer-only task, defers analyzer until finalizing stage
        AntlrParser.submitAntlrTask(antlrTasks, () -> body,
                ctx -> {
                    FuncProcAnalysisLauncher launcher = new FuncProcAnalysisLauncher(
                            function, ctx, fileName, funcArgs);
                    db.addAnalysisLauncher(launcher);
                });
    }

    /**
     * Returns a list of pairs, each of which contains the name of the argument
     * and its full type name in GenericColumn object (typeSchema, typeName, DbObjType.TYPE).
     */
    private List<Pair<String, GenericColumn>> fillArguments(AbstractPgFunction function) {
        List<Pair<String, GenericColumn>> funcArgs = new ArrayList<>();
        for (Function_argumentsContext argument : ctx.function_parameters()
                .function_args().function_arguments()) {
            Identifier_nontypeContext name = argument.identifier_nontype();
            String argName = name != null ? name.getText() : null;
            String typeSchema = ApgdiffConsts.PG_CATALOG;
            String typeName;

            Data_typeContext dataType = argument.data_type();
            Schema_qualified_name_nontypeContext typeQname = dataType.predefined_type()
                    .schema_qualified_name_nontype();
            if (typeQname != null) {
                if (typeQname.schema != null) {
                    typeSchema = typeQname.schema.getText();
                }
                typeName = typeQname.identifier_nontype().getText();
            } else {
                typeName = getFullCtxText(dataType);
            }

            Argument arg = new Argument(parseArgMode(argument.argmode()),
                    argName, getTypeName(dataType));
            addPgTypeDepcy(dataType, function);

            VexContext def = argument.vex();
            if (def != null) {
                arg.setDefaultExpression(getFullCtxText(def));
                db.addAnalysisLauncher(new VexAnalysisLauncher(function, def, fileName));
            }

            function.addArgument(arg);
            funcArgs.add(new Pair<>(argName, new GenericColumn(typeSchema, typeName, DbObjType.TYPE)));
        }
        return funcArgs;
    }

    @Override
    protected String getStmtAction() {
        return getStrForStmtAction(ACTION_CREATE,
                ctx.PROCEDURE() != null ? DbObjType.PROCEDURE : DbObjType.FUNCTION,
                        ctx.function_parameters().schema_qualified_name());
    }
}