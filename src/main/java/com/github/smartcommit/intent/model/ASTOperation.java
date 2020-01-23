package com.github.smartcommit.intent.model;

public enum ASTOperation {
  ADD("add"),
  DEL("del"),
  UPD("update"),
  MOV("move");
  public String label;

  ASTOperation(String label) {
    this.label = label;
  }
}
