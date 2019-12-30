package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.ChangeType;
import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.FileType;

public class DiffHunk {
  private String repoID;
  private String repoName;
  private String fileID;
  private String diffHunkID;
  private String commitID;

  private Integer fileIndex; // the index of the diff file
  private Integer index; // the index of the diff hunk in the current file diff, start from 0
  private Hunk baseHunk;
  private Hunk currentHunk;
  private FileType fileType;
  private ChangeType changeType;
  //  private List<ActionCluster> changeActions;
  private String description;

  public DiffHunk(
      Integer index,
      FileType fileType,
      ChangeType changeType,
      Hunk baseHunk,
      Hunk currentHunk,
      String description) {
    this.index = index;
    this.fileType = fileType;
    this.baseHunk = baseHunk;
    this.currentHunk = currentHunk;
    this.changeType = changeType;
    this.description = description;
  }

  public Integer getIndex() {
    return index;
  }

  public Hunk getBaseHunk() {
    return baseHunk;
  }

  public Hunk getCurrentHunk() {
    return currentHunk;
  }
  //  public List<ActionCluster> getChangeActions() {
  //    return changeActions;
  //  }
  //
  //  public void setChangeActions(List<ActionCluster> changeActions) {
  //    this.changeActions = changeActions;
  //  }

  public String getRepoID() {
    return repoID;
  }

  public String getFileID() {
    return fileID;
  }

  public void setFileID(String fileID) {
    this.fileID = fileID;
  }

  public void setRepoID(String repoID) {
    this.repoID = repoID;
  }

  public String getRepoName() {
    return repoName;
  }

  public void setRepoName(String repoName) {
    this.repoName = repoName;
  }

  public String getDiffHunkID() {
    return diffHunkID;
  }

  public void setDiffHunkID(String diffHunkID) {
    this.diffHunkID = diffHunkID;
  }

  public String getCommitID() {
    return commitID;
  }

  public void setCommitID(String commitID) {
    this.commitID = commitID;
  }

  public Integer getBaseStartLine() {
    return baseHunk.getStartLine();
  }

  public Integer getBaseEndLine() {
    return baseHunk.getEndLine();
  }

  public Integer getCurrentStartLine() {
    return currentHunk.getStartLine();
  }

  public Integer getCurrentEndLine() {
    return currentHunk.getEndLine();
  }

  public Integer getFileIndex() {
    return fileIndex;
  }

  public void setFileIndex(Integer fileIndex) {
    this.fileIndex = fileIndex;
  }

  //  public void addCodeAction(ActionCluster actionCluster) {
  //    if (this.changeActions == null) {
  //      this.changeActions = new ArrayList<>();
  //    }
  //    this.changeActions.add(actionCluster);
  //  }

  public boolean containsCode() {
    return baseHunk.getContentType().equals(ContentType.CODE)
        || currentHunk.getContentType().equals(ContentType.CODE);
  }
}
