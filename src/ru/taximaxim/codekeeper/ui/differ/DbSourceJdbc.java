package ru.taximaxim.codekeeper.ui.differ;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.eclipse.core.runtime.SubMonitor;

import ru.taximaxim.codekeeper.apgdiff.Log;
import cz.startnet.utils.pgdiff.parsers.CreateFunctionParser;
import cz.startnet.utils.pgdiff.parsers.Parser;
import cz.startnet.utils.pgdiff.schema.PgColumn;
import cz.startnet.utils.pgdiff.schema.PgConstraint;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgForeignKey;
import cz.startnet.utils.pgdiff.schema.PgFunction;
import cz.startnet.utils.pgdiff.schema.PgIndex;
import cz.startnet.utils.pgdiff.schema.PgPrivilege;
import cz.startnet.utils.pgdiff.schema.PgSchema;
import cz.startnet.utils.pgdiff.schema.PgSelect;
import cz.startnet.utils.pgdiff.schema.PgTable;
import cz.startnet.utils.pgdiff.schema.PgTrigger;
import cz.startnet.utils.pgdiff.schema.PgView;

public class DbSourceJdbc extends DbSource {

    /*
     * Trigger firing conditions
     */
    private final int TRIGGER_TYPE_ROW = 1 << 0;
    private final int TRIGGER_TYPE_BEFORE = 1 << 1;
    private final int TRIGGER_TYPE_INSERT = 1 << 2;
    private final int TRIGGER_TYPE_DELETE = 1 << 3;
    private final int TRIGGER_TYPE_UPDATE = 1 << 4;
    private final int TRIGGER_TYPE_TRUNCATE = 1 << 5;
    private final int TRIGGER_TYPE_INSTEAD = 1 << 6;
    
    
    private final String DB_TABLE_OF_TABLES = "pg_catalog.pg_class";
    private final String DB_TABLE_OF_CONSTRAINTS = "pg_catalog.pg_constraint";

    /**
     * Prepared statements to be executed
     */
    private PreparedStatement prepStatTriggers;
    private PreparedStatement prepStatFuncName;
    private PreparedStatement prepStatFunctions;
    private PreparedStatement prepStatLanguages;
    private PreparedStatement prepStatTypes;
    private PreparedStatement prepStatRoles;
    
    // TODO HashMap caching of namespace, type, language, owner names/oids?
    
    final private static Map<String, String> DATA_TYPE_ALIASES = new HashMap<String, String>(){
        {
            put("int8","bigint");
            put("serial8","bigserial");
            put("varbit","bit varying");
            put("bool","boolean");
            put("char","character");
            put("varchar","character varying");
            put("float8","double precision");
            put("int","integer");
            put("int4","integer");
            put("float4","real");
            put("int2","smallint");
            put("serial2","smallserial");
            put("serial4","serial");
            put("timetz","time with time zone");
            put("timestamptz","timestamp with time zone");
        }
    };
    
    private String host;
    private int port;
    private String user;
    private String pass;
    private Object dbName;
    private String encoding;
    
    private Connection connection;
    private DatabaseMetaData metaData;
    
    static final String JDBC_DRIVER = "org.postgresql.Driver";
    
    DbSourceJdbc(String host, int port, String user, String pass, String dbName, String encoding) {
        super(dbName);
        this.host = host;
        this.port = port;
        this.user = user;
        this.pass = pass;
        this.dbName = dbName;
        this.encoding = encoding;
    }

    @Override
    protected PgDatabase loadInternal(SubMonitor monitor) throws IOException {
        PgDatabase d = new PgDatabase();
        try {
            Class.forName(JDBC_DRIVER);
            connection = DriverManager.getConnection(
                   "jdbc:postgresql://" + host + ":" + port + "/" + dbName, user, pass);
            prepareStatements();
            metaData = connection.getMetaData();

            ResultSet res = metaData.getSchemas();
            while (res.next()) {
                String schemaName = res.getString("TABLE_SCHEM");
                if (schemaName.equals("pg_catalog") || schemaName.equals("information_schema")){
                    continue;
                }
                PgSchema schema = getSchema(res.getString("TABLE_SCHEM"));
                d.addSchema(schema);
            }
        } catch (SQLException e) {
            throw new IOException("Database JDBC access error occured", e);
        } catch (ClassNotFoundException e) {
            throw new IOException("JDBC driver class not found", e);
        }finally{
            try {
                connection.close();
            } catch (Exception e) {
                Log.log(Log.LOG_INFO, "Could not close JDBC connection", e);
            }
            try {
                prepStatTriggers.close();
            } catch (Exception e) {
                Log.log(Log.LOG_INFO, "Could not close prepared statement for triggers", e);
            }
            try {
                prepStatFuncName.close();
            } catch (Exception e) {
                Log.log(Log.LOG_INFO, "Could not close prepared statement for function names", e);
            }
            try {
                prepStatFunctions.close();
            } catch (Exception e) {
                Log.log(Log.LOG_INFO, "Could not close prepared statement for functions", e);
            }
        }
        return d;
    }
    
