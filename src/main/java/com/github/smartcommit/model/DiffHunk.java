package com.github.smartcommit.model;

public class DiffHunk {

  private Integer index; // the index of the diff hunk in the current file diff, start from 0
  private String oldRelativePath;
  private String newRelativePath;
  private Hunk oldHunk;
  private Hunk newHunk;
  //  private List<ActionCluster> changeActions;
  private Integer oldStartLine;
  private Integer oldEndLine;
  private Integer newStartLine;
  private Integer newEndLine;

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }

  public String getOldRelativePath() {
    return oldRelativePath;
  }

  public void setOldRelativePath(String oldRelativePath) {
    this.oldRelativePath = oldRelativePath;
  }

  public String getNewRelativePath() {
    return newRelativePath;
  }

  public void setNewRelativePath(String newRelativePath) {
    this.newRelativePath = newRelativePath;
  }

  //  public List<ActionCluster> getChangeActions() {
  //    return changeActions;
  //  }
  //
  //  public void setChangeActions(List<ActionCluster> changeActions) {
  //    this.changeActions = changeActions;
  //  }

  public Integer getOldStartLine() {
    return oldStartLine;
  }

  public void setOldStartLine(Integer oldStartLine) {
    this.oldStartLine = oldStartLine;
  }

  public Integer getOldEndLine() {
    return oldEndLine;
  }

  public void setOldEndLine(Integer oldEndLine) {
    this.oldEndLine = oldEndLine;
  }

  public Integer getNewStartLine() {
    return newStartLine;
  }

  public void setNewStartLine(Integer newStartLine) {
    this.newStartLine = newStartLine;
  }

  public Integer getNewEndLine() {
    return newEndLine;
  }

  public void setNewEndLine(Integer newEndLine) {
    this.newEndLine = newEndLine;
  }

  //  public void addCodeAction(ActionCluster actionCluster) {
  //    if (this.changeActions == null) {
  //      this.changeActions = new ArrayList<>();
  //    }
  //    this.changeActions.add(actionCluster);
  //  }
}
