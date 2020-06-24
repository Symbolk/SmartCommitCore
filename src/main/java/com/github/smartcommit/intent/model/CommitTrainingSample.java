package com.github.smartcommit.intent.model;

import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.DiffFile;

import java.util.List;

public class CommitTrainingSample {
  // raw
  private String repoID;
  private String repoPath;
  private String repoName;

  private String commitID;
  private String commitMsg;
  private String committer;
  private String committerEmail;
  private String commitTime;

  private Integer numOfDiffFiles;
  private List<DiffFile> diffFiles;

  // extraction
  private Intent intent;
  private List<IntentDescription> intentDescriptions;
  private List<AstAction> gumtreeActions;
  private List<Action> diffHunksActions;

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

  public String getCommitter() {
    return committer;
  }

  public void setCommitter(String committer) {
    this.committer = committer;
  }

  public String getCommitterEmail() {
    return committerEmail;
  }

  public void setCommitEmail(String committerEmail) {
    this.committerEmail = committerEmail;
  }

  public String getCommitTime() {
    return commitTime;
  }

  public void setCommitTime(String commitTime) {
    this.commitTime = commitTime;
  }

  public List<AstAction> getGumtreeActionList() {
    return gumtreeActions;
  }

  public void setGumtreeActionList(List<AstAction> gumtreeActionList) {
    this.gumtreeActions = gumtreeActionList;
  }

  public List<Action> getDiffHunksActions() {
    return diffHunksActions;
  }

  public void setDiffHunksActions(List<Action> actionList) {
    this.diffHunksActions = actionList;
  }

  public Intent getIntent() {
    return this.intent;
  }

  public void setIntent(Intent intent) {
    this.intent = intent;
  }

  public List<IntentDescription> getIntentDescription() {
    return intentDescriptions;
  }

  public void setIntentDescription(List<IntentDescription> intentDescription) {
    this.intentDescriptions = intentDescription;
  }

  public void addIntentDescription(IntentDescription intentDescription) {
    this.intentDescriptions.add(intentDescription);
  }

  public void setCommitterEmail(String committerEmail) {
    this.committerEmail = committerEmail;
  }

  public Integer getNumOfDiffFiles() {
    return numOfDiffFiles;
  }

  public void setNumOfDiffFiles(Integer numOfDiffFiles) {
    this.numOfDiffFiles = numOfDiffFiles;
  }

  public List<IntentDescription> getIntentDescriptions() {
    return intentDescriptions;
  }

  public void setIntentDescriptions(List<IntentDescription> intentDescriptions) {
    this.intentDescriptions = intentDescriptions;
  }

  public List<AstAction> getGumtreeActions() {
    return gumtreeActions;
  }

  public void setGumtreeActions(List<AstAction> gumtreeActions) {
    this.gumtreeActions = gumtreeActions;
  }

  public void setDiffFiles(List<DiffFile> diffFiles) {
    this.diffFiles = diffFiles;
  }

  public List<DiffFile> getDiffFiles() {
    return this.diffFiles;
  }

  public void setRepoID(String repoID) {
    this.repoID = repoID;
  }

  public void setRepoPath(String repoPath) {
    this.repoPath = repoPath;
  }

  public void setRepoName(String repoName) {
    this.repoName = repoName;
  }

  public String getRepoID() {
    return this.repoID;
  }

  public String getRepoPath() {
    return this.repoPath;
  }

  public String getRepoName() {
    return this.repoName;
  }
}