    private void prepareStatements() throws SQLException{
        prepStatTriggers = connection.prepareStatement("SELECT tgfoid, tgname, tgfoid, tgtype, tgrelid FROM pg_catalog.pg_trigger WHERE tgrelid = ?");
        prepStatFuncName = connection.prepareStatement("SELECT proname FROM pg_catalog.pg_proc WHERE oid = ?");
        prepStatFunctions = connection.prepareStatement("SELECT proname, prolang, prosrc, pg_get_functiondef(oid) AS probody, prorettype, proowner, proallargtypes, proargmodes, proargnames, pg_get_function_arguments(oid) AS proarguments, proargdefaults FROM pg_catalog.pg_proc WHERE pronamespace = ? AND proisagg = FALSE");
        prepStatLanguages = connection.prepareStatement("SELECT lanname FROM pg_catalog.pg_language WHERE oid = ?");
        prepStatTypes = connection.prepareStatement("SELECT typname FROM pg_catalog.pg_type WHERE oid = ?");
        prepStatRoles = connection.prepareStatement("SELECT pg_get_userbyid(?) AS rolname");
    }
    
    private PgSchema getSchema(String schema) throws SQLException{
        PgSchema s = new PgSchema(schema, "");
        
        ResultSet res = metaData.getTables(null, schema, "%", new String[] {"TABLE"} );
        
        while (res.next()) {
            PgTable table = getTable(schema, res.getString("table_name"));
            s.addTable(table);
        }
        
        res = metaData.getTables(null, schema, "%", new String[] {"VIEW"} );
        while (res.next()) {
            PgView view = getView(schema, res.getString("table_name"));
            s.addView(view);
        }
         
        prepStatFunctions.setInt(1, getSchemaOidByName(schema));
        res = prepStatFunctions.executeQuery();
        while (res.next()){
            PgFunction function = getFunction(res, schema);
            if (function != null){
                s.addFunction(function);
            }
        }
        
        return s;
    }
    
    private PgConstraint getConstraint(String schema, String constraint) throws SQLException {
        int schemaOid = getSchemaOidByName(schema);
        String query = "SELECT contype, conrelid, consrc, conkey, confrelid, confkey FROM " + DB_TABLE_OF_CONSTRAINTS + " WHERE connamespace = " + schemaOid + "" + " AND conname = '" + constraint + "'";
        ResultSet res = connection.createStatement().executeQuery(query);
        if (!res.next()){
            // TODO throw exception, log, output to console?
            System.err.println("Cannot create constraint " + constraint + ": no constraint found.");
            return null; 
        }
                
        /**
         * Constraint types:
         * 
         *   c = check constraint
         *   f = foreign key constraint
         *   p = primary key constraint
         *   
         *   u = unique constraint
         *   t = constraint trigger
         *   x = exclusion constraint
         */
        String definition = "";
        PgConstraint c = null;
        String conType = res.getString("contype");
        c = new PgConstraint(constraint, "", getSearchPath(schema));
        
        String[] columnNames = getColumnNames((Integer[])res.getArray("conkey").getArray(), res.getInt("conrelid"));
        switch (conType){
            case "f":
                c = new PgForeignKey(constraint, "", getSearchPath(schema));
                String[] referencedColumnNames = getColumnNames((Integer[])res.getArray("confkey").getArray(), res.getInt("confrelid"));
                definition = "FOREIGN KEY (";
                // TODO code reuse
                for(int i = 0; i < columnNames.length; i++){
                    String columnName = columnNames[i];
                    definition = definition.concat(columnName);
                    if(i < columnNames.length - 1){
                        definition = definition.concat(", ");
                    }
                }
                SimpleEntry<String, String> referencedTableName = getTableNameByOid(res.getInt("confrelid"));
                String schemaPrefix = "";
                if (!referencedTableName.getKey().equals(schema)){
                    schemaPrefix = referencedTableName.getKey() + ".";
                }
                definition = definition.concat(") REFERENCES " + schemaPrefix + referencedTableName.getValue() + "(");
                
                for(int i = 0; i < referencedColumnNames.length; i++){
                    String columnName = referencedColumnNames[i];
                    definition = definition.concat(columnName);
                    if(i < referencedColumnNames.length - 1){
                        definition = definition.concat(", ");
                    }
                }
                definition = definition.concat(")");
                break;
            case "p":
                definition = "PRIMARY KEY (";
                for(int i = 0; i < columnNames.length; i++){
                    String columnName = columnNames[i];
                    definition = definition.concat(columnName);
                    if(i < columnNames.length - 1){
                        definition = definition.concat(", ");
                    }
                }
                definition = definition.concat(")");
                break;
            case "c":
                definition = res.getString("consrc");
                break;
        }
        c.setDefinition(definition);
        
        // set table name
        String tableOid = res.getString("conrelid");
        if (!tableOid.equals("0")){
            query = "SELECT relname FROM " + DB_TABLE_OF_TABLES + " WHERE oid = '" + tableOid + "'";
            res = connection.createStatement().executeQuery(query);
            if(res.next()){
                c.setTableName(res.getString("relname"));
            }
        }
        
        // TODO c.setComment() ???
        return c;
    }
    
