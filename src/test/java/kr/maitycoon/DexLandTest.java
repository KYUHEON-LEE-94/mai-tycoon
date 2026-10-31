package kr.maitycoon;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DexLandTest {
    private static Dex dex() {
        return new Dex(Map.of("농사", List.of("WHEAT", "CARROT", "POTATO")), Map.of(
            1, new Dex.Reward(100, false), 2, new Dex.Reward(0, true)));
    }
    @Test void registeringGrantsEachRewardOnce() {
        Dex dex = dex();
        Profile first = dex.register(Profile.empty(), "WHEAT");
        assertEquals(100, first.coins());
        assertFalse(first.landPermit());
        Profile second = dex.register(first, "CARROT");
        assertEquals(100, second.coins());
        assertTrue(second.landPermit());
        assertEquals(Set.of(1, 2), second.dexRewards());
        Profile third = dex.register(second, "POTATO");
        assertEquals(100, third.coins());
    }
    @Test void rejectsDuplicateAndUnknownEntries() {
        Dex dex = dex();
        Profile first = dex.register(Profile.empty(), "WHEAT");
        assertThrows(IllegalArgumentException.class, () -> dex.register(first, "WHEAT"));
        assertThrows(IllegalArgumentException.class, () -> dex.register(first, "DIRT"));
        assertThrows(IllegalArgumentException.class, () -> new Dex(Map.of("a", List.of("X"), "b", List.of("X")), Map.of()));
    }
    @Test void starterKeepsCollectionData() {
        Profile profile = dex().register(Profile.empty(), "WHEAT").claimStarter();
        assertTrue(profile.starterClaimed());
        assertEquals(Set.of("WHEAT"), profile.dex());
    }
    @Test void landLimitAndOwnership() {
        var lands = new LandRegistry();
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        String a = LandRegistry.key("world", 0, 0), b = LandRegistry.key("world", 1, 0);
        assertEquals(LandRegistry.Claim.OK, lands.check(a, alice, 1));
        lands.put(a, alice);
        assertEquals(LandRegistry.Claim.TAKEN, lands.check(a, bob, 1));
        assertEquals(LandRegistry.Claim.LIMIT, lands.check(b, alice, 1));
        assertEquals(LandRegistry.Claim.OK, lands.check(b, bob, 1));
        assertEquals(1, lands.count(alice));
        assertEquals(7500, LandRegistry.price(5000, 2500, 1));
    }
}
