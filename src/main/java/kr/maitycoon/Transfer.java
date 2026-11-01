package kr.maitycoon;

import java.io.IOException;
import java.util.UUID;

/**
 * Moves 골드 between two accounts. Two files cannot change as one, so both players get a journal entry first and a
 * failed save holds both for an admin: which side landed cannot be proven, so nothing is refunded or paid again.
 */
public final class Transfer {
    public interface Saver { void save(UUID id, Profile profile) throws IOException; }
    public enum Result { DONE, SELF, AMOUNT, FUNDS, HELD, OVERFLOW }
    private Transfer() {}
    public static Result run(TransactionJournal journal, ProfileStore store, Saver saver, UUID from, UUID to, long amount) throws IOException {
        if (from.equals(to)) return Result.SELF;
        if (amount <= 0) return Result.AMOUNT;
        if (journal.held(from) || journal.held(to)) return Result.HELD;
        Profile sender = store.load(from), receiver = store.load(to);
        if (sender.coins() < amount) return Result.FUNDS;
        if (Long.MAX_VALUE - receiver.coins() < amount) return Result.OVERFLOW;
        String detail = from + " -> " + to + " " + amount + " 골드, 보내기 전 " + sender.coins() + " / " + receiver.coins();
        String out = journal.begin(from, "transfer-out", detail), in;
        try { in = journal.begin(to, "transfer-in", detail); }
        catch (IOException e) {
            journal.finish(out);
            throw e;
        }
        try {
            saver.save(from, sender.spend(amount));
            saver.save(to, receiver.sell(amount));
        } catch (IOException e) {
            journal.hold(out);
            journal.hold(in);
            throw e;
        }
        journal.finish(out);
        journal.finish(in);
        return Result.DONE;
    }
}
