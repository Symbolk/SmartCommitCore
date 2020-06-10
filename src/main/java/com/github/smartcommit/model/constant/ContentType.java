package com.github.smartcommit.model.constant;

/** Type of the content in hunk */
public enum ContentType {
  IMPORT("ImportStatement"), // pure imports
  COMMENT("Comment"), // pure comment
  CODE("Code"), // actual code (or mixed)
  BLANKLINE("BlankLine"), // blank lines
  EMPTY("Empty"), // added/deleted
  BINARY("Binary"); // binary content

  public String label;

  ContentType(String label) {
    this.label = label;
  }
}
