package com.github.smartcommit.model;

public class DiffHunk {

  private Integer index; // the index of the diff hunk in the current file diff, start from 0
  private Hunk baseHunk;
  private Hunk currentHunk;
  //  private List<ActionCluster> changeActions;
  private String description;

  public DiffHunk(Integer index, Hunk baseHunk, Hunk currentHunk, String description) {
    this.index = index;
    this.baseHunk = baseHunk;
    this.currentHunk = currentHunk;
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

  //  public void addCodeAction(ActionCluster actionCluster) {
  //    if (this.changeActions == null) {
  //      this.changeActions = new ArrayList<>();
  //    }
  //    this.changeActions.add(actionCluster);
  //  }
}
