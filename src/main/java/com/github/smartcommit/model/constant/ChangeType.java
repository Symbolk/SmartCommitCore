package com.github.smartcommit.model.constant;

/**
 * Change type of a DiffHunk
 */
public enum ChangeType {
  MODIFIED("M", "Modify"),
  ADDED("A", "Add"),
  DELETED("D", "Delete");

  public String symbol;
  public String label;

  ChangeType(String symbol, String label) {
    this.symbol = symbol;
    this.label = label;
  }
}
