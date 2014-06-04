 
package ru.taximaxim.codekeeper.ui.handlers;

import java.lang.reflect.InvocationTargetException;

import javax.inject.Named;

import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;

import ru.taximaxim.codekeeper.ui.ExceptionNotifier;
import ru.taximaxim.codekeeper.ui.Log;
import ru.taximaxim.codekeeper.ui.UIConsts;
import ru.taximaxim.codekeeper.ui.pgdbproject.DiffWizard;
import ru.taximaxim.codekeeper.ui.pgdbproject.PgDbProject;

public class Diff {

    @Execute
    private void execute(
            PgDbProject proj,
            @Named(IServiceConstants.ACTIVE_SHELL)
            Shell shell,
            @Named(UIConsts.PREF_STORE)
            IPreferenceStore prefStore){
        try {
            if(ProjSyncSrc.sync(proj, shell, prefStore)) {
                Log.log(Log.LOG_DEBUG, "Diff wizard about to show");
                
                WizardDialog dialog = new WizardDialog(
                        shell, new DiffWizard(proj, prefStore));
                dialog.open();
            }
        } catch (InvocationTargetException e) {
            ExceptionNotifier.notify(e, "Could not syncronize repository with remote",
                    shell, true, true);
        }
    }
    
    @CanExecute
    private boolean canExecute(PgDbProject proj) {
        return proj != null;
    }
}