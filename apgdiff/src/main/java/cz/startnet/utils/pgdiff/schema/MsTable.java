package cz.startnet.utils.pgdiff.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import cz.startnet.utils.pgdiff.MsDiffUtils;
import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.hashers.Hasher;

/**
 * Base MS SQL table class
 *
 * @since 5.3.1.
 * @author galiev_mr
 */
public class MsTable extends AbstractTable implements PgSimpleOptionContainer{

    private static final String MEMORY_OPTIMIZED = "MEMORY_OPTIMIZED";

    /**
     * list of internal primary keys for memory optimized table
     */
    private List<AbstractConstraint> pkeys;

    private boolean ansiNulls;
    private Boolean isTracked;

    private String textImage;
    private String fileStream;
    private String tablespace;

    public MsTable(String name) {
        super(name);
    }

    @Override
    public String getCreationSQL() {
        final StringBuilder sbSQL = new StringBuilder();

        appendName(sbSQL);
        appendColumns(sbSQL);
        appendOptions(sbSQL);
        appendAlterOptions(sbSQL);
        appendOwnerSQL(sbSQL);
        appendPrivileges(sbSQL);
        appendColumnsPriliges(sbSQL);

        return sbSQL.toString();
    }

    @Override
    public boolean appendAlterSQL(PgStatement newCondition, StringBuilder sb,
            AtomicBoolean isNeedDepcies) {
        final int startLength = sb.length();
        MsTable newTable = (MsTable) newCondition;

        if (isRecreated(newTable)) {
            isNeedDepcies.set(true);
            return true;
        }

        compareOptions(newTable, sb);
        compareOwners(newTable, sb);
        compareTableOptions(newTable, sb);
        alterPrivileges(newTable, sb);

        return sb.length() > startLength;
    }

    protected void appendAlterOptions(StringBuilder sbSQL) {
        if (isTracked != null) {
            enableTracking(sbSQL);
        }
    }

    protected void appendName(StringBuilder sbSQL) {
        sbSQL.append("SET QUOTED_IDENTIFIER ON").append(GO).append('\n');
        sbSQL.append("SET ANSI_NULLS ").append(ansiNulls ? "ON" : "OFF");
        sbSQL.append(GO).append('\n');
        sbSQL.append("CREATE TABLE ").append(getQualifiedName());
    }

    protected void appendColumns(StringBuilder sbSQL) {
        sbSQL.append("(\n");

        int start = sbSQL.length();
        for (AbstractColumn column : columns) {
            sbSQL.append("\t");
            sbSQL.append(column.getFullDefinition());
            sbSQL.append(",\n");
        }

        for (AbstractConstraint con : getPkeys()) {
            if (con.isPrimaryKey()) {
                sbSQL.append("\t");
                String name = con.name;
                if (!name.isEmpty()) {
                    sbSQL.append("CONSTRAINT ").append(MsDiffUtils.quoteName(name)).append(' ');
                }
                sbSQL.append(con.getDefinition());
                sbSQL.append(",\n");
            }
        }

        if (start != sbSQL.length()) {
            sbSQL.setLength(sbSQL.length() - 2);
            sbSQL.append('\n');
        }

        sbSQL.append(')');
    }

    public List<AbstractConstraint> getPkeys() {
        return pkeys == null ? Collections.emptyList() : Collections.unmodifiableList(pkeys);
    }

    @Override
    public void addConstraint(AbstractConstraint constraint) {
        if (constraint.isPrimaryKey() && isMemoryOptimized()) {
            if (pkeys == null) {
                pkeys = new ArrayList<>();
            }
            pkeys.add(constraint);
        } else {
            super.addConstraint(constraint);
        }
    }

    protected void appendOptions(StringBuilder sbSQL) {
        int startLength = sbSQL.length();
        if (tablespace != null) {
            // tablespace already quoted
            sbSQL.append(" ON ").append(tablespace).append(' ');
        }

        if (getTextImage() != null) {
            sbSQL.append("TEXTIMAGE_ON ").append(MsDiffUtils.quoteName(getTextImage())).append(' ');
        }

        if (getFileStream() != null) {
            sbSQL.append("FILESTREAM_ON ").append(MsDiffUtils.quoteName(getFileStream())).append(' ');
        }

        if (sbSQL.length() > startLength) {
            sbSQL.setLength(sbSQL.length() - 1);
        }

        StringBuilder sb = new StringBuilder();
        for (Entry <String, String> entry : options.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            sb.append(key);
            if (!value.isEmpty()) {
                sb.append(" = ").append(value);
            }
            sb.append(", ");
        }

        if (sb.length() > 0){
            sb.setLength(sb.length() - 2);
            sbSQL.append("\nWITH (").append(sb).append(")");
        }

        sbSQL.append(GO);
    }

