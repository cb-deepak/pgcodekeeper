package cz.startnet.utils.pgdiff.loader.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.loader.JdbcQueries;
import cz.startnet.utils.pgdiff.parsers.antlr.statements.CreateRule;
import cz.startnet.utils.pgdiff.schema.AbstractSchema;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import cz.startnet.utils.pgdiff.schema.PgEventType;
import cz.startnet.utils.pgdiff.schema.PgRule;
import cz.startnet.utils.pgdiff.schema.PgStatementContainer;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class RulesReader extends JdbcReader {

    public RulesReader(JdbcLoaderBase loader) {
        super(JdbcQueries.QUERY_RULES, loader);
    }

    @Override
    protected void processResult(ResultSet result, AbstractSchema schema) throws SQLException {
        String contName = result.getString(CLASS_RELNAME);
        PgStatementContainer c = schema.getStatementContainer(contName);
        if (c != null) {
            c.addRule(getRule(result, schema, contName));
        }
    }

    private PgRule getRule(ResultSet res, AbstractSchema schema, String tableName) throws SQLException {
        String schemaName = schema.getName();
        String ruleName = res.getString("rulename");
        loader.setCurrentObject(new GenericColumn(schemaName, tableName, ruleName, DbObjType.RULE));

        String command = res.getString("rule_string");
        checkObjectValidity(command, DbObjType.RULE, ruleName);
        PgRule r = new PgRule(ruleName);

        switch (res.getString("ev_type")) {
        case "1":
            r.setEvent(PgEventType.SELECT);
            break;
        case "2":
            r.setEvent(PgEventType.UPDATE);
            break;
        case "3":
            r.setEvent(PgEventType.INSERT);
            break;
        case "4":
            r.setEvent(PgEventType.DELETE);
            break;
        }

        if (res.getBoolean("is_instead")) {
            r.setInstead(true);
        }

        switch (res.getString("ev_enabled")) {
        case "A":
            r.setEnabledState("ENABLE ALWAYS");
            break;
        case "R":
            r.setEnabledState("ENABLE REPLICA");
            break;
        case "D":
            r.setEnabledState("DISABLE");
            break;
        }

        loader.submitAntlrTask(command, p -> p.sql().statement(0)
                .schema_statement().schema_create().create_rewrite_statement(),
                ctx -> CreateRule.setConditionAndAddCommands(ctx, r,
                        schema.getDatabase(), loader.getCurrentLocation()));

        loader.setAuthor(r, res);
        // COMMENT
        String comment = res.getString("comment");
        if (comment != null && !comment.isEmpty()) {
            r.setComment(loader.args, PgDiffUtils.quoteString(comment));
        }
        return r;
    }

    @Override
    protected String getClassId() {
        return "pg_rewrite";
    }
}
