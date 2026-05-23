package com.cybersec.smartroute.model;

public enum AccelerationPattern {
    SMOOTH,
    CONSTANT,
    SUDDEN;

    public String wireName() {
        return name().toLowerCase();
    }

    public static AccelerationPattern fromWire(String name) {
        if (name == null) return SMOOTH;
        switch (name) {
            case "constant": return CONSTANT;
            case "sudden":   return SUDDEN;
            default:         return SMOOTH;
        }
    }
}
