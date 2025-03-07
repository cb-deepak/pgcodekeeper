package ru.taximaxim.codekeeper.ui.differ;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.operation.IRunnableWithProgress;

import cz.startnet.utils.pgdiff.PgDiff;
import cz.startnet.utils.pgdiff.PgDiffArguments;
import cz.startnet.utils.pgdiff.PgDiffUtils;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement;
import ru.taximaxim.codekeeper.ui.Log;
import ru.taximaxim.codekeeper.ui.UIConsts.DB_UPDATE_PREF;
import ru.taximaxim.codekeeper.ui.UIConsts.PLUGIN_ID;
import ru.taximaxim.codekeeper.ui.localizations.Messages;
import ru.taximaxim.codekeeper.ui.properties.OverridablePrefs;

public class Differ implements IRunnableWithProgress {

    private final PgDatabase sourceDbFull;
    private final PgDatabase targetDbFull;
    private final TreeElement root;
    private final boolean needTwoWay;
    private final String timezone;
    private final boolean msSql;
    private final IProject proj;
    private final Map<String, Boolean> oneTimePrefs;

    private String diffDirect;
    private String diffReverse;

    private List<Entry<PgStatement, PgStatement>> additionalDepciesSource;
    private List<Entry<PgStatement, PgStatement>> additionalDepciesTarget;


    public void setAdditionalDepciesSource(
            List<Entry<PgStatement, PgStatement>> additionalDepcies) {
        this.additionalDepciesSource = additionalDepcies;
    }

    public void setAdditionalDepciesTarget(
            List<Entry<PgStatement, PgStatement>> additionalDepcies) {
        this.additionalDepciesTarget = additionalDepcies;
    }

    public void addAdditionalDepciesSource(
            List<Entry<PgStatement, PgStatement>> additionalDepcies) {
        if (this.additionalDepciesSource == null) {
            setAdditionalDepciesSource(additionalDepcies);
        } else {
            this.additionalDepciesSource.addAll(additionalDepcies);
        }
    }

    public List<Entry<PgStatement, PgStatement>> getAdditionalDepciesSource() {
        return additionalDepciesSource;
    }

    public Differ(PgDatabase sourceDbFull, PgDatabase targetDbFull, TreeElement root,
            boolean needTwoWay, String timezone, boolean msSql, IProject proj,
            Map<String, Boolean> oneTimePrefs) {
        this.sourceDbFull = sourceDbFull;
        this.targetDbFull = targetDbFull;
        this.root = root;
        this.needTwoWay = needTwoWay;
        this.timezone = timezone;
        this.msSql = msSql;
        this.proj = proj;
        this.oneTimePrefs = oneTimePrefs;
    }

    public Differ(PgDatabase sourceDbFull, PgDatabase targetDbFull, TreeElement root,
            boolean needTwoWay, String timezone, boolean msSql, IProject proj) {
        this(sourceDbFull, targetDbFull, root, needTwoWay, timezone, msSql, proj, null);
    }

    public Job getDifferJob() {
        return new Job(Messages.differ_get_differ) {

            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    Differ.this.run(monitor);
                } catch (InvocationTargetException e) {
                    return new Status(Status.ERROR, PLUGIN_ID.THIS,
                            Messages.error_in_the_project_modifier_thread, e);
                } catch (InterruptedException e) {
                    return Status.CANCEL_STATUS;
                }
                return Status.OK_STATUS;
            }
        };
    }

    public String getDiffDirect() {
        if (diffDirect == null) {
            throw new IllegalStateException(Messages.runnable_has_not_finished);
        }
        return diffDirect;
    }

    public String getDiffReverse() {
        if (diffReverse == null) {
            throw new IllegalStateException(Messages.runnable_has_not_finished);
        }
        return diffReverse;
    }

    public String getTimezone() {
        return timezone;
    }

    @Override
    public void run(IProgressMonitor monitor) throws InvocationTargetException,
    InterruptedException {
        SubMonitor pm = SubMonitor.convert(monitor, Messages.calculating_diff, 100); // 0

        Log.log(Log.LOG_INFO, "Diff from: " + sourceDbFull.getName() //$NON-NLS-1$
        + " to: " + targetDbFull.getName()); //$NON-NLS-1$

        pm.newChild(25).subTask(Messages.differ_direct_diff); // 75
        try (Getter source = new Getter(sourceDbFull, proj, oneTimePrefs);
                Getter target = new Getter(targetDbFull, proj, oneTimePrefs)) {
            // forceUnixNewLines has no effect on diff operaiton, just pass true
            PgDiffArguments args =
                    DbSource.getPgDiffArgs(ApgdiffConsts.UTF_8, timezone, true, msSql, proj, oneTimePrefs);
            diffDirect = new PgDiff(args).diffDatabaseSchemasAdditionalDepcies(
                    root,
                    sourceDbFull, targetDbFull,
                    additionalDepciesSource, additionalDepciesTarget);

            if (needTwoWay) {
                Log.log(Log.LOG_INFO, "Diff from: " + targetDbFull.getName() //$NON-NLS-1$
                + " to: " + sourceDbFull.getName()); //$NON-NLS-1$

                pm.newChild(25).subTask(Messages.differ_reverse_diff); // 100
                diffReverse = new PgDiff(args).diffDatabaseSchemasAdditionalDepcies(
                        root.getRevertedCopy(),
                        targetDbFull, sourceDbFull,
                        additionalDepciesTarget, additionalDepciesSource);
            }
        }

        PgDiffUtils.checkCancelled(pm);
        monitor.done();
    }

    // TODO костыль, сохраняет текущие аргументы, подменяет их новыми, при закрытии возвращает старые аргументы
    private static final class Getter implements AutoCloseable {
        private final Consumer<PgDiffArguments> consumer;
        private final PgDiffArguments oldArgs;

        public Getter(PgDatabase db, IProject proj, Map<String, Boolean> oneTimePrefs) {
            oldArgs = db.getArguments();
            consumer = (db::setArguments);
            PgDiffArguments newArgs = oldArgs.copy();
            // применить параметры для генерации кода ко всем БД
            OverridablePrefs prefs = new OverridablePrefs(proj, oneTimePrefs);
            newArgs.setConcurrentlyMode(
                    prefs.getBooleanOfDbUpdatePref(DB_UPDATE_PREF.PRINT_INDEX_WITH_CONCURRENTLY));
            newArgs.setUsingTypeCastOff(
                    !prefs.getBooleanOfDbUpdatePref(DB_UPDATE_PREF.USING_ON_OFF));
            db.setArguments(newArgs);
        }

        @Override
        public void close() {
            consumer.accept(oldArgs);
        }
    }
}
