package com.steelquill.mcroastbot.ai;

/** Key rotation strategy. */
public enum RotationMode {
    ROUND_ROBIN,
    FAILOVER_PREFERRED;

    public static RotationMode parse(String s) {
        if (s == null) {
            return ROUND_ROBIN;
        }
        try {
            return RotationMode.valueOf(s.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ROUND_ROBIN;
        }
    }
}
