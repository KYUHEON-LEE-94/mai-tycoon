package kr.maitycoon;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import kr.maitycoon.Transfer.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TransferTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    @TempDir Path directory;
    private ProfileStore store;
    private TransactionJournal journal;
    @BeforeEach void setUp() throws IOException {
        store = new ProfileStore(directory.resolve("players"));
        journal = new TransactionJournal(directory.resolve("journal"));
        store.save(A, Profile.empty().sell(1000));
        store.save(B, Profile.empty().sell(50));
    }
    private Result send(UUID from, UUID to, long amount) throws IOException { return Transfer.run(journal, store, store::save, from, to, amount); }
    private long total() throws IOException { return store.load(A).coins() + store.load(B).coins(); }
    private List<String> open() throws IOException {
        try (var files = Files.list(directory.resolve("journal"))) { return files.map(path -> path.getFileName().toString()).toList(); }
    }
    @Test void movesTheAmountAndLeavesNoRecordOpen() throws IOException {
        assertEquals(Result.DONE, send(A, B, 300));
        assertEquals(700, store.load(A).coins());
        assertEquals(350, store.load(B).coins());
        assertEquals(1050, total());
        assertTrue(open().isEmpty());
    }
    @Test void theWholeBalanceCanBeSent() throws IOException {
        assertEquals(Result.DONE, send(A, B, 1000));
        assertEquals(0, store.load(A).coins());
    }
    @Test void refusalsChangeNothing() throws IOException {
        assertEquals(Result.SELF, send(A, A, 1));
        assertEquals(Result.AMOUNT, send(A, B, 0));
        assertEquals(Result.AMOUNT, send(A, B, -5));
        assertEquals(Result.FUNDS, send(A, B, 1001));
        store.save(B, Profile.empty().sell(Long.MAX_VALUE - 1));
        assertEquals(Result.OVERFLOW, send(A, B, 2));
        assertEquals(1000, store.load(A).coins());
        assertTrue(open().isEmpty());
    }
    @Test void aPlayerHeldForReviewCanNeitherSendNorReceive() throws IOException {
        journal.hold(journal.begin(B, "sell", "stopped"));
        assertEquals(Result.HELD, send(A, B, 1));
        assertEquals(Result.HELD, send(B, A, 1));
        assertEquals(1000, store.load(A).coins());
    }
    @Test void aFailedSaveHoldsBothAccountsInsteadOfGuessing() throws IOException {
        int[] saves = {0};
        Transfer.Saver second = (id, profile) -> {
            if (++saves[0] == 2) throw new IOException("disk full");
            store.save(id, profile);
        };
        assertThrows(IOException.class, () -> Transfer.run(journal, store, second, A, B, 300));
        assertTrue(journal.held(A) && journal.held(B));
        assertEquals(2, open().size());
        assertEquals(50, store.load(B).coins());
        assertEquals(Result.HELD, send(A, B, 1));
        var restarted = new TransactionJournal(directory.resolve("journal"));
        assertTrue(restarted.held(A) && restarted.held(B));
        assertTrue(restarted.interrupted().iterator().next().detail().contains("300"));
    }
    @Test void aFailedLoadLeavesNoRecord() throws IOException {
        Files.writeString(directory.resolve("players").resolve(B + ".yml"), "coins: [broken");
        assertThrows(IOException.class, () -> send(A, B, 1));
        assertTrue(open().isEmpty());
    }
}
