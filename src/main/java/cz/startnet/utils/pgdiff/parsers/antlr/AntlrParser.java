package cz.startnet.utils.pgdiff.parsers.antlr;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.SqlContext;

public class AntlrParser {

    public void parseInputStream(InputStream inputStream,
            String charsetName, CustomSQLParserListener listener) throws IOException {

        SQLLexer lexer = new SQLLexer(new ANTLRInputStream(new InputStreamReader(inputStream, charsetName)));
        lexer.removeErrorListeners();
        CustomErrorListener.INSTATANCE.setPath(listener.getPath());
        lexer.addErrorListener(CustomErrorListener.INSTATANCE);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        SQLParser parser = new SQLParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(CustomErrorListener.INSTATANCE);
        ParseTreeWalker walker = new ParseTreeWalker();
        SqlContext ctx = parser.sql();
        walker.walk(listener, ctx);
    }
    
}