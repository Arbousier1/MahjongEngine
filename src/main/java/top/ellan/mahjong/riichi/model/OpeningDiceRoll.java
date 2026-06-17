package top.ellan.mahjong.riichi.model;

import java.util.concurrent.ThreadLocalRandom;

public record OpeningDiceRoll(int firstDie, int secondDie, int firstDie2, int secondDie2) {
    public OpeningDiceRoll {
        if (firstDie < 1 || firstDie > 6 || secondDie < 1 || secondDie > 6
            || firstDie2 < 1 || firstDie2 > 6 || secondDie2 < 1 || secondDie2 > 6) {
            throw new IllegalArgumentException("Dice values must be between 1 and 6");
        }
    }

    public OpeningDiceRoll(int firstDie, int secondDie) {
        this(firstDie, secondDie, firstDie, secondDie);
    }

    public int total() {
        return this.firstDie + this.secondDie;
    }

    public int total2() {
        return this.firstDie2 + this.secondDie2;
    }

    public static OpeningDiceRoll random() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new OpeningDiceRoll(random.nextInt(1, 7), random.nextInt(1, 7), random.nextInt(1, 7), random.nextInt(1, 7));
    }
}
