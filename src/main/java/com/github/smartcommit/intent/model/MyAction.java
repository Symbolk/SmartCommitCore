package com.github.smartcommit.intent.model;

public class MyAction {
  private ActionType type;
  private String targetNodeType;

  public MyAction(ActionType type, String targetNodeType) {
    this.type = type;
    this.targetNodeType = targetNodeType;
  }
}
