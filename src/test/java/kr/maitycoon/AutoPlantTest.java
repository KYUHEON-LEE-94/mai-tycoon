package kr.maitycoon;
import kr.maitycoon.AutoPlanting.Plan;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AutoPlantTest {
    @Test void planCoversEverySeedAndUseCombination() {
        assertEquals(Plan.OFF, Plan.of(false, 5, true));
        assertEquals(Plan.NO_USES, Plan.of(true, 0, true));
        assertEquals(Plan.NO_SEED, Plan.of(true, 1, false));
        assertEquals(Plan.REPLANT, Plan.of(true, 1, true));
    }
    @Test void buyingSpendsZcoinOnlyAndEnablesIt() {
        Profile profile = new Profile(50, 0, false).addLamps(0);
        assertThrows(IllegalArgumentException.class, () -> profile.buyReplants(1, 1000));
        var reward = new Dex.Reward(0, 3, false);
        Profile rich = profile.claimReward(10, reward).buyReplants(1, 1000);
        assertEquals(2, rich.zcoin());
        assertEquals(50, rich.coins());
        assertEquals(1000, rich.replants());
        assertTrue(rich.autoPlant());
    }
    @Test void usesCannotGoNegative() {
        assertThrows(IllegalArgumentException.class, () -> Profile.empty().useReplant());
        assertEquals(1, Profile.empty().addReplants(2).useReplant().replants());
    }
    @Test void togglingKeepsTheUses() {
        Profile on = Profile.empty().addReplants(5).toggleAutoPlant();
        assertTrue(on.autoPlant());
        assertFalse(on.toggleAutoPlant().autoPlant());
        assertEquals(5, on.toggleAutoPlant().replants());
    }
}
