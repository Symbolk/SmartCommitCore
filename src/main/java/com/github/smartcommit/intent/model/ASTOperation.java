package com.github.smartcommit.intent.model;

public enum ASTOperation {
  ADD("Add"),
  DEL("Delete"),
  UPD("Update"),
  MOV("Move");
  public String label;

  ASTOperation(String label) {
    this.label = label;
  }
}
