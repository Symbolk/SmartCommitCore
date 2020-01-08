package com.github.smartcommit.model.diffgraph;

public enum DiffEdgeType {
  HARD(true, "hard"),
  NONJAVA(false, "nonjava"),
  OTHERS(false, "others"),
  SOFT(false, "soft");

  Boolean isConstraint;
  String label;

  DiffEdgeType(Boolean isConstraint, String label) {
    this.isConstraint = isConstraint;
    this.label = label;
  }

  public String asString() {
    return this.label;
  }

  public Boolean isConstraint() {
    return this.isConstraint;
  }
}
