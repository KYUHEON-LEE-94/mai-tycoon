package kr.maitycoon;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TractorTest {
    @Test void coversTheWholeSquareOnceNearestFirst() {
        List<int[]> offsets = Tractors.offsets(4);
        assertEquals(81, offsets.size());
        assertEquals(81, offsets.stream().map(Arrays::toString).distinct().count());
        assertArrayEquals(new int[]{0, 0}, offsets.get(0));
        for (int i = 1; i < offsets.size(); i++) {
            int[] a = offsets.get(i - 1), b = offsets.get(i);
            assertTrue(a[0] * a[0] + a[1] * a[1] <= b[0] * b[0] + b[1] * b[1]);
        }
    }
    @Test void boughtOnceWithZcoin() {
        Profile rich = Profile.empty().claimReward(10, new Dex.Reward(0, 250, false));
        Profile owner = rich.buyTractor(200);
        assertTrue(owner.tractor());
        assertEquals(50, owner.zcoin());
        assertThrows(IllegalArgumentException.class, () -> owner.buyTractor(200));
        assertThrows(IllegalArgumentException.class, () -> Profile.empty().buyTractor(200));
        assertTrue(owner.claimStarter().addLamps(1).sell(5).tractor());
    }
}
