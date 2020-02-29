package com.github.smartcommit.model.constant;

/** Action operation on AST or Refactoring */
public enum Operation {
  // AST operations
  ADD("Add", 1),
  DEL("Delete", 2),
  UPD("Update", 3),
  MOV("Move", 4),

  // Refactoring operations
  EXTRACT_OPERATION("Extract Method", 5),
  RENAME_CLASS("Rename Class", 6),
  MOVE_ATTRIBUTE("Move Field", 7),
  MOVE_RENAME_ATTRIBUTE("Move And Rename Field", 8),
  REPLACE_ATTRIBUTE("Replace Field", 9),
  RENAME_METHOD("Rename Method", 10),
  INLINE_OPERATION("Inline Method", 11),
  MOVE_OPERATION("Move Method", 12),
  MOVE_AND_RENAME_OPERATION("Move And Rename Method", 13),
  PULL_UP_OPERATION("Pull Up Method",14),
  MOVE_CLASS("Move Class", 15),
  MOVE_RENAME_CLASS("Move And Rename Class", 16),
  MOVE_SOURCE_FOLDER("Move Source Folder", 17),
  PULL_UP_ATTRIBUTE("Pull Up Field", 18),
  PUSH_DOWN_ATTRIBUTE("Push Down Field", 19),
  PUSH_DOWN_OPERATION("Push Down Method", 20),
  EXTRACT_INTERFACE("Extract Interface", 21),
  EXTRACT_SUPERCLASS("Extract Superclass", 22),
  EXTRACT_SUBCLASS("Extract Subclass", 23),
  EXTRACT_CLASS("Extract Class", 24),
  MERGE_OPERATION("Merge Method", 25),
  EXTRACT_AND_MOVE_OPERATION("Extract And Move Method", 26),
  MOVE_AND_INLINE_OPERATION("Move And Inline Method", 27),
  CONVERT_ANONYMOUS_CLASS_TO_TYPE("Convert Anonymous Class to Type", 28),
  INTRODUCE_POLYMORPHISM("Introduce Polymorphism", 29),
  RENAME_PACKAGE("Change Package", 30),
  CHANGE_METHOD_SIGNATURE("Change Method Signature", 31),
  EXTRACT_VARIABLE("Extract Variable", 32),
  EXTRACT_ATTRIBUTE("Extract Field", 33),
  INLINE_VARIABLE("Inline Variable", 34),
  RENAME_VARIABLE("Rename Variable", 35),
  RENAME_PARAMETER("Rename Parameter", 36),
  RENAME_ATTRIBUTE("Rename Field", 37),
  MERGE_VARIABLE("Merge Variable", 38),
  MERGE_PARAMETER("Merge Parameter", 39),
  MERGE_ATTRIBUTE("Merge Field", 40),
  SPLIT_VARIABLE("Split Variable", 41),
  SPLIT_PARAMETER("Split Parameter", 42),
  SPLIT_ATTRIBUTE("Split Field", 43),
  REPLACE_VARIABLE_WITH_ATTRIBUTE("Replace Variable With Field", 44),
  PARAMETERIZE_VARIABLE("Parameterize Variable", 45),
  CHANGE_RETURN_TYPE("Change Return Type", 46),
  CHANGE_VARIABLE_TYPE("Change Variable Type", 47),
  CHANGE_PARAMETER_TYPE("Change Parameter Type", 48),
  CHANGE_ATTRIBUTE_TYPE("Change Field Type", 49),

  UKN("Unknown", 50);

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
