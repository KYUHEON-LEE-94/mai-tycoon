package kr.maitycoon;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class TransactionJournalTest {
    @TempDir Path directory;
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    @Test void finishedTradesLeaveNothingBehind() throws IOException {
        var journal = new TransactionJournal(directory);
        journal.finish(journal.begin(alice, "sell", "WHEAT x3 +24"));
        assertTrue(new TransactionJournal(directory).interrupted().isEmpty());
    }
    @Test void anInterruptedTradeHoldsOnlyThatPlayerUntilResolved() throws IOException {
        String id = new TransactionJournal(directory).begin(alice, "sell", "WHEAT x3 +24");
        var restarted = new TransactionJournal(directory);
        assertTrue(restarted.held(alice));
        assertFalse(restarted.held(bob));
        assertEquals("WHEAT x3 +24", restarted.interrupted().iterator().next().detail());
        assertFalse(restarted.resolve("missing"));
        assertTrue(restarted.resolve(id));
        assertFalse(restarted.held(alice));
        assertTrue(new TransactionJournal(directory).interrupted().isEmpty());
    }
    @Test void aTradeStartedAfterRestartIsNotHeld() throws IOException {
        var journal = new TransactionJournal(directory);
        journal.begin(alice, "buy", "open");
        assertFalse(journal.held(alice));
    }
    @Test void rejectsDamagedEntries() throws IOException {
        Files.writeString(directory.resolve("1-bad.tx"), "not a journal entry");
        assertThrows(IOException.class, () -> new TransactionJournal(directory));
    }
}
