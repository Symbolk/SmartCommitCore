package com.github.smartcommit.model.constant;

/**
 * Change type of a DiffHunk
 */
public enum ChangeType {
  MODIFIED("M", "modified"),
  ADDED("A", "added"),
  DELETED("D", "deleted");

  public String symbol;
  public String label;

  ChangeType(String symbol, String label) {
    this.symbol = symbol;
    this.label = label;
  }
}