    private PgView getView(String schema, String view) throws SQLException {
        Statement stmt = null;
        String query = "select view_definition from INFORMATION_SCHEMA.views WHERE table_schema = '" + schema + "'" + " AND table_name = '" + view + "'";
        String definitionColumnName = "view_definition";
        stmt = connection.createStatement();
        ResultSet res = stmt.executeQuery(query);
        
        // try pg_catalog.views, if view is blocked (invisible or definition is null)
        if (!res.next() || res.getString(definitionColumnName) == null){
            query = "select definition from pg_catalog.pg_views WHERE schemaname = '" + schema + "'" + " AND viewname = '" + view + "'";
            stmt = connection.createStatement();
            res = stmt.executeQuery(query);
            definitionColumnName = "definition";
            if (!res.next()){
                // TODO throw exception, log, output to console?
                System.err.println("No view info found in INFORMATION_SCHEMA and pg_catalog for " + schema + "." + view);
                return null;
            }
        }
        
        String viewDef = res.getString(definitionColumnName);
        if (viewDef == null){
            // TODO throw exception, log, output to console?
            System.err.println("View without definition (locked): " + view);
            viewDef = "";
        }else if (viewDef.charAt(viewDef.length() - 1) == ';'){
            viewDef = viewDef.substring(0, viewDef.length() - 1);
        }
        PgView v = new PgView(view, viewDef, getSearchPath(schema));
        v.setQuery(viewDef);
        // skip column names (aliases), as they are not used by us
        
        // we skip PgSelect, as it does not affect export (does it?)
        // TODO can query selected columns from pg_catalog
        // prevent NPE, because select in PgView is not initialized
        v.setSelect(new PgSelect("", ""));
        
        // Query columns default values and comments
        ResultSet res2 = metaData.getColumns(null, schema, view, null);
        while(res2.next()){
            String colName = res2.getString("COLUMN_NAME");
            String colDefault = res2.getString("COLUMN_DEF");
            if (colDefault != null){
                v.addColumnDefaultValue(colName, colDefault);
            }
            String colComment = res2.getString("REMARKS");
            if (colComment != null){
                v.addColumnComment(colName, colComment);
            }
        }
        
        // Query owners of view
        query = "select viewowner from pg_catalog.pg_views WHERE schemaname = '" + schema + "'" + " AND viewname = '" + view + "'";
        stmt = connection.createStatement();
        res = stmt.executeQuery(query);
        if (res.next()){
            v.setOwner(res.getString("viewowner"));
        }
        
        // TODO Comment on view
        
        return v;
    }

