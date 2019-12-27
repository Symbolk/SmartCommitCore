package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.model.constant.FileType;

import java.util.ArrayList;
import java.util.List;

public class DiffFile {
  private String repoID;
  private String repoName;
  private String fileID;

  private Integer index; // the index of the diff file in the current repo, start from 0
  private FileStatus status;
  private FileType fileType;
  private String baseRelativePath;
  private String currentRelativePath;
  private String baseContent;
  private String currentContent;
  private List<DiffHunk> diffHunks;

  public DiffFile(
      Integer index,
      FileStatus status,
      FileType fileType,
      String baseRelativePath,
      String currentRelativePath,
      String baseContent,
      String currentContent) {
    this.index = index;
    this.status = status;
    this.fileType = fileType;
    this.baseRelativePath = baseRelativePath;
    this.currentRelativePath = currentRelativePath;
    this.baseContent = baseContent;
    this.currentContent = currentContent;
    this.diffHunks = new ArrayList<>();
  }

  public DiffFile(
      String repoID,
      String repoName,
      String fileID,
      Integer index,
      FileStatus status,
      FileType fileType,
      String baseRelativePath,
      String currentRelativePath,
      String baseContent,
      String currentContent,
      List<DiffHunk> diffHunks) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.fileID = fileID;
    this.index = index;
    this.status = status;
    this.fileType = fileType;
    this.baseRelativePath = baseRelativePath;
    this.currentRelativePath = currentRelativePath;
    this.baseContent = baseContent;
    this.currentContent = currentContent;
    this.diffHunks = diffHunks;
  }

  public String getRepoID() {
    return repoID;
  }

  public String getRepoName() {
    return repoName;
  }

  public String getFileID() {
    return fileID;
  }

  public void setRepoID(String repoID) {
    this.repoID = repoID;
  }

  public void setRepoName(String repoName) {
    this.repoName = repoName;
  }

  public void setFileID(String fileID) {
    this.fileID = fileID;
  }

  public FileStatus getStatus() {
    return status;
  }

  public FileType getFileType() {
    return fileType;
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

  /**
   * Clone the object with non-needed field left empty
   * @return
   */
  public DiffFile shallowClone() {
    return new DiffFile(
        repoID,
        repoName,
        fileID,
        index,
        status,
        fileType,
        baseRelativePath,
        currentRelativePath,
        "",
        "",
        diffHunks);
  }
}
