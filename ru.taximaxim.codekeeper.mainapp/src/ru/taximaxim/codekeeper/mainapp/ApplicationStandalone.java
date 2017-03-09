package ru.taximaxim.codekeeper.mainapp;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import cz.startnet.utils.pgdiff.Main;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;

/**
 * This class controls all aspects of the application's execution
 */
public class ApplicationStandalone implements IApplication {

    @Override
    public Object start(IApplicationContext context) throws Exception {
        callApgdiffMain(Platform.getApplicationArgs());
        return IApplication.EXIT_OK;
    }

    private void callApgdiffMain(String[] pgCommands) {
        try {
            Main.main(pgCommands);
        } catch (Exception e) {
            Status error = new Status(IStatus.ERROR, ApgdiffConsts.APGDIFF_PLUGIN_ID,
                    "Calling pgcodekeeper error", e);
            Platform.getLog(Activator.getDefault().getBundle()).log(error);
        }
    }

    @Override
    public void stop() {
        //don't need stop
    }
}
