package cz.startnet.utils.pgdiff.parsers.antlr.statements.mssql;

import java.util.Arrays;
import java.util.List;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

import cz.startnet.utils.pgdiff.parsers.antlr.TSQLParser.Batch_statementContext;
import cz.startnet.utils.pgdiff.parsers.antlr.TSQLParser.Create_or_alter_triggerContext;
import cz.startnet.utils.pgdiff.parsers.antlr.TSQLParser.IdContext;
import cz.startnet.utils.pgdiff.parsers.antlr.msexpr.MsSqlClauses;
import cz.startnet.utils.pgdiff.schema.AbstractSchema;
import cz.startnet.utils.pgdiff.schema.MsTrigger;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgTriggerContainer;
import cz.startnet.utils.pgdiff.schema.StatementActions;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class CreateMsTrigger extends BatchContextProcessor {

    private final Create_or_alter_triggerContext ctx;

    private final boolean ansiNulls;
    private final boolean quotedIdentifier;

    public CreateMsTrigger(Batch_statementContext ctx, PgDatabase db,
            boolean ansiNulls, boolean quotedIdentifier, CommonTokenStream stream) {
        super(db, ctx, stream);
        this.ctx = ctx.batch_statement_body().create_or_alter_trigger();
        this.ansiNulls = ansiNulls;
        this.quotedIdentifier = quotedIdentifier;
    }

    @Override
    protected ParserRuleContext getDelimiterCtx() {
        return ctx.table_name;
    }

    @Override
    public void parseObject() {
        List<IdContext> ids = Arrays.asList(ctx.trigger_name.schema, ctx.table_name.name);
        addFullObjReference(ids, DbObjType.TABLE, StatementActions.NONE);
        getObject(getSchemaSafe(ids));
    }

    public MsTrigger getObject(AbstractSchema schema) {
        MsTrigger trigger = new MsTrigger(ctx.trigger_name.name.getText(),
                ctx.table_name.name.getText());
        trigger.setAnsiNulls(ansiNulls);
        trigger.setQuotedIdentified(quotedIdentifier);
        setSourceParts(trigger);

        IdContext schemaCtx = ctx.trigger_name.schema;
        String schemaName = schemaCtx != null ? schemaCtx.getText() : getDefSchemaName();
        MsSqlClauses clauses = new MsSqlClauses(schemaName);
        clauses.analyze(ctx.sql_clauses());
        trigger.addAllDeps(clauses.getDepcies());

        PgTriggerContainer cont = getSafe(AbstractSchema::getTriggerContainer,
                schema, ctx.table_name.name);

        addSafe(PgTriggerContainer::addTrigger, cont, trigger, ctx.trigger_name.schema,
                ctx.table_name.name, ctx.trigger_name.name);
        return trigger;
    }
}
