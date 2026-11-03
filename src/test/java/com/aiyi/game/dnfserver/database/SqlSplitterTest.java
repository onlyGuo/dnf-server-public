package com.aiyi.game.dnfserver.database;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SqlSplitterTest {
    @Test public void splitsQuotedSemicolonsCommentsAndRoutineDelimiters() {
        List<String> statements = SqlSplitter.split("SELECT 'a;b', `x;y`; -- comment ;\nSELECT 2;\nDELIMITER $$\nCREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END$$\nDELIMITER ;\nSELECT 3;");
        assertEquals(4, statements.size());
        assertTrue(statements.get(0).contains("'a;b'"));
        assertTrue(statements.get(1).contains("SELECT 2"));
        assertTrue(statements.get(2).contains("SELECT 1; SELECT 2;"));
        assertEquals("SELECT 3", statements.get(3));
    }
    @Test public void refusesUnfinishedLiteral() {
        try { SqlSplitter.split("SELECT 'unfinished"); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
