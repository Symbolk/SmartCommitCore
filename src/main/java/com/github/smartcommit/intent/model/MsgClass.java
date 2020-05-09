package com.github.smartcommit.intent.model;

/**
 * Classification of commit message
 */
public enum MsgClass {
    FEAT("Feature"),
    FIX("Fix"),
    CHR("Chore");

    public String label;

    MsgClass(String label) {
        this.label = label;
    }

}
