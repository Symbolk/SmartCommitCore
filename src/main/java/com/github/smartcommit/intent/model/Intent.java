package com.github.smartcommit.intent.model;

public enum Intent {
    FIX("fix"),
    FEA("feat"),
    FMT("format"),
    OPT("optimize"),
    TST("test"),
    ADD("add"),
    RMV("remove"),
    UPD("update"),
    UPG("upgrade"),
    RFC("refactor"),
    UNKNOWN("NA");

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