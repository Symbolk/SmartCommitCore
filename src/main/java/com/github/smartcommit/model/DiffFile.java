package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.FileStatus;

import java.util.List;

public class DiffFile {
  private Integer index; // the index of the diff file in the current repo, start from 0
  private FileStatus status;
  private String baseRelativePath;
  private String currentRelativePath;
  private String baseContent;
  private String currentContent;
  private List<DiffHunk> diffHunks;

  public DiffFile(
      Integer index,
      FileStatus status,
      String baseRelativePath,
      String currentRelativePath,
      String baseContent,
      String currentContent) {
    this.index = index;
    this.status = status;
    this.baseRelativePath = baseRelativePath;
    this.currentRelativePath = currentRelativePath;
    this.baseContent = baseContent;
    this.currentContent = currentContent;
  }

  public FileStatus getStatus() {
    return status;
  }

  public String getBaseRelativePath() {
    return baseRelativePath;
  }

  public String getCurrentRelativePath() {
    return currentRelativePath;
  }

  public String getBaseContent() {
    return baseContent;
  }

  public String getCurrentContent() {
    return currentContent;
  }

  public Integer getIndex() {
    return index;
  }

  public List<DiffHunk> getDiffHunks() {
    return diffHunks;
  }

  public void setDiffHunks(List<DiffHunk> diffHunks) {
    this.diffHunks = diffHunks;
  }
}
