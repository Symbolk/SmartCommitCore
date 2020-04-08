package com.github.smartcommit.intent.model;

/**
 * Classification of commit message
 */
public enum MsgClass {
    ADD("Add"),
    CRT("Create"),
    IMP("Implement"),
    MAK("Make"),
    UPD("Update"),
    USE("Use"),
    IMV("Improve"),
    UPG("Upgrade"),
    RMV("Remove"),
    FIX("Fix"),
    TST("Test"),
    MDF("Modify");

    public String label;

    MsgClass(String label) {
        this.label = label;
    }

}
