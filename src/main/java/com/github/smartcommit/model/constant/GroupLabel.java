package com.github.smartcommit.model.constant;

public enum GroupLabel {
  NONJAVA("Non-Java"),
  FEATURE("Feature"), // new feature
  REFORMAT("Reformat"), // moving, whitespace changes
  REFACTOR("Refactor"), // currently support types: renaming method/field/type
  DOC("Document"), // comment, javadoc only changes
  FIX("Fix"), // fix bugs

  LINKED("Dependent"), // syntatically or semantically dependent
  SIMILAR("Similar"), // systematic changes
  CLEAR("Clear"), // clear unused code
  OTHER("Others");

  public String label;

  GroupLabel(String label) {
    this.label = label;
  }
}
