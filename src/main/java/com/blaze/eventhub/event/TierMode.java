package com.blaze.eventhub.event;

/**
 * Modo de acumulação de tiers para um tipo de ação.
 * REPLACE: tier mais alto substitui o anterior (ex: 10 votos=1e, 30 votos=5e -> 30 votos = 5 entradas total)
 * ACCUMULATE: tiers se acumulam (ex: 10 votos=1e, 30 votos=5e -> 30 votos = 6 entradas total)
 */
public enum TierMode {
    REPLACE("replace"),
    ACCUMULATE("accumulate");

    private final String value;

    TierMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TierMode fromString(String value) {
        if (value == null) {
            return REPLACE;
        }
        for (TierMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return REPLACE;
    }
}