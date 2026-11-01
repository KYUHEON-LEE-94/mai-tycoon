package kr.maitycoon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import kr.maitycoon.WalletLog.Line;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WalletLogTest {
    private static final UUID ID = new UUID(0, 1);
    @TempDir Path directory;
    @Test void newestComesFirstAndAccountsStayApart() throws IOException {
        var log = new WalletLog(directory);
        log.add(ID, new Line(1, "sell", "골드", 0, 10));
        log.add(ID, new Line(2, "buy", "골드", 10, 4));
        log.add(new UUID(0, 2), new Line(3, "sell", "골드", 0, 1));
        assertEquals(List.of(new Line(2, "buy", "골드", 10, 4), new Line(1, "sell", "골드", 0, 10)), log.recent(ID, 9));
        assertEquals(-6, log.recent(ID, 1).get(0).change());
    }
    @Test void anAccountWithoutHistoryIsEmpty() throws IOException { assertTrue(new WalletLog(directory).recent(ID, 9).isEmpty()); }
    @Test void damagedLinesAreSkipped() throws IOException {
        var log = new WalletLog(directory);
        log.add(ID, new Line(1, "sell", "골드", 0, 10));
        Files.writeString(directory.resolve(ID + ".log"), "broken\nx|sell|골드|1|2\n", StandardOpenOption.APPEND);
        assertEquals(1, log.recent(ID, 9).size());
    }
    @Test void theFileIsTrimmed() throws IOException {
        var log = new WalletLog(directory);
        for (int i = 0; i < 250; i++) log.add(ID, new Line(i, "sell", "골드", i, i + 1));
        assertTrue(Files.readAllLines(directory.resolve(ID + ".log")).size() < 200);
        assertEquals(249, log.recent(ID, 1).get(0).at());
    }
}
