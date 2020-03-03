package com.github.smartcommit.intent.model;

public enum Intent {
    FEA("featur"),
    FIX("fix"),
    DOC("docs"),
    RFM("reformat"),
    RFT("refactor"),
    OPT("optimiz"),
    TST("test"),
    CHR("chore"),
    FIL("file");


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