package top.ellan.mahjong.table.core.round;

import top.ellan.mahjong.table.core.MahjongVariant;

public enum GbRuleProfile {
    GB(MahjongVariant.GB, "GB_MAHJONG", 8, true, true, false, true),
    SICHUAN(MahjongVariant.SICHUAN, "SICHUAN_MAHJONG", 0, false, false, true, false);

    private final MahjongVariant variant;
    private final String nativeRuleProfile;
    private final int minimumFan;
    private final boolean includesHonors;
    private final boolean includesFlowers;
    private final boolean useSichuanHuEvaluator;
    private final boolean allowsChii;

    GbRuleProfile(
        MahjongVariant variant,
        String nativeRuleProfile,
        int minimumFan,
        boolean includesHonors,
        boolean includesFlowers,
        boolean useSichuanHuEvaluator,
        boolean allowsChii
    ) {
        this.variant = variant;
        this.nativeRuleProfile = nativeRuleProfile;
        this.minimumFan = minimumFan;
        this.includesHonors = includesHonors;
        this.includesFlowers = includesFlowers;
        this.useSichuanHuEvaluator = useSichuanHuEvaluator;
        this.allowsChii = allowsChii;
    }

    public MahjongVariant variant() {
        return this.variant;
    }

    public String nativeRuleProfile() {
        return this.nativeRuleProfile;
    }

    public int minimumFan() {
        return this.minimumFan;
    }

    public boolean includesHonors() {
        return this.includesHonors;
    }

    public boolean includesFlowers() {
        return this.includesFlowers;
    }

    public boolean useSichuanHuEvaluator() {
        return this.useSichuanHuEvaluator;
    }

    public boolean allowsChii() {
        return this.allowsChii;
    }

    public static GbRuleProfile forVariant(MahjongVariant variant) {
        if (variant == MahjongVariant.SICHUAN) {
            return SICHUAN;
        }
        return GB;
    }
}
