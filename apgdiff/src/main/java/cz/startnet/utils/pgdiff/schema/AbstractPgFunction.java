package cz.startnet.utils.pgdiff.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import cz.startnet.utils.pgdiff.PgDiffArguments;
import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.hashers.Hasher;

public abstract class AbstractPgFunction extends AbstractFunction {

    public static final String FROM_CURRENT = "FROM CURRENT";

    protected static final float DEFAULT_INTERNAL_PROCOST = 1.0f;
    protected static final float DEFAULT_PROCOST = 100.0f;
    protected static final float DEFAULT_PROROWS = 1000.0f;

    private float rows = DEFAULT_PROROWS;
    private boolean isWindow;
    private boolean isStrict;
    private boolean isLeakproof;
    private boolean isSecurityDefiner;
    private String cost;
    private String language;
    private String parallel;
    private String volatileType;
    private String body;
    private String returns;
    private String supportFunc;

    protected final List<String> transforms = new ArrayList<>();
    protected final Map<String, String> configurations = new LinkedHashMap<>();
    protected final Map<String, String> returnsColumns = new LinkedHashMap<>();

    private String signatureCache;
    private boolean inStatementBody;

    public AbstractPgFunction(String name) {
        super(name);
    }

    @Override
    public String getCreationSQL() {
        final StringBuilder sbSQL = new StringBuilder();
        sbSQL.append("CREATE OR REPLACE ");
        sbSQL.append(getStatementType());
        sbSQL.append(' ');
        sbSQL.append(PgDiffUtils.getQuotedName(getSchemaName())).append('.');
        appendFunctionSignature(sbSQL, true, true);
        if (getReturns() != null) {
            sbSQL.append(' ');
            sbSQL.append("RETURNS ");
            sbSQL.append(getReturns());
        }
        sbSQL.append("\n    ");

        if (getLanguage() != null) {
            sbSQL.append("LANGUAGE ").append(PgDiffUtils.getQuotedName(getLanguage()));
        }

        if (!transforms.isEmpty()) {
            sbSQL.append(" TRANSFORM ");
            for (String tran : transforms) {
                sbSQL.append("FOR TYPE ").append(tran).append(", ");
            }

            sbSQL.setLength(sbSQL.length() - 2);
        }

        if (isWindow()) {
            sbSQL.append(" WINDOW");
        }

        if (getVolatileType() != null) {
            sbSQL.append(' ').append(getVolatileType());
        }

        if (isStrict()) {
            sbSQL.append(" STRICT");
        }

        if (isSecurityDefiner()) {
            sbSQL.append(" SECURITY DEFINER");
        }

        if (isLeakproof()) {
            sbSQL.append(" LEAKPROOF");
        }

        if (getParallel() != null) {
            sbSQL.append(" PARALLEL ").append(getParallel());
        }

        if (getCost() != null) {
            sbSQL.append(" COST ").append(getCost());
        }

        if (DEFAULT_PROROWS != getRows()) {
            sbSQL.append(" ROWS ");
            if (getRows() % 1 == 0) {
                sbSQL.append((int)getRows());
            } else {
                sbSQL.append(getRows());
            }
        }

        if (getSupportFunc() != null) {
            sbSQL.append(" SUPPORT ").append(getSupportFunc());
        }

        for (Entry<String, String> param : configurations.entrySet()) {
            String val = param.getValue();
            sbSQL.append("\n    SET ").append(param.getKey()).append(' ');
            if (FROM_CURRENT.equals(val)) {
                sbSQL.append(val);
            } else {
                sbSQL.append("TO ").append(val);
            }
        }

        sbSQL.append("\n    ");
        if (!isInStatementBody()) {
            sbSQL.append("AS ");
        }
        sbSQL.append(getBody());
        sbSQL.append(';');

        appendOwnerSQL(sbSQL);
        appendPrivileges(sbSQL);

        if (comment != null && !comment.isEmpty()) {
            sbSQL.append("\n\n");
            appendCommentSql(sbSQL);
        }

        return sbSQL.toString();
    }

