package ru.taximaxim.codekeeper.ui.search;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.DecoratingStyledCellLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;
import org.eclipse.search.ui.text.Match;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import cz.startnet.utils.pgdiff.schema.PgObjLocation;

public class ReferenceResultPage extends AbstractTextSearchViewPage {

    private ReferenceContentProvider contentProvider;

    @Override
    protected void elementsChanged(Object[] objects) {
        contentProvider.inputChanged(getViewer(), null, getInput());
        getViewer().refresh();
    }

    @Override
    protected void clear() {
        // no impl
    }

    @Override
    protected void configureTreeViewer(TreeViewer viewer) {
        viewer.setLabelProvider(new DecoratingStyledCellLabelProvider(new ReferenceLabelProvider(),
                PlatformUI.getWorkbench().getDecoratorManager().getLabelDecorator(), null));

        contentProvider = new ReferenceContentProvider();
        viewer.setContentProvider(contentProvider);
        viewer.setComparator(new ViewerComparator() {

            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                if (e1 instanceof PgObjLocation && e2 instanceof PgObjLocation) {
                    int x = ((PgObjLocation) e1).getOffset();
                    int y = ((PgObjLocation) e2).getOffset();
                    return Integer.compare(x, y);
                }

                return super.compare(viewer, e1, e2);
            }
        });
    }

    @Override
    protected void showMatch(Match match, int offset, int length, boolean activate) throws PartInitException {
        PgObjLocation loc = (PgObjLocation) match.getElement();

        IFile file = ResourcesPlugin.getWorkspace().getRoot()
                .getFileForLocation(new Path(loc.getFilePath()));

        if (file == null) {
            return;
        }

        IWorkbenchPage page = getSite().getPage();

        if (offset >= 0 && length != 0) {
            openAndSelect(page, file, offset, length, activate);
        } else {
            open(page, file, activate);
        }
    }

    @Override
    protected void configureTableViewer(TableViewer viewer) {
        throw new IllegalStateException("Doesn't support table mode."); //$NON-NLS-1$
    }
}