package ru.taximaxim.codekeeper.ui.sqledit;

import java.util.Iterator;

import org.eclipse.jface.text.DefaultTextHover;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.EditorsUI;

import cz.startnet.utils.pgdiff.schema.PgObjLocation;
import ru.taximaxim.codekeeper.ui.UIConsts.MARKER;

final class SQLEditorTextHover extends DefaultTextHover implements ITextHoverExtension, ITextHoverExtension2, ITextHover {

    private static final String QUICKDIFF = "org.eclipse.ui.workbench.texteditor.quickdiff"; //$NON-NLS-1$

    private final SQLEditor editor;

    public SQLEditorTextHover(ISourceViewer sourceViewer, SQLEditor editor) {
        super(sourceViewer);
        this.editor = editor;
    }

    @Override
    public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
        PgObjLocation obj = editor.getObjectAtOffset(offset, false);
        if (obj == null) {
            return new Region(offset, 0);
        }
        return editor.getParser().getDefinitionsForObj(obj)
                .findAny()
                .map(meta -> (IRegion) new SQLEditorMyRegion(obj,  meta.getComment()))
                .orElse(new Region(offset, 0));
    }

    @Override
    public IInformationControlCreator getHoverControlCreator() {
        return parent -> new SQLEditorInformationControl(parent, EditorsUI.getTooltipAffordanceString());
    }

    @Override
    protected boolean isIncluded(Annotation annotation) {
        // exclude text change annotations
        return annotation.getText() != null && !annotation.isMarkedDeleted()
                && !annotation.getType().startsWith(QUICKDIFF);
    }

    @Override
    public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
        return null;
    }

    @Override
    public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
        IAnnotationModel model = ((ISourceViewer) textViewer).getAnnotationModel();
        if (model == null) {
            return null;
        }

        PgObjLocation pgObjLocation = null;
        String comment = null;
        if (hoverRegion instanceof SQLEditorMyRegion) {
            SQLEditorMyRegion sqlEditorMyRegion = (SQLEditorMyRegion) hoverRegion;
            pgObjLocation = sqlEditorMyRegion.getPgObjLocation();
            comment = sqlEditorMyRegion.getComment();
        }

        SQLHoverInfo sqlHoverInfo = null;
        Iterator<Annotation> iter = model.getAnnotationIterator();
        while (iter.hasNext()) {
            Annotation a = iter.next();
            if (isIncluded(a)) {
                Position p = model.getPosition(a);
                if (p != null && p.overlapsWith(hoverRegion.getOffset(),
                        hoverRegion.getLength())) {
                    sqlHoverInfo = new SQLHoverInfo(a, textViewer, pgObjLocation, comment);
                    if (!a.getType().equals(MARKER.OBJECT_OCCURRENCE)) {
                        break;
                    }
                }
            }
        }

        if (sqlHoverInfo == null && comment != null && !comment.isEmpty()) {
            sqlHoverInfo = new SQLHoverInfo(null, textViewer, pgObjLocation, comment);
        }
        return sqlHoverInfo;
    }
}
