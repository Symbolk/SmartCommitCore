package com.github.smartcommit.model.constant;

/**
 * Type of the content in hunk
 */
public enum ContentType {
    IMPORT("ImportStatement"), // pure imports
    COMMENT("Comment"), // pure comment
    CODE( "Code"), // actual code (or mixed)
    EMPTY("Empty"); // empty lines

    public String label;

    ContentType( String label) {
        this.label = label;
    }
}
