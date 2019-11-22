package ru.taximaxim.codekeeper.ui.prefs;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import ru.taximaxim.codekeeper.ui.Activator;
import ru.taximaxim.codekeeper.ui.UIConsts.FORMATTER_PREF;
import ru.taximaxim.codekeeper.ui.localizations.Messages;

public class SQLEditorFormatterPrefPage extends FieldEditorPreferencePage
implements IWorkbenchPreferencePage {

    private IntegerFieldEditor indentSize;
    private IntegerFieldEditor replaceCount;

    public SQLEditorFormatterPrefPage() {
        super(GRID);
    }

    @Override
    public void init(IWorkbench workbench) {
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
    }

    @Override
    protected void createFieldEditors() {
        IPreferenceStore store = getPreferenceStore();

        addField(new ComboFieldEditor(FORMATTER_PREF.INDENT_TYPE,
                Messages.SQLEditorFormatterPrefPage_indent_type, new String[][] {
            {Messages.SQLEditorFormatterPrefPage_whitespace, FORMATTER_PREF.WHITESPACE},
            {Messages.SQLEditorFormatterPrefPage_tab, FORMATTER_PREF.TAB},
            {Messages.SQLEditorFormatterPrefPage_disable, FORMATTER_PREF.DISABLE}},
                getFieldEditorParent()));

        indentSize = new IntegerFieldEditor(FORMATTER_PREF.INDENT_SIZE,
                Messages.SQLEditorFormatterPrefPage_indent_size,
                getFieldEditorParent(), 2);

        indentSize.setEnabled(
                !store.getString(FORMATTER_PREF.INDENT_TYPE).equals(FORMATTER_PREF.DISABLE),
                getFieldEditorParent());

        addField(indentSize);

        addField(new BooleanFieldEditor(FORMATTER_PREF.REMOVE_TRAILING_WHITESPACE,
                Messages.SQLEditorFormatterPrefPage_remove_trailing_whitespace,
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(FORMATTER_PREF.ADD_WHITESPACE_BEFORE_OP,
                Messages.SQLEditorFormatterPrefPage_add_whitespace_before_operators,
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(FORMATTER_PREF.ADD_WHITESPACE_AFTER_OP,
                Messages.SQLEditorFormatterPrefPage_add_whitespace_after_operators,
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(FORMATTER_PREF.REPLACE_TAB,
                Messages.SQLEditorFormatterPrefPage_replace_tab_with_whitespaces,
                getFieldEditorParent()));

        replaceCount = new IntegerFieldEditor(FORMATTER_PREF.WHITESPACE_COUNT,
                Messages.SQLEditorFormatterPrefPage_whitespace_count,
                getFieldEditorParent(), 2);

        replaceCount.setEnabled(
                store.getBoolean(FORMATTER_PREF.REPLACE_TAB),
                getFieldEditorParent());

        addField(replaceCount);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        FieldEditor source = (FieldEditor) event.getSource();
        if (source.getPreferenceName() == FORMATTER_PREF.REPLACE_TAB) {
            replaceCount.setEnabled(((boolean) event.getNewValue()), getFieldEditorParent());
        } else if (source.getPreferenceName() == FORMATTER_PREF.INDENT_TYPE) {
            indentSize.setEnabled(!event.getNewValue().equals(FORMATTER_PREF.DISABLE), getFieldEditorParent());
        }

        super.propertyChange(event);
    }
}
