package com.github.smartcommit.intent.model;

import java.util.List;

public class CommitInfo {
  private String commitID;
  private String commitMsg;
  private Intent intent;
  private String committer;
  private String committerEmail;
  private String commitTime;
  private List<MyAction> actionList;

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

  public List<MyAction> getActionList() {
    return actionList;
  }

  public void setActionList(List<MyAction> actionList) {
    this.actionList = actionList;
  }

  public void addAction(MyAction action) {
    this.actionList.add(action);
  }

  public Intent getIntent() {
    return this.intent;
  }

  public void setIntent(Intent intent) {
    this.intent = intent;
  }

}
