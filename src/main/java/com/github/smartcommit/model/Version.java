package com.github.smartcommit.model;

public enum Version {
  OLD(0, "base"),
  NEW(1, "current");

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
