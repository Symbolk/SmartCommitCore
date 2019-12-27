package com.github.smartcommit.model.constant;

/**
 * Status of a DiffFile
 */
public enum FileStatus {
  UNMODIFIED(" ", "unmodified"),
  MODIFIED("M", "modified"),
  ADDED("A", "added"),
  DELETED("D", "deleted"),
  RENAMED("R", "renamed"),
  RENAMED2("RM", "renamed"), // RM since Git 2.23.0
  COPIED("C", "copied"),
  UNMERGED("U", "unmerged"),
  UNTRACKED("??", "untracked"),
  IGNORED("!", "ignored");

  public String symbol;
  public String label;

  FileStatus(String symbol, String label) {
    this.symbol = symbol;
    this.label = label;
  }
}
