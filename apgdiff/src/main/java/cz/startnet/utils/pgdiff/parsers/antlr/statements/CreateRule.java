package cz.startnet.utils.pgdiff.parsers.antlr.statements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cz.startnet.utils.pgdiff.parsers.antlr.QNameParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Create_rewrite_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.IdentifierContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Rewrite_commandContext;
import cz.startnet.utils.pgdiff.parsers.antlr.expr.launcher.RuleAnalysisLauncher;
import cz.startnet.utils.pgdiff.schema.AbstractSchema;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgEventType;
import cz.startnet.utils.pgdiff.schema.PgRule;
import cz.startnet.utils.pgdiff.schema.PgStatementContainer;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class CreateRule extends ParserAbstract {
    private final Create_rewrite_statementContext ctx;

    public CreateRule(Create_rewrite_statementContext ctx, PgDatabase db) {
        super(db);
        this.ctx = ctx;
    }

    @Override
    public void parseObject() {
        List<IdentifierContext> ids = ctx.table_name.identifier();
        addObjReference(ids, DbObjType.TABLE, null);

        PgRule rule = new PgRule(ctx.name.getText());
        rule.setEvent(PgEventType.valueOf(ctx.event.getText().toUpperCase(Locale.ROOT)));
        if (ctx.INSTEAD() != null){
            rule.setInstead(true);
        }

        setConditionAndAddCommands(ctx, rule, db, fileName);

        IdentifierContext parent = QNameParser.getFirstNameCtx(ids);
        PgStatementContainer cont = getSafe(AbstractSchema::getStatementContainer,
                getSchemaSafe(ids), parent);
        addSafe(cont, rule, Arrays.asList(QNameParser.getSchemaNameCtx(ids), parent, ctx.name));
    }

    public static void setConditionAndAddCommands(Create_rewrite_statementContext ctx,
            PgRule rule, PgDatabase db, String location) {
        rule.setCondition((ctx.WHERE() != null) ? getFullCtxText(ctx.vex()) : null);

        // allows to write a common namespace-setup code with no copy-paste for each cmd type
        for (Rewrite_commandContext cmd : ctx.rewrite_command()) {
            rule.addCommand(db.getArguments(), getFullCtxText(cmd));
        }

        db.addAnalysisLauncher(new RuleAnalysisLauncher(rule, ctx, location));
    }

    @Override
    protected String getStmtAction() {
        List<IdentifierContext> ids = new ArrayList<>(ctx.table_name.identifier());
        ids.add(ctx.name);
        return getStrForStmtAction(ACTION_CREATE, DbObjType.RULE, ids);
    }
}
