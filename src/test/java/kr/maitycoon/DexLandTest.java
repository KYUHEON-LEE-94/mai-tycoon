package kr.maitycoon;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DexLandTest {
    private static Dex dex() {
        return new Dex(Map.of("농사", List.of("WHEAT", "CARROT", "POTATO")), Map.of(
            1, new Dex.Reward(100, 1, false), 2, new Dex.Reward(0, 5, true)));
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
    @Test void rewardCurrenciesStaySeparate() {
        Dex dex = dex();
        Profile second = dex.register(dex.register(Profile.empty(), "WHEAT"), "CARROT");
        assertEquals(100, second.coins());
        assertEquals(6, second.zcoin());
        assertThrows(IllegalArgumentException.class, () -> second.spend(101));
        assertEquals("즈코인 5개 + 땅 구매 이용권", new Dex.Reward(0, 5, true).describe());
    }
    @Test void progressSurvivesAddingEntries() {
        Profile old = dex().register(dex().register(Profile.empty(), "WHEAT"), "CARROT");
        Dex bigger = new Dex(Map.of("농사", List.of("WHEAT", "CARROT", "POTATO", "BEETROOT")), Map.of(
            1, new Dex.Reward(100, 1, false), 2, new Dex.Reward(0, 5, true), 3, new Dex.Reward(7, 0, false)));
        Profile next = bigger.register(old, "POTATO");
        assertEquals(107, next.coins());
        assertEquals(6, next.zcoin());
        assertEquals(Set.of(1, 2, 3), next.dexRewards());
    }
    @Test void oldProfileFilesLoadWithoutZcoin(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        UUID id = UUID.randomUUID();
        java.nio.file.Files.writeString(directory.resolve(id + ".yml"), "coins: 70\nexperience: 0\nstarter-claimed: true\ndex: [WHEAT]\ndex-rewards: [10]\nland-permit: true\n");
        var store = new ProfileStore(directory);
        Profile loaded = store.load(id);
        assertEquals(70, loaded.coins());
        assertEquals(0, loaded.zcoin());
        assertEquals(Set.of("WHEAT"), loaded.dex());
        assertEquals(Set.of(10), loaded.dexRewards());
        store.save(id, loaded.claimStarter());
        assertEquals(loaded, store.load(id));
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
