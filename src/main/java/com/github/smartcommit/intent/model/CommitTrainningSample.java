package com.github.smartcommit.intent.model;

import java.util.List;

public class CommitTrainningSample {
  private String repoID;
  private String repoPath;
  private String repoName;
  private String commitID;
  private String commitMsg;
  private Intent intent;
  private String committer;
  private String committerEmail;
  private String commitTime;
  private List<Action> actions;

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

  public List<Action> getActionList() {
    return actions;
  }

  public void setActionList(List<Action> actionList) {
    this.actions = actionList;
  }

  public void addAction(Action action) {
    this.actions.add(action);
  }

  public Intent getIntent() {
    return this.intent;
  }

  public void setIntent(Intent intent) {
    this.intent = intent;
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
