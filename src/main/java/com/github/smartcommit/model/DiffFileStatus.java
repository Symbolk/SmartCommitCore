package com.github.smartcommit.model;

/**
 * Status of a DiffFile
 */
public enum DiffFileStatus {
  UNMODIFIED(" ", "unmodified"),
  MODIFIED("M", "modified"),
  ADDED("A", "added"),
  DELETED("D", "deleted"),
  RENAMED("R", "renamed"),
  COPIED("C", "copied"),
  UNMERGED("U", "unmerged"),
  UNTRACKED("??", "untracked"),
  IGNORED("!", "ignored");

  public String symbol;
  public String label;

  DiffFileStatus(String symbol, String label) {
    this.symbol = symbol;
    this.label = label;
  }
}
