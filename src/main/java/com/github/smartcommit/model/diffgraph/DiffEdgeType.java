package com.github.smartcommit.model.diffgraph;

public enum DiffEdgeType {
  DEPEND(true, "dependency"),
  REFACTOR(true, "refactor"),
  SIMILAR(false, "similar"),
  CLOSE(false, "close"),

  MOVING(true, "moving"),
  REFORMAT(true, "reformat"),
  DOC(false, "doc"),
  CONFIG(false, "config"),
  OTHERS(false, "others");

  Boolean fixed;
  String label;

  DiffEdgeType(Boolean fixed, String label) {
    this.fixed = fixed;
    this.label = label;
  }

  public String asString() {
    return this.label;
  }

  public Boolean isConstraint() {
    return this.fixed;
  }
}