    private PgTable getTable(String schema, String table) throws SQLException{
        StringBuilder tableDef = new StringBuilder(); 

        tableDef.append("CREATE TABLE " + table + " (\n");
        
        ResultSet res = metaData.getColumns(null, schema, table, "%");
        List<PgColumn> columns = new ArrayList<PgColumn>(5);
        while (res.next()) {
            String columnName = res.getString("COLUMN_NAME");
            PgColumn column = new PgColumn(columnName);
            
            String columnType = res.getString("TYPE_NAME");
            // TODO надо ли проверку на наличие DEFULT, если serial?
            if (DATA_TYPE_ALIASES.get(columnType) != null){
                columnType = DATA_TYPE_ALIASES.get(columnType);
            }else if (columnType.equals("bigserial")){
                columnType = "bigint";
            }else if (columnType.equals("serial")){
                columnType = "integer";
            }
            column.setType(columnType);
            
            tableDef.append("   " + columnName + " " + columnType);
            if (columnType.equals("character") || columnType.equals("character varying")){
                String numOfChars = "(" + res.getInt("CHAR_OCTET_LENGTH") + ")";
                tableDef.append(numOfChars);
                column.setType(column.getType() + numOfChars);
            }
            
            String columnDefault = res.getString("COLUMN_DEF");
            if (columnDefault != null){
                tableDef.append(" DEFAULT " + columnDefault);
                column.setDefaultValue(columnDefault);
            }
            
            if (res.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls){
                tableDef.append(" NOT NULL");
                column.setNullValue(false);
            }
            
            tableDef.append(",\n");
            columns.add(column);
        }
        tableDef.append(");");
        
        
        PgTable t = new PgTable(table, tableDef.toString(), getSearchPath(schema));
        
        for(PgColumn column : columns){
            t.addColumn(column);
        }
        
        // privileges
        String revokeMaindb = "ALL ON TABLE " + table + " TO maindb";
        String revokePublic = "ALL ON TABLE " + table + " TO PUBLIC";
        PgPrivilege p = new PgPrivilege(true, revokeMaindb, "REVOKE " + revokeMaindb);
        t.addPrivilege(p);
        p = new PgPrivilege(true, revokePublic, "REVOKE " + revokePublic);
        t.addPrivilege(p);
        
        res = metaData.getTablePrivileges(null, schema, table);
        Map<String, List<String>> privileges = new HashMap<String, List<String>>(10);
        while (res.next()) {
            String grantee = res.getString("GRANTEE");
            
            if (privileges.get(grantee) == null){
                privileges.put(grantee, new ArrayList<String>(Arrays.asList(res.getString("PRIVILEGE"))));
            }else if (privileges.get(grantee).size() < 6){
                privileges.get(grantee).add(res.getString("PRIVILEGE"));
            }else {
                privileges.put(grantee, new ArrayList<String>(Arrays.asList("ALL")));
            }
        }
        for(String grantee : privileges.keySet()){
            for(String priv : privileges.get(grantee)){
                String privDef = priv + " ON TABLE " + table + " TO " + grantee;
                p = new PgPrivilege(false, privDef, "GRANT " + privDef);
                t.addPrivilege(p);            
            }
        }
        // end privileges
        
        // Query OWNER
        String query = "SELECT tableowner FROM pg_catalog.pg_tables WHERE schemaname = '" + schema + "'" + " AND tablename = '" + table + "'";
        Statement stmt = connection.createStatement();
        res = stmt.executeQuery(query);
        if (res.next()){
            t.setOwner(res.getString("tableowner"));
        }
        
        // Query CONSTRAINTS
        query = "SELECT constraint_name, constraint_schema FROM information_schema.constraint_column_usage WHERE table_schema = '" + schema + "'" + " AND table_name = '" + table + "'";
        res = stmt.executeQuery(query);
        while (res.next()){
            String constraintName = res.getString("constraint_name");
            String constraintSchema = res.getString("constraint_schema");
            PgConstraint constraint = getConstraint(constraintSchema, constraintName);
            if (constraint != null){
                t.addConstraint(constraint);
            }
        }
        
        // Query INDECIES
        // TODO get oids earlier (or cache), as they are not changed in time (minimize db queries)
        int tableOid = getTableOidByName(table, getSchemaOidByName(schema));
        query = "SELECT indexrelid, indkey FROM pg_catalog.pg_index WHERE indrelid = '" + tableOid + "'";
        res = stmt.executeQuery(query);
        while (res.next()){
            String columnsString = res.getString("indkey");
            Scanner sc = new Scanner(columnsString);
            List<Integer> columnsNumbers = new ArrayList<Integer>(2);
            while(sc.hasNext()){
                columnsNumbers.add(Integer.valueOf(sc.next()));
            }
            sc.close();
            PgIndex index = getIndex(res.getInt("indexrelid"), tableOid, columnsNumbers.toArray(new Integer[columnsNumbers.size()]));
            if (index != null){
                t.addIndex(index);
            }
        }
        
        // Query TRIGGERS
        prepStatTriggers.setInt(1, tableOid);
        res = prepStatTriggers.executeQuery();
        while(res.next()){
            PgTrigger trigger = getTrigger(res);
            if (trigger != null){
                t.addTrigger(trigger);
            }
        }
        
        res.close();
        return t;
    }

