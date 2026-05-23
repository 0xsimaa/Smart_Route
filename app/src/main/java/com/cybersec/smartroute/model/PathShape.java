package com.cybersec.smartroute.model;

public enum PathShape {
    STRAIGHT,
    CURVED;

    public boolean isCurved() {
        return this == CURVED;
    }

    public static PathShape fromBool(boolean curved) {
        return curved ? CURVED : STRAIGHT;
    }
}
