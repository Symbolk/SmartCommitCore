package com.github.smartcommit.model.constant;

public enum Version {
  BASE(0, "base"),
  CURRENT(1, "current");

  private int index;
  private String label;

  Version(int index, String label) {
    this.index = index;
    this.label = label;
  }

  public String asString() {
    return label;
  }
}