    @Override
    protected boolean isNeedRecreate(AbstractTable newTable) {
        if (newTable instanceof MsTable) {
            MsTable smt = (MsTable) newTable;
            return !Objects.equals(smt.getTablespace(), getTablespace())
                    || isAnsiNulls() != smt.isAnsiNulls()
                    || !PgDiffUtils.setlikeEquals(smt.getPkeys(), getPkeys())
                    || !Objects.equals(smt.getOptions(), getOptions())
                    || !Objects.equals(smt.getFileStream(), getFileStream())
                    || (smt.getTextImage() != null && getTextImage() != null
                    && !Objects.equals(smt.getTextImage(), getTextImage()));
        }

        return true;
    }

    private void compareTableOptions(MsTable table, StringBuilder sb) {
        MsTable newTable = table;
        if (!Objects.equals(isTracked, newTable.isTracked())) {
            if (newTable.isTracked() == null) {
                sb.append(getAlterTable(true, false));
                sb.append(" DISABLE CHANGE_TRACKING").append(GO);
            } else if (isTracked == null) {
                newTable.enableTracking(sb);
            } else {
                sb.append(getAlterTable(true, false));
                sb.append(" DISABLE CHANGE_TRACKING").append(GO);
                newTable.enableTracking(sb);
            }
        }
    }

    private void enableTracking(StringBuilder sb) {
        sb.append(getAlterTable(true, false));
        sb.append(" ENABLE CHANGE_TRACKING WITH (TRACK_COLUMNS_UPDATED = ");
        sb.append(isTracked() ? "ON" : "OFF").append(')').append(GO);
    }

    @Override
    public String getDropSQL() {
        return "DROP TABLE " + getQualifiedName() + GO;
    }

    @Override
    protected String getAlterTable(boolean nextLine, boolean only) {
        StringBuilder sb = new StringBuilder();
        if (nextLine) {
            sb.append("\n\n");
        }
        sb.append("ALTER TABLE ");
        sb.append(getQualifiedName());
        return sb.toString();
    }

    public String getFileStream() {
        return fileStream;
    }

    public void setFileStream(String fileStream) {
        this.fileStream = fileStream;
        resetHash();
    }

    public String getTextImage() {
        return textImage;
    }

    public void setTextImage(String textImage) {
        this.textImage = textImage;
        resetHash();
    }

    public void setAnsiNulls(boolean ansiNulls) {
        this.ansiNulls = ansiNulls;
        resetHash();
    }

    public boolean isAnsiNulls() {
        return ansiNulls;
    }

    public Boolean isTracked() {
        return isTracked;
    }

    public void setTracked(final Boolean isTracked) {
        this.isTracked = isTracked;
        resetHash();
    }

    public String getTablespace() {
        return tablespace;
    }

    public void setTablespace(final String tablespace) {
        this.tablespace = tablespace;
        resetHash();
    }

    public boolean isMemoryOptimized() {
        return "ON".equalsIgnoreCase(getOptions().get(MEMORY_OPTIMIZED));
    }

    @Override
    public boolean isPostgres() {
        return false;
    }

    @Override
    public boolean compare(PgStatement obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof MsTable && super.compare(obj)) {
            MsTable table = (MsTable) obj;
            return ansiNulls == table.isAnsiNulls()
                    && Objects.equals(textImage, table.getTextImage())
                    && Objects.equals(fileStream, table.getFileStream())
                    && Objects.equals(isTracked, table.isTracked())
                    && Objects.equals(tablespace, table.getTablespace())
                    && PgDiffUtils.setlikeEquals(getPkeys(), table.getPkeys());
        }

        return false;
    }

    @Override
    public void computeHash(Hasher hasher) {
        super.computeHash(hasher);
        hasher.put(getTextImage());
        hasher.put(getFileStream());
        hasher.put(isAnsiNulls());
        hasher.put(isTracked());
        hasher.put(getTablespace());
        hasher.putUnordered(getPkeys());
    }

    @Override
    protected MsTable getTableCopy() {
        MsTable table = new MsTable(name);
        table.setFileStream(getFileStream());
        table.setTextImage(getTextImage());
        table.setAnsiNulls(isAnsiNulls());
        table.setTracked(isTracked());
        table.setTablespace(getTablespace());

        if (pkeys != null) {
            table.pkeys = new ArrayList<>();
            table.pkeys.addAll(pkeys);
        }
        return table;
    }
}