    /**
     * Returns trigger object.
     * <br>
     * Available trigger firing conditions:
     *      boolean onDelete;
     *      boolean onInsert;
     *      boolean onUpdate;
     *      boolean onTruncate;
     *     
     *      boolean forEachRow;
     *      boolean before;
     */
    private PgTrigger getTrigger(ResultSet res) throws SQLException{
        
        String triggerName = res.getString("tgname");
        PgTrigger t = new PgTrigger(triggerName, "", "");
        
        int firingConditions = res.getInt("tgtype");
        if ((firingConditions & TRIGGER_TYPE_DELETE) != 0){
            t.setOnDelete(true);
        }
        if ((firingConditions & TRIGGER_TYPE_INSERT) != 0){
            t.setOnInsert(true);
        }
        if ((firingConditions & TRIGGER_TYPE_UPDATE) != 0){
            t.setOnUpdate(true);
        }
        if ((firingConditions & TRIGGER_TYPE_TRUNCATE) != 0){
            t.setOnTruncate(true);
        }
        if ((firingConditions & TRIGGER_TYPE_ROW) != 0){
            t.setForEachRow(true);
        }
        if ((firingConditions & TRIGGER_TYPE_BEFORE) != 0){
            t.setBefore(true);
        }
        
        String tableName = getTableNameByOid(res.getInt("tgrelid")).getValue();
        t.setTableName(tableName);
        
        String functionName = getFunctionNameByOid(res.getInt("tgfoid"));
        t.setFunction(functionName);
        return t;
    }
    
    private PgIndex getIndex(int indexOid, int tableOid, Integer[] columnsNumbers) throws SQLException {
        SimpleEntry<String, String> indexName = getTableNameByOid(indexOid);
        PgIndex i = new PgIndex(indexName.getValue(), "", getSearchPath(indexName.getKey()));
        i.setTableName(getTableNameByOid(tableOid).getValue());
        
        String definition = "USING ";
        
        String query = "SELECT pg_catalog.pg_am.amname "
                     + "FROM pg_catalog.pg_class JOIN pg_catalog.pg_am ON pg_class.relam = pg_am.oid "
                     + "WHERE pg_catalog.pg_class.oid = '" + indexOid + "'";
        ResultSet res = connection.createStatement().executeQuery(query);
        if(res.next()){
            definition = definition.concat(res.getString("amname"));
        }
        
        String [] columnNames = getColumnNames(columnsNumbers, tableOid);
        if (columnNames.length > 0){
            definition = definition.concat(" (");
        }
        for(int j = 0; j < columnNames.length; j++){
            definition = definition.concat(columnNames[j]);
            if (j < columnNames.length - 1){
                definition = definition.concat(", ");
            }
        }
        if (columnNames.length > 0){
            definition = definition.concat(")");
        }
        i.setDefinition(definition);
        
        return i;
    }
    
    /**
     * Returns function object accordingly to data stored in current res row
     * (except for aggregate functions). 
     * Defines function body from Postgres pg_get_functiondef() output.
     * <br><br>
     * 
     * Use this select query to define function manually (not completed):
     * "SELECT proname, prolang, prosrc, proisagg AS isaggregate, proiswindow AS iswindow, 
     *         prosecdef AS issecuritydefiner, proleakproof AS isleakproof, 
     *         proisstrict AS isnullonnull FROM pg_catalog.pg_proc WHERE pronamespace = ?"
     */
    private PgFunction getFunction(ResultSet res, String schemaName) throws SQLException{
        String definition = res.getString("probody");
        String languageName = getLangNameByOid(res.getInt("prolang"));
        int langFirstOccurenceIndex = definition.indexOf("LANGUAGE " + languageName); 
        String body = definition.substring(langFirstOccurenceIndex);
        
        PgFunction f = new PgFunction(res.getString("proname"), "", getSearchPath(schemaName));
        f.setBody(body);
        f.setReturns(getTypeNameByOid(res.getInt("prorettype")));
        f.setOwner(getRoleNameByOid(res.getInt("proowner")));

        String arguments = res.getString("proarguments");
        if (!arguments.isEmpty()){
            CreateFunctionParser.parseArguments(new Parser("(" + arguments + ")"), f);
        }

        return f;
    }
    
    private String getSearchPath(String schema){
        return "SET search_path = " + schema + ", pg_catalog";
    }

