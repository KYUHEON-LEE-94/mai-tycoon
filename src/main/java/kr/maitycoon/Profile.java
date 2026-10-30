package kr.maitycoon;

public record Profile(long coins, long experience, boolean starterClaimed) {
    public Profile {
        if (coins < 0 || experience < 0) throw new IllegalArgumentException("Negative profile value");
    }
    public static Profile empty() { return new Profile(0, 0, false); }
    public Profile sell(long income) {
        if (income <= 0) throw new IllegalArgumentException("Invalid sale");
        return new Profile(Math.addExact(coins, income), experience, starterClaimed);
    }
    public Profile spend(long cost) {
        if (cost <= 0) throw new IllegalArgumentException("Invalid purchase");
        return new Profile(coins - cost, experience, starterClaimed);
    }
}
