package com.github.smartcommit.model.constant;

/** Action operation on AST or Refactoring */
public enum Operation {
  // AST operations
  ADD("Add", 1),
  DEL("Delete", 2),
  UPD("Update", 3),
  MOV("Move", 4),

  // Refactoring operations
  CHANGE("Change", 5),
  CONVERT("Convert", 6),
  EXTRACT("Extract", 7),
  INLINE("Inline", 8),
  INTRODUCE("Introduce", 9),
  MERGE("Merge", 10),
  MOVE("Move", 11),
  PARAMETERIZE("Parameterize", 12),
  PULLUP("Pull up", 13),
  PULLDOWN("Pull down", 14),
  REPLACE("Replace", 15),
  RENAME("Rename", 16),
  SPILT("Split", 17),
  MODIFY("Modify", 18),
  REORDER("Reorder", 19),

  UKN("Unknown", 20);

  public String label;
  public int index;

  Operation(String label, int index) {
    this.label = label;
    this.index = index;
  }

  @Override
  public String toString() {
    return label;
  }

  public int getIndex() {
    return index;
  }
}
