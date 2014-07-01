package cz.startnet.utils.pgdiff.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import cz.startnet.utils.pgdiff.PgDiffUtils;

/**
 * The superclass for general pgsql statement.
 * All changes to hashed fields of extending classes must be
 * followed by a {@link #resetHash()} call. 
 * 
 * @author Alexander Levsha
 */
abstract public class PgStatement {
    /**
     * The statement as it's been read from dump before parsing.
     */
    private final String rawStatement;
    
    protected final String name;
    
    private PgStatement parent;
    
    protected final List<PgPrivilege> privileges = new ArrayList<>(1);

    protected String owner;
    
    private volatile int hash;
    private volatile boolean hashComputed;

    public PgStatement(String name, String rawStatement) {
        this.name = name;
        this.rawStatement = rawStatement;
    }
    
    public String getRawStatement() {
        return rawStatement;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * @return Always returns just the object's name.
     */
    public final String getBareName() {
        return name;
    }

    public PgStatement getParent() {
        return parent;
    }
    
    public void setParent(PgStatement parent) {
        if(this.parent != null) {
            throw new IllegalStateException("Statement already has a parent: "
                    + this.getClass() + " Name: " + this.getName());
        }
        
        this.parent = parent;
    }
    
    public List<PgPrivilege> getPrivileges() {
        return Collections.unmodifiableList(privileges);
    }
    
    public void addPrivilege(PgPrivilege privilege) {
        privileges.add(privilege);
        privilege.setParent(this);
        resetHash();
    }
    
    protected StringBuilder appendPrivileges(StringBuilder sb) {
        if (privileges.isEmpty()) {
            return sb;
        }
        
        String type;
        if (this instanceof PgSchema) {
            type = "SCHEMA";
        } else if (this instanceof PgSequence) {
            type = "SEQUENCE";
        } else if (this instanceof PgTable) {
            type = "TABLE";
        } else if (this instanceof PgView) {
            type = "VIEW";
        } else if (this instanceof PgFunction) {
            type = "FUNCTION";
        } else {
            throw new IllegalStateException("GRANTs allowed only for SCHEMA, "
                    + "SEQUENCE, TABLE, VIEW, FUNCTION objects.");
        }
        sb.append("\n\n-- ")
            .append(type)
            .append(' ')
            .append(getName())
            .append(' ')
            .append("GRANT\n");
        
        for (PgPrivilege priv : privileges) {
            sb.append('\n').append(priv.getCreationSQL());
        }
        
        return sb;
    }
    
    public String getPrivilegesSQL() {
        return appendPrivileges(new StringBuilder()).toString();
    }

    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
        resetHash();
    }
    
    protected StringBuilder appendOwnerSQL(StringBuilder sb) {
        if (owner == null) {
            return sb;
        }
        
        String type;
        if (this instanceof PgSchema) {
            type = "SCHEMA";
        } else if (this instanceof PgSequence) {
            type = "SEQUENCE";
        } else if (this instanceof PgTable) {
            type = "TABLE";
        } else if (this instanceof PgView) {
            type = "VIEW";
        } else {
            throw new IllegalStateException("OWNERs allowed only for SCHEMA, "
                    + "SEQUENCE, TABLE, VIEW, FUNCTION objects.");
        }
        
        sb.append("\n\nALTER ")
            .append(type)
            .append(' ')
            .append(PgDiffUtils.getQuotedName(getName()))
            .append(" OWNER TO ")
            .append(owner)
            .append(';');
        
        return sb;
    }
    
    public String getOwnerSQL() {
        return appendOwnerSQL(new StringBuilder()).toString();
    }
    
    abstract public String getCreationSQL();
    
    abstract public String getDropSQL();
    
    /**
     * Copies all object properties into a new object and leaves all its children empty.
     * 
     * @return shallow copy of a DB object.
     */
    abstract public PgStatement shallowCopy();
    
    /**
     * Performs {@link #shallowCopy()} on this object and all its children.
     * 
     * @return a fully recursive copy of this statement.
     */
    abstract public PgStatement deepCopy();
    
    /**
     * This method does not account for nested child PgStatements.
     * Shallow version of {@link #equals(Object)}
     */
    abstract public boolean compare(PgStatement obj);
    
    /**
     * Compares this object and all its children with another statement.<br>
     * This default version falls back to {@link #compare(PgStatement)}.
     * Override for any object with nested children!
     * Overriding classes should still call this method via <code>super</code>
     * to get correct parent comparison and {@link #compare(PgStatement)} call.
     * <hr><br>
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj){
        if (obj instanceof PgStatement) {
            return this.compare((PgStatement) obj)
                    && this.parentNamesEquals((PgStatement) obj);
        }
        
        return false;
    }
    
    /**
     * Recursively compares objects' parents
     * to ensure their equal position in their object trees.
     */
    private boolean parentNamesEquals(PgStatement st){
        PgStatement p = parent;
        PgStatement p2 = st.getParent();
        while (p != null && p2 != null){
            if (!Objects.equals(p.getName(), p2.getName())){
                return false;
            }
            p = p.getParent();
            p2 = p2.getParent();
        }
        if (p == null && p2 == null) {
            return true;
        }
        return false;
    }
    
    /**
     * Calls {@link #computeHash()}. Modifies that value with combined hashcode
     * of all parents of this object in the tree to complement
     * {@link #parentNamesEquals(PgStatement)} and {@link #equals(Object)}<br>
     * Caches the hashcode value until recalculation is requested via {@link #resetHash()}.
     * Always request recalculation when you change the hashed fields.<br>
     * Override only with bare <code>super</code> call.
     * Do actual hashing in {@link #computeHash()}. 
     * <hr><br>
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        if (!hashComputed){
            hash = computeHash();
            
            final int prime = 31;
            PgStatement p = parent;
            while (p != null) {
                String pName = p.getName();
                hash = prime * hash + ((pName == null) ? 0 : pName.hashCode());
                p = p.getParent();
            }
            hashComputed = true;
        }
        return hash;
    }
    
    protected void resetHash(){
        PgStatement st = this;
        while (st != null){
            st.hashComputed = false;
            st = st.getParent();
        }
    }
    
    /**
     * @see #hashCode()
     */
    abstract protected int computeHash();
    
    /**
     * @return fully qualified (up to schema) dot-delimited object name.
     *          Identifiers are quoted.
     */
    public String getQualifiedName() {
        String name = PgDiffUtils.getQuotedName(getName());
        
        PgStatement parent = this.parent;
        while (parent != null) {
            if (parent instanceof PgDatabase) {
                break;
            }
            name = PgDiffUtils.getQuotedName(parent.getName())
                    + '.' + name;
            parent = parent.getParent();
        }
        
        return name;
    }
}
