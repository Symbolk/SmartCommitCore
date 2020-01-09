package com.github.smartcommit.model;

import java.util.List;

/** The output result, one group for one commit */
public class Group {
  private String repoID;
  private String repoName;
  private String groupID;
  private String commitID;

  // fileID:diffHunkID
  // if fileID==diffHunkID, status is UNTRACKED, the whole file is a diff hunk
  private List<String> diffHunks;
  private String commitMsg;
  private String templateCommitMsg;
  private String intentLabel;

  public Group(String repoID, String repoName, String groupID, List<String> diffHunks) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.groupID = groupID;
    this.commitID = "";
    this.diffHunks = diffHunks;
    this.commitMsg = "";
    this.templateCommitMsg = "";
    this.intentLabel = "";
  }

  public List<String> getDiffHunks() {
    return diffHunks;
  }

  public void addDiffHunk(String diffID) {
    if (diffHunks.contains(diffID)) {
      return;
    } else {
      diffHunks.add(diffID);
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(commitMsg).append("\n");
    diffHunks.forEach(diffHunk -> builder.append(diffHunk).append("\n"));
    return builder.toString();
  }
}
