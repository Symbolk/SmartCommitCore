package com.github.smartcommit.model;

public enum Side {
  OLD(0, "old"),
  NEW(1, "new");

  private int index;
  private String label;

  Side(int index, String label) {
    this.index = index;
    this.label = label;
  }

  public String asString() {
    return label;
  }
}
