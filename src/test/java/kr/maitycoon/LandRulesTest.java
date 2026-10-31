package kr.maitycoon;
import java.util.*;
import kr.maitycoon.FootprintLog.Report;
import kr.maitycoon.LandRegistry.Action;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LandRulesTest {
    private final UUID owner = UUID.randomUUID(), worker = UUID.randomUUID(), visitor = UUID.randomUUID();
    private static final String HOME = LandRegistry.key("w", -1, -1), NEXT = LandRegistry.key("w", 0, -1), WILD = LandRegistry.key("w", 5, 5);
    private LandRegistry lands() {
        var lands = new LandRegistry();
        lands.put(HOME, owner);
        lands.addWorker(owner, worker);
        return lands;
    }
    @Test void rolesFollowThePermissionTable() {
        var lands = lands();
        for (Action action : Action.values()) assertTrue(lands.allows(HOME, owner, action));
        assertTrue(lands.allows(HOME, worker, Action.HARVEST));
        assertTrue(lands.allows(HOME, worker, Action.PLANT));
        assertTrue(lands.allows(HOME, worker, Action.TILL));
        assertFalse(lands.allows(HOME, worker, Action.BUILD));
        assertFalse(lands.allows(HOME, worker, Action.USE));
        assertTrue(lands.allows(HOME, visitor, Action.HARVEST));
        for (Action action : List.of(Action.BUILD, Action.USE, Action.TILL, Action.PLANT)) assertFalse(lands.allows(HOME, visitor, action));
        for (Action action : Action.values()) assertTrue(lands.allows(WILD, visitor, action));
    }
    @Test void onlyVisitorsCommitTheft() {
        var lands = lands();
        assertTrue(lands.isTheft(HOME, visitor));
        assertFalse(lands.isTheft(HOME, owner));
        assertFalse(lands.isTheft(HOME, worker));
        assertFalse(lands.isTheft(WILD, visitor));
    }
    @Test void removedWorkersBecomeVisitors() {
        var lands = lands();
        assertTrue(lands.removeWorker(owner, worker));
        assertFalse(lands.removeWorker(owner, worker));
        assertTrue(lands.isTheft(HOME, worker));
        assertThrows(IllegalArgumentException.class, () -> lands.addWorker(owner, owner));
    }
    @Test void crossingOnlyMattersWhenEnteringOtherLand() {
        var lands = lands();
        assertTrue(lands.crossesInto(WILD, HOME));
        assertFalse(lands.crossesInto(HOME, WILD));
        assertFalse(lands.crossesInto(HOME, HOME));
        lands.put(NEXT, owner);
        assertFalse(lands.crossesInto(HOME, NEXT));
        lands.put(NEXT, visitor);
        assertTrue(lands.crossesInto(HOME, NEXT));
    }
    private static final long DAY = 86_400_000L;
    private FootprintLog.Footprint leave(FootprintLog log, long now, long tick) {
        return log.add(visitor, owner, "w", 1, 64, 2, "WHEAT", "break", now, tick);
    }
    @Test void sameEventInOneTickIsRecordedOnce() {
        var log = new FootprintLog(DAY);
        assertNotNull(leave(log, 1000, 5));
        assertNull(leave(log, 1000, 5));
        assertNotNull(log.add(visitor, owner, "w", 1, 64, 2, "WHEAT", "right-click", 1000, 5));
        assertNotNull(leave(log, 1050, 6));
        assertEquals(3, log.all().size());
    }
    @Test void reportWorksOnceForTheCurrentOwnerInsideTheWindow() {
        var log = new FootprintLog(DAY);
        String id = leave(log, 1000, 1).id();
        assertEquals(1, log.reportable(owner, 2000).size());
        assertEquals(Report.NOT_OWNER, log.report(id, visitor, owner, 2000));
        assertEquals(Report.NOT_OWNER, log.report(id, owner, visitor, 2000));
        assertEquals(Report.MISSING, log.report("missing", owner, owner, 2000));
        assertEquals(Report.OK, log.report(id, owner, owner, 2000));
        assertEquals(Report.ALREADY, log.report(id, owner, owner, 2001));
        assertTrue(log.reportable(owner, 2002).isEmpty());
    }
    @Test void expiredFootprintsCannotBeReported() {
        var log = new FootprintLog(DAY);
        String id = leave(log, 1000, 1).id();
        assertEquals(Report.EXPIRED, log.report(id, owner, owner, 1000 + DAY + 1));
        assertTrue(log.reportable(owner, 1000 + DAY + 1).isEmpty());
    }
    @Test void cannotReportYourOwnAct() {
        var log = new FootprintLog(DAY);
        String id = log.add(owner, owner, "w", 1, 64, 2, "WHEAT", "break", 1000, 1).id();
        assertEquals(Report.OWN_ACT, log.report(id, owner, owner, 2000));
    }
}
