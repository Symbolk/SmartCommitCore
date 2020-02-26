package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.GroupLabel;

import java.util.ArrayList;
import java.util.List;

/** The output result, one group for one commit */
public class Group {
  private String repoID;
  private String repoName;
  private String groupID;
  // fileID:diffHunkID
  // if fileID==diffHunkID, status is UNTRACKED, the whole file is a diff hunk
  private List<String> diffHunkIDs;

  // system recommendation
  private GroupLabel intentLabel;
  private List<String> recommendedCommitMsgs = new ArrayList<>();
  // user choice
  private String commitID = "";
  private String commitMsg = "";

  public Group(
      String repoID, String repoName, String groupID, List<String> diffHunkIDs, GroupLabel label) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.groupID = groupID;
    this.diffHunkIDs = diffHunkIDs;
    this.intentLabel = label;
  }

  public String getGroupID() {
    return groupID;
  }

  public List<String> getDiffHunkIDs() {
    return diffHunkIDs;
  }

  public GroupLabel getIntentLabel() {
    return intentLabel;
  }

  public List<String> getRecommendedCommitMsgs() {
    return recommendedCommitMsgs;
  }

  public void setRecommendedCommitMsgs(List<String> recommendedCommitMsgs) {
    this.recommendedCommitMsgs = recommendedCommitMsgs;
  }

  public void setIntentLabel(GroupLabel intentLabel) {
    this.intentLabel = intentLabel;
  }

  public String getCommitMsg() {
    return commitMsg;
  }

  public void setCommitMsg(String commitMsg) {
    this.commitMsg = commitMsg;
  }

  public void addDiffHunk(String diffID) {
    if (diffHunkIDs.contains(diffID)) {
      return;
    } else {
      diffHunkIDs.add(diffID);
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(intentLabel).append("\n");
    //    builder.append(commitMsg).append("\n");
    diffHunkIDs.forEach(diffHunk -> builder.append(diffHunk).append("\n"));
    return builder.toString();
  }
}
