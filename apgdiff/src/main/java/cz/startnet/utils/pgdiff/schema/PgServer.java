package cz.startnet.utils.pgdiff.schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.hashers.Hasher;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class PgServer extends PgStatement implements PgForeignOptionContainer{
    private String type;
    private String version;
    private String fdw;
    private final Map<String, String> options = new LinkedHashMap<>();

    public PgServer(String name) {
        super(name);
    }

    @Override
    public String getAlterHeader() {
        return "\n\nALTER SERVER " + getQualifiedName();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        resetHash();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        resetHash();
    }

    public String getFdw() {
        return fdw;
    }

    public void setFdw(String fdw) {
        this.fdw = fdw;
        resetHash();
    }

    @Override
    public Map<String, String> getOptions() {
        return options;
    }

    @Override
    public void addOption(String key, String value) {
        this.options.put(key, value);
        resetHash();
    }

    @Override
    public DbObjType getStatementType() {
        return DbObjType.SERVER;
    }

    @Override
    public void computeHash(Hasher hasher) {
        hasher.put(type);
        hasher.put(version);
        hasher.put(fdw);
        hasher.put(options);
    }

    @Override
    public boolean compare(PgStatement obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof PgServer && super.compare(obj)) {
            PgServer srv = (PgServer) obj;
            return obj instanceof PgServer && super.compare(obj)
                    && Objects.equals(type, srv.getType())
                    && Objects.equals(version, srv.getVersion())
                    && Objects.equals(fdw, srv.getFdw())
                    && Objects.equals(options, srv.getOptions());
        }
        return false;
    }

    @Override
    public PgDatabase getDatabase() {
        return (PgDatabase)getParent();
    }

    @Override
    public String getCreationSQL() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CREATE SERVER ").append(PgDiffUtils.getQuotedName(getName()));
        if (getType() != null) {
            sb.append(" TYPE ").append(getType());
        }
        if (getVersion() != null) {
            sb.append(" VERSION ").append(getVersion());
        }
        sb.append(" FOREIGN DATA WRAPPER ").append(PgDiffUtils.getQuotedName(getFdw()));

        if (!options.isEmpty()) {
            sb.append(" OPTIONS ").append("(");
            for (Entry<String, String> entry : options.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                sb.append(PgDiffUtils.getQuotedName(key))
                .append(" ")
                .append(value)
                .append(", ");
            }
            sb.setLength(sb.length() - 2);
            sb.append(")");
        }
        sb.append(";");
        appendOwnerSQL(sb);
        appendPrivileges(sb);

        if (comment != null && !comment.isEmpty()) {
            sb.append("\n\n");
            appendCommentSql(sb);
        }
        return sb.toString();
    }

    @Override
    public String getDropSQL() {
        return "DROP SERVER " + PgDiffUtils.getQuotedName(getName()) + ';';
    }

    @Override
    public boolean appendAlterSQL(PgStatement newCondition, StringBuilder sb,
            AtomicBoolean isNeedDepcies) {
        final int startLength = sb.length();
        PgServer newServer = (PgServer) newCondition;
        if (!Objects.equals(newServer.getFdw(), getFdw()) ||
                !Objects.equals(newServer.getType(), getType())) {
            isNeedDepcies.set(true);
            return true;
        }

        if (!Objects.equals(newServer.getVersion(), getVersion())) {
            sb.append(getAlterHeader());
            sb.append(" VERSION ").append(newServer.getVersion())
            .append(';');
        }

        if (!Objects.equals(newServer.getOptions(), getOptions())) {
            compareOptions(newServer, sb);
        }

        if (!Objects.equals(newServer.getOwner(), getOwner())) {
            newServer.appendOwnerSQL(sb);
        }
        alterPrivileges(newCondition, sb);

        if (!Objects.equals(newServer.getComment(), getComment())) {
            sb.append("\n\n");
            newServer.appendCommentSql(sb);
        }
        return sb.length() > startLength;
    }

    @Override
    public PgStatement shallowCopy() {
        PgServer copyServer = new PgServer(getName());
        copyBaseFields(copyServer);
        copyServer.setType(type);
        copyServer.setVersion(version);
        copyServer.setFdw(fdw);
        copyServer.options.putAll(options);
        return copyServer;
    }
}
