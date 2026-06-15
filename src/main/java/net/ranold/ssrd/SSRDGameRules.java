package net.ranold.ssrd;

import net.minecraft.world.level.GameRules;

public class SSRDGameRules {
    public static GameRules.Key<GameRules.IntegerValue> RULE_SSRD_FORCELOAD_LIMIT;

    public static void register() {
        RULE_SSRD_FORCELOAD_LIMIT = GameRules.register("ssrdForceloadLimit", GameRules.Category.MISC, GameRules.IntegerValue.create(2));
    }
}
