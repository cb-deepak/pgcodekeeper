package ru.taximaxim.codekeeper.ui.sqledit;

import java.util.stream.Stream;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import cz.startnet.utils.pgdiff.schema.PgObjLocation;
import cz.startnet.utils.pgdiff.schema.PgObjLocation.LocationType;
import ru.taximaxim.codekeeper.ui.pgdbproject.parser.PgDbParser;

public class SQLEditorHyperLinkDetector extends AbstractHyperlinkDetector {

    @Override
    public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
            boolean canShowMultipleHyperlinks) {
        SQLEditor editor = getAdapter(SQLEditor.class);
        PgDbParser parser = editor.getParser();

        int offset = region.getOffset();
        Stream<IHyperlink> links = Stream.empty();

        for (PgObjLocation obj : parser.getObjsForEditor(editor.getEditorInput())) {
            if (offset >= obj.getOffset()
                    && offset < (obj.getOffset() + obj.getObjLength())) {
                Stream<IHyperlink> stream = parser.getAllObjReferences()
                        .filter(obj::compare)
                        .filter(def -> {
                            LocationType type = def.getLocationType();
                            if (type == LocationType.DEFINITION) {
                                return true;
                            }
                            if (type == LocationType.VARIABLE) {
                                // search only on current file
                                return def.getFilePath().equals(obj.getFilePath());
                            }

                            return false;
                        })
                        .map(def -> new SQLEditorHyperLink(
                                new Region(def.getOffset(), def.getObjLength()),
                                new Region(obj.getOffset(), obj.getObjLength()),
                                obj.getObjName(), def.getFilePath(),
                                def.getLineNumber(), editor.isMsSql()));
                links = Stream.concat(links, stream);
            }
        }

        IHyperlink[] result = links.toArray(IHyperlink[]::new);
        return result.length == 0 ? null : result;
    }
}