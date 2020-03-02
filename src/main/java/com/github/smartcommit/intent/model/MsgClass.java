package com.github.smartcommit.intent.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    TST("Test");

    public String label;

    MsgClass(String label) {
        this.label = label;
    }

}