    @Override
    public String getDropSQL() {
        final StringBuilder sbString = new StringBuilder();
        sbString.append("DROP ").append(getTypeName()).append(' ');
        appendFullName(sbString);
        sbString.append(';');
        return sbString.toString();
    }

    @Override
    protected String getFunctionFullSQL(boolean isCreate) {
        return getCreationSQL();
    }

    /**
     * Alias for {@link #getSignature()} which provides a unique function ID.
     *
     * Use {@link #getBareName()} to get just the function name.
     */
    @Override
    public String getName() {
        return getSignature();
    }

    @Override
    protected StringBuilder appendFullName(StringBuilder sb) {
        sb.append(PgDiffUtils.getQuotedName(getParent().getName())).append('.');
        appendFunctionSignature(sb, false, true);
        return sb;
    }

    /**
     * Appends signature of statement to sb.
     */
    public StringBuilder appendFunctionSignature(StringBuilder sb,
            boolean includeDefaultValues, boolean includeArgNames) {
        boolean cache = !includeDefaultValues && !includeArgNames;
        if (cache && signatureCache != null) {
            return sb.append(signatureCache);
        }
        final int sigStart = sb.length();

        sb.append(PgDiffUtils.getQuotedName(name)).append('(');
        boolean addComma = false;
        for (final Argument argument : arguments) {
            if (!includeArgNames && ArgMode.OUT == argument.getMode()) {
                continue;
            }
            if (addComma) {
                sb.append(", ");
            }
            argument.appendDeclaration(sb, includeDefaultValues, includeArgNames);
            addComma = true;
        }
        sb.append(')');

        if (cache) {
            signatureCache = sb.substring(sigStart, sb.length());
        }
        return sb;
    }

    public boolean isWindow() {
        return isWindow;
    }

