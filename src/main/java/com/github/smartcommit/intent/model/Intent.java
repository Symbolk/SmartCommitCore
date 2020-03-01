package com.github.smartcommit.intent.model;

public enum Intent {
    ADD("Add"),
    CRT("Create"),
    IMP("Implement"),
    MAK("Make"),
    UPD("Update"),
    USE("Use"),
    SET("Set"),
    HDL("Handle"),
    IMV("Improve"),
    OPT("Optimize"),
    UPG("Upgrade"),
    RMV("Remove"),
    RFC("Refactor"),
    REP("Replace"),
    MOV("Move"),
    CHG("Change"),
    RNM("Rename"),
    DOC("Document"),
    RFM("Reformat"),
    FIX("Fix"),
    RVT("Revert"),
    TST("Test"),
    UNKNOWN("unknown");


    public String label;

    Intent(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }

}