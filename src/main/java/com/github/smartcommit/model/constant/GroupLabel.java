package com.github.smartcommit.model.constant;

public enum GroupLabel {
  FEATURE("Feature"), // new feature
  REFACTOR("Refactor"), // refactoring
  FIX("Fix"), // fix bugs

  REFORMAT("Reformat"), // blank character changes
  DOC("Document"),
  CONFIG("Config"),
  SIMILAR("Similar"), // systematic changes
  CLEAR("Clear"), // clear unused code
  MOVING("Moving"),

  NONJAVA("Non-Java"),
  OTHER("Others"); // trivial changes

  public String label;

  GroupLabel(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
