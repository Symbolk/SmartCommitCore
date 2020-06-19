package com.github.smartcommit.model.constant;

/** Action operation on AST or Refactoring */
public enum Operation {
  // AST operations
  ADD("Add", 1),
  DEL("Delete", 2),
  UPD("Update", 3),
  MOV("Move", 4),

  // Refactoring operations
  // ADD("Add", 1),
  CHANGE("Change", 5),
  CONVERT("Convert", 6),
  EXTRACT("Extract", 7),
  INLINE("Inline", 8),
  INTRODUCE("Introduce", 9),
  MERGE("Merge", 10),
  MODIFY("Modify", 11),
  MOVE("Move", 12),
  MOVEANDINLINE("Move and Rename", 13),
  MOVEANDRENAME("Move and Rename", 14),
  PARAMETERIZE("Parameterize", 15),
  PULLUP("Pull up", 16),
  PULLDOWN("Pull down", 17),
  REPLACE("Replace", 18),
  REORDER("Reorder", 19),
  RENAME("Rename", 20),
  REMOVE("Remove", 21),
  SPILT("Split", 22),


  UKN("Unknown", 23);

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
