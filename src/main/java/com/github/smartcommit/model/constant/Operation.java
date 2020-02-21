package com.github.smartcommit.model.constant;

/** Action operation on AST or Refactoring */
public enum Operation {
  // AST operations
  ADD("Add"),
  DEL("Delete"),
  UPD("Update"),
  MOV("Move"),

  // Refactoring operations
  EXTRACT("Extract");

  public String label;

  Operation(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
