package ru.taximaxim.codekeeper.ui.refactoring;

import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import ru.taximaxim.codekeeper.ui.localizations.Messages;

public class PgRenameRefactoringInputPage extends UserInputWizardPage {

    public static final String PAGE_NAME = "RenameRefactoringPage"; //$NON-NLS-1$

    private Text txtName;

    public PgRenameRefactoringInputPage() {
        super(PAGE_NAME);
    }

    @Override
    public void createControl(Composite parent) {
        Composite top = new Composite(parent, SWT.NONE);
        initializeDialogUnits(top);
        setControl(top);

        top.setLayout(new GridLayout(2, false));

        Label label= new Label(top, SWT.NONE);
        label.setText(Messages.PgRenameRefactoringInputPage_new_name);
        txtName = new Text(top, SWT.BORDER);
        txtName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        txtName.addModifyListener(e ->  {
            updatePageComplete();
            getRenameProcessor().setNewName(txtName.getText());
        });
        txtName.setText(getOldName());
        txtName.selectAll();
    }

    private RenameDefinitionProcessor getRenameProcessor() {
        RenameRefactoring refactoring = (RenameRefactoring) getRefactoring();
        return (RenameDefinitionProcessor) refactoring.getProcessor();
    }

    private String getOldName() {
        return getRenameProcessor().getOldName();
    }

    private void updatePageComplete() {
        String txt = txtName.getText();
        if (txt.isEmpty() || txt.equals(getOldName())) {
            setPageComplete(false);
        } else {
            setPageComplete(true);
        }
    }
}
