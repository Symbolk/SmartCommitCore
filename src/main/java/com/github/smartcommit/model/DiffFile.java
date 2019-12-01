package com.github.smartcommit.model;

public class DiffFile {
  private Integer index; // the index of the diff file in the current repo, start from 0
  private DiffFileStatus status;
  private String oldRelativePath;
  private String newRelativePath;
  private String oldContent;
  private String newContent;

  public DiffFile(
      Integer index,
      DiffFileStatus status,
      String oldRelativePath,
      String newRelativePath,
      String oldContent,
      String newContent) {
    this.index = index;
    this.status = status;
    this.oldRelativePath = oldRelativePath;
    this.newRelativePath = newRelativePath;
    this.oldContent = oldContent;
    this.newContent = newContent;
  }

  public DiffFileStatus getStatus() {
    return status;
  }

  public String getOldRelativePath() {
    return oldRelativePath;
  }

  public String getNewRelativePath() {
    return newRelativePath;
  }

  public String getOldContent() {
    return oldContent;
  }

  public String getNewContent() {
    return newContent;
  }

  public Integer getIndex() {
    return index;
  }
}