    public void setWindow(boolean isWindow) {
        this.isWindow = isWindow;
        resetHash();
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguageCost(String language, Float cost) {
        this.language = language;

        if (cost != null) {
            String val = "" + (cost % 1 == 0 ? cost.intValue() : cost);

            if ("internal".equals(getLanguage()) || "c".equals(getLanguage())) {
                if (DEFAULT_INTERNAL_PROCOST != cost) {
                    this.cost = val;
                }
            } else if (DEFAULT_PROCOST != cost) {
                this.cost = val;
            }
        }

        resetHash();
    }

    public String getVolatileType() {
        return volatileType;
    }

    public void setVolatileType(String volatileType) {
        this.volatileType = volatileType;
        resetHash();
    }

    public boolean isStrict() {
        return isStrict;
    }

    public void setStrict(boolean isStrict) {
        this.isStrict = isStrict;
        resetHash();
    }

    public boolean isSecurityDefiner() {
        return isSecurityDefiner;
    }

    public void setSecurityDefiner(boolean isSecurityDefiner) {
        this.isSecurityDefiner = isSecurityDefiner;
        resetHash();
    }

    public boolean isLeakproof() {
        return isLeakproof;
    }

    public void setLeakproof(boolean isLeakproof) {
        this.isLeakproof = isLeakproof;
        resetHash();
    }

    public String getCost() {
        return cost;
    }

    public float getRows() {
        return rows;
    }

    public void setRows(float rows) {
        this.rows = rows;
        resetHash();
    }

    public String getParallel() {
        return parallel;
    }

    public void setParallel(String parallel) {
        this.parallel = parallel;
        resetHash();
    }

    public void setBody(final String body) {
        this.body = body;
        resetHash();
    }

    /**
     * Sets {@link #body} with newlines as requested in arguments.
     */
    public void setBody(PgDiffArguments args, String body) {
        setBody(args.isKeepNewlines() ? body : body.replace("\r", ""));
    }

    public String getBody() {
        return body;
    }


    public List<String> getTransform() {
        return Collections.unmodifiableList(transforms);
    }

    public void addTransform(String datatype) {
        transforms.add(datatype);
        resetHash();
    }

    public Map<String, String> getConfigurations() {
        return Collections.unmodifiableMap(configurations);
    }

    public void addConfiguration(String par, String val) {
        configurations.put(par, val);
        resetHash();
    }

    /**
     * @return the returns
     */
    @Override
    public String getReturns() {
        return returns;
    }

    /**
     * @param returns the returns to set
     */
    public void setReturns(String returns) {
        this.returns = returns;
        resetHash();
    }

    public String getSupportFunc() {
        return supportFunc;
    }

    public void setSupportFunc(String supportFunc) {
        this.supportFunc = supportFunc;
        resetHash();
    }

    /**
     * @return unmodifiable RETURNS TABLE map
     */
    @Override
    public Map<String, String> getReturnsColumns() {
        return Collections.unmodifiableMap(returnsColumns);
    }

    public void addReturnsColumn(String name, String type) {
        returnsColumns.put(name, type);
    }

    public boolean isInStatementBody() {
        return inStatementBody;
    }

    public void setInStatementBody(boolean inStatementBody) {
        this.inStatementBody = inStatementBody;
    }

    /**
     * Returns function signature. It consists of unquoted name and argument
     * data types.
     *
     * @return function signature
     */
    public String getSignature() {
        if (signatureCache == null) {
            signatureCache = appendFunctionSignature(new StringBuilder(), false, false).toString();
        }
        return signatureCache;
    }

    @Override
    protected boolean compareUnalterable(AbstractFunction function) {
        if (function instanceof AbstractPgFunction && super.compareUnalterable(function)) {
            AbstractPgFunction func = (AbstractPgFunction) function;

            return Objects.equals(body, func.getBody())
                    && isWindow == func.isWindow()
                    && Objects.equals(language, func.getLanguage())
                    && Objects.equals(parallel, func.getParallel())
                    && Objects.equals(volatileType, func.getVolatileType())
                    && isStrict == func.isStrict()
                    && isSecurityDefiner == func.isSecurityDefiner()
                    && isLeakproof == func.isLeakproof()
                    && rows == func.getRows()
                    && Objects.equals(cost, func.getCost())
                    && Objects.equals(returns, func.getReturns())
                    && transforms.equals(func.transforms)
                    && configurations.equals(func.configurations)
                    && Objects.equals(supportFunc, func.getSupportFunc());
        }

        return false;
    }

    @Override
    public void computeHash(Hasher hasher) {
        super.computeHash(hasher);
        hasher.put(returns);
        hasher.put(supportFunc);
        hasher.put(body);
        hasher.put(transforms);
        hasher.put(configurations);
        hasher.put(isWindow);
        hasher.put(language);
        hasher.put(volatileType);
        hasher.put(isStrict);
        hasher.put(isSecurityDefiner);
        hasher.put(isLeakproof);
        hasher.put(rows);
        hasher.put(cost);
        hasher.put(parallel);
    }

    @Override
    public AbstractFunction shallowCopy() {
        AbstractPgFunction functionDst = (AbstractPgFunction) super.shallowCopy();
        functionDst.setReturns(getReturns());
        functionDst.setSupportFunc(getSupportFunc());
        functionDst.setBody(getBody());
        functionDst.setWindow(isWindow());
        functionDst.language = getLanguage();
        functionDst.setVolatileType(getVolatileType());
        functionDst.setStrict(isStrict());
        functionDst.setSecurityDefiner(isSecurityDefiner());
        functionDst.setLeakproof(isLeakproof());
        functionDst.setRows(getRows());
        functionDst.cost = getCost();
        functionDst.setParallel(getParallel());
        functionDst.transforms.addAll(transforms);
        functionDst.returnsColumns.putAll(returnsColumns);
        functionDst.configurations.putAll(configurations);
        functionDst.setInStatementBody(isInStatementBody());

        return functionDst;
    }

    @Override
    public String getQualifiedName() {
        return getParent().getQualifiedName() + '.' + getName();
    }

    public class PgArgument extends Argument {

        private static final long serialVersionUID = -6351018532827424260L;

        public PgArgument(ArgMode mode, String name, String dataType) {
            super(mode, name, dataType);
        }

        @Override
        public void setDefaultExpression(String defaultExpression) {
            super.setDefaultExpression(defaultExpression);
            resetHash();
        }
    }
}
