package com.cybersec.smartroute.model;

public enum SpoofMode {
    STATIC_LOCATION,
    DYNAMIC_PATH;

    public String wireName() {
        return this == STATIC_LOCATION ? "staticLocation" : "dynamicPath";
    }

    public static SpoofMode fromWire(String name) {
        return "staticLocation".equals(name) ? STATIC_LOCATION : DYNAMIC_PATH;
    }
}
