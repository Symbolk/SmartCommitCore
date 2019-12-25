package com.github.smartcommit.intent.model;

public enum ActionType {
  ADD("add"),
  DEL("del"),
  UPD("update"),
  MOV("move");
  public String label;

  ActionType(String label) {
    this.label = label;
  }
}
