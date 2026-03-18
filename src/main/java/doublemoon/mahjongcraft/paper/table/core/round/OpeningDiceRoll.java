package doublemoon.mahjongcraft.paper.table.core.round;

import java.util.concurrent.ThreadLocalRandom;

public record OpeningDiceRoll(int firstDie, int secondDie) {
    public OpeningDiceRoll {
        if (firstDie < 1 || firstDie > 6 || secondDie < 1 || secondDie > 6) {
            throw new IllegalArgumentException("Dice values must be between 1 and 6");
        }
    }

    public int total() {
        return this.firstDie + this.secondDie;
    }

    public static OpeningDiceRoll random() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new OpeningDiceRoll(random.nextInt(1, 7), random.nextInt(1, 7));
    }
}
