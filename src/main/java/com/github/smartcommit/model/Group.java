package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.GroupLabel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The output result, one group for one commit */
public class Group {
  private String repoID;
  private String repoName;
  private String groupID;
  // fileIndex:diffHunkIndex
  private List<String> diffHunkIndices;
  // fileID:diffHunkID
  // if fileID==diffHunkID, status is UNTRACKED, the whole file is a diff hunk
  private List<String> diffHunkIDs;

  // system recommendation
  private GroupLabel intentLabel;
  private List<String> recommendedCommitMsgs = new ArrayList<>();
  // user choice
  private String commitID = "";
  private String commitMsg = "";

  // record link categories for interpretability
  // transient?
  private Set<Integer> linkCategories = new HashSet<>();

  public Group(
      String repoID, String repoName, String groupID, List<String> diffHunkIDs, GroupLabel label) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.groupID = groupID;
    this.diffHunkIndices = new ArrayList<>();
    this.diffHunkIDs = diffHunkIDs;
    this.intentLabel = label;
  }

  public Group(
      String repoID,
      String repoName,
      String groupID,
      List<String> diffHunkIndices,
      List<String> diffHunkIDs,
      GroupLabel label) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.groupID = groupID;
    this.diffHunkIndices = diffHunkIndices;
    this.diffHunkIDs = diffHunkIDs;
    this.intentLabel = label;
  }

  public String getGroupID() {
    return groupID;
  }

  public List<String> getDiffHunkIDs() {
    return diffHunkIDs;
  }

  public List<String> getDiffHunkIndices() {
    return diffHunkIndices;
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

  public String getCommitID() {
    return commitID;
  }

  public void setCommitID(String commitID) {
    this.commitID = commitID;
  }

  public String getCommitMsg() {
    return commitMsg;
  }

  public void setCommitMsg(String commitMsg) {
    this.commitMsg = commitMsg;
  }

  public void addByID(String diffID) {
    if (diffHunkIDs.contains(diffID)) {
      return;
    } else {
      diffHunkIDs.add(diffID);
    }
  }

  public void addByIndex(String diffIndex) {
    if (diffHunkIndices.contains(diffIndex)) {
      return;
    } else {
      diffHunkIndices.add(diffIndex);
    }
  }

  public Set<Integer> getLinkCategories() {
    return linkCategories;
  }

  public void addLinkCategories(Set<Integer> categories) {
    this.linkCategories.addAll(categories);
  }

  public void setDiffHunkIDs(List<String> diffHunkIDs) {
    this.diffHunkIDs = diffHunkIDs;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
//    builder.append(intentLabel).append("\n");
    //    builder.append(commitMsg).append("\n");
    builder.append("Changes: {");
    builder.append(String.join(", ", diffHunkIndices));
    builder.append("}\n");
    return builder.toString();
  }
}
