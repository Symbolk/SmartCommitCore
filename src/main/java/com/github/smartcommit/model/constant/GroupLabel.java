package com.github.smartcommit.model.constant;

public enum GroupLabel {
  FEATURE("Add or modify feature"), // new feature
  REFACTOR("Refactor code structure"), // refactoring
  FIX("Fix bug"), // fix bugs
  OPT("Optimize code"), // optimization for existing functions

  REFORMAT("Reformat code"), // blank/special character changes
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
