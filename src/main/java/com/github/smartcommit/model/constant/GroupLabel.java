package com.github.smartcommit.model.constant;

public enum GroupLabel {
  FEATURE("Feature"), // new feature
  REFACTOR("Refactor"), // refactoring
  FIX("Fix"), // fix bugs

  REFORMAT("Reformat code"), // blank character changes
  DOC("Update document"),
  CONFIG("Change config file"),
  RESOURCE("Change resource file"),
  SIMILAR("Apply some similar changes"), // systematic changes
  CLEAR("Clear unused code"), // clear dead code or comment

  TEST("Modify test cases or tested methods"),
  NONJAVA("Modify non-java file"),
  OTHER("Other changes"); // trivial changes

  public String label;

  GroupLabel(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