    /**
     * Returns an array of column names, resolved from conCols Array object 
     * by pg_attribute.attnum and tableOid
     * 
     * @param conCols   Array of ints containing column numbers (references pg_attribute.attnum)
     * @param tableOid  Oid of table - owner of these columns
     * @return
     */
    private String [] getColumnNames(Integer[] cols, int tableOid) throws SQLException{
        String query = "SELECT attname FROM pg_catalog.pg_attribute WHERE attrelid = " + tableOid + " AND attnum IN (";
        for(int i = 0; i < cols.length; i++){
            Integer colNum = cols[i];
            query = query.concat(colNum.toString());
            if (i < cols.length - 1){
                query = query.concat(", ");
            }
        }
        query = query.concat(")");
        List<String> columnNames = new ArrayList<String>(3);
        ResultSet res = connection.createStatement().executeQuery(query);
        while(res.next()){
            columnNames.add(res.getString("attname"));
        }
        return columnNames.toArray(new String[columnNames.size()]);
    }

    private String getFunctionNameByOid(int functionOid) throws SQLException{
        prepStatFuncName.setInt(1, functionOid);
        ResultSet res = prepStatFuncName.executeQuery();
        if (res.next()){
            return res.getString("proname");
        }
        return null;
    }

    /**
     * Returns schemaName.objectName pair for the table (and similar, such as 
     * Index or View) by seeking its oid in pg_catalog.pg_class  
     * @param tableOid
     * @return
     */
    private SimpleEntry<String, String> getTableNameByOid(int tableOid) throws SQLException{
        String query = "SELECT relname, relnamespace FROM pg_catalog.pg_class WHERE oid = '" + tableOid + "'";
        Statement stmt = connection.createStatement();
        ResultSet res = stmt.executeQuery(query);
        String tableName = null;
        String schemaOid = null;
        if(res.next()){
            schemaOid = res.getString("relnamespace");
            tableName = res.getString("relname");
        }else{
            return null;
        }
        query = "SELECT nspname FROM pg_catalog.pg_namespace WHERE oid = '" + schemaOid + "'";
        res = stmt.executeQuery(query);
        String schemaName = null;
        if(res.next()){
            schemaName = res.getString("nspname");
        }else{
            return null;
        }
        stmt.close();
        return new SimpleEntry<String, String>(schemaName, tableName);
    }

    private int getTableOidByName(String tableName, int schemaOid) throws SQLException{
        String query = "SELECT oid FROM " + DB_TABLE_OF_TABLES + " WHERE relname = '" + tableName + "' AND relnamespace = " + schemaOid + " AND relkind = 'r'";
        Statement stmt = connection.createStatement();
        ResultSet res = stmt.executeQuery(query);
        int tableOid = 0;
        if(res.next()){
            tableOid = res.getInt("oid");
        }
        if (res.next()){
            // WHAT?
            throw new IllegalArgumentException("There should be only one row for table " + tableName + 
                    " in schema oid " + schemaOid + ". Modify select query to limit result to single row."); 
        }
        return tableOid;
    }

    private int getSchemaOidByName(String schema) throws SQLException{
        String query = "SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = '" + schema + "'";
        Statement stmt = connection.createStatement();
        ResultSet res = stmt.executeQuery(query);
        int schemaOid;
        if(res.next()){
            schemaOid = res.getInt("oid");
        }else{
            throw new IllegalStateException("Could not resolve schema oid by its name " + schema + " in pg_catalog.pg_namespace");
        }
        
        if (res.next()){
            throw new IllegalStateException("More than one schema oid found by its name " + schema + " in pg_catalog.pg_namespace");
        }
        return schemaOid;
    }
    
    private String getLangNameByOid (int langOid) throws SQLException{
        prepStatLanguages.setInt(1, langOid);
        try(ResultSet res = prepStatLanguages.executeQuery()){
            if (res.next()){
                return res.getString("lanname");
            }
        }
        return null;
    }
    
    private String getTypeNameByOid(int typeOid) throws SQLException {
        prepStatTypes.setInt(1, typeOid);
        ResultSet res = prepStatTypes.executeQuery();
        if (res.next()){
            return res.getString("typname");
        }
        return "";
    }
    
    private String getRoleNameByOid(int ownerOid) throws SQLException {
        prepStatRoles.setInt(1, ownerOid);
        ResultSet res = prepStatRoles.executeQuery();
        if (res.next()){
            return res.getString("rolname");
        }
        return "";
    }
}