package com.github.smartcommit.model.mergebot;

import com.github.smartcommit.model.DiffHunk;

import java.util.List;

public class Diff {
  private String repoID;
  private String repoName;
  private String fileID;
  private String basePath;
  private String currentPath;
  private String status;
  private List<DiffHunk> diffHunks;

  public Diff(
      String repoID,
      String repoName,
      String fileID,
      String basePath,
      String currentPath,
      String status,
      List<DiffHunk> diffHunks) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.fileID = fileID;
    this.basePath = basePath;
    this.currentPath = currentPath;
    this.status = status;
    this.diffHunks = diffHunks;
  }
}
