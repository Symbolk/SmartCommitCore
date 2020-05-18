package com.github.smartcommit.model.constant;

public enum GroupLabel {
  FEATURE("Add or modify feature"), // new feature
  REFACTOR("Refactor code structure"), // refactoring
  FIX("Fix bug"), // fix bugs
  OPT("Optimize code"), // optimization for existing functions

  REFORMAT("Reformat code"), // blank character changes
  DOC("Document changes"),
  CONFIG("Config files"),
  RESOURCE("Change resource files"),
  SIMILAR("Change some code systematically"), // systematic changes
  CLEAR("Clear some code"), // clear unused code

  TEST("Modify test cases or methods"),
  NONJAVA("Change non-java files"),
  OTHER("Other changes"); // trivial changes

  public String label;

  GroupLabel(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
