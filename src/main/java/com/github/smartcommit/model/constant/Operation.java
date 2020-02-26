package com.github.smartcommit.model.constant;

/** Action operation on AST or Refactoring */
public enum Operation {
  // AST operations
  ADD("Add"),
  DEL("Delete"),
  UPD("Update"),
  MOV("Move"),

  // Refactoring operations
  EXTRACT_OPERATION("Extract Method"),
  RENAME_CLASS("Rename Class"),
  MOVE_ATTRIBUTE("Move Field"),
  MOVE_RENAME_ATTRIBUTE("Move And Rename Field"),
  REPLACE_ATTRIBUTE("Replace Field"),
  RENAME_METHOD("Rename Method"),
  INLINE_OPERATION("Inline Method"),
  MOVE_OPERATION("Move Method"),
  MOVE_AND_RENAME_OPERATION("Move And Rename Method"),
  PULL_UP_OPERATION("Pull Up Method"),
  MOVE_CLASS("Move Class"),
  MOVE_RENAME_CLASS("Move And Rename Class"),
  MOVE_SOURCE_FOLDER("Move Source Folder"),
  PULL_UP_ATTRIBUTE("Pull Up Field"),
  PUSH_DOWN_ATTRIBUTE("Push Down Field"),
  PUSH_DOWN_OPERATION("Push Down Method"),
  EXTRACT_INTERFACE("Extract Interface"),
  EXTRACT_SUPERCLASS("Extract Superclass"),
  EXTRACT_SUBCLASS("Extract Subclass"),
  EXTRACT_CLASS("Extract Class"),
  MERGE_OPERATION("Merge Method"),
  EXTRACT_AND_MOVE_OPERATION("Extract And Move Method"),
  MOVE_AND_INLINE_OPERATION("Move And Inline Method"),
  CONVERT_ANONYMOUS_CLASS_TO_TYPE("Convert Anonymous Class to Type"),
  INTRODUCE_POLYMORPHISM("Introduce Polymorphism"),
  RENAME_PACKAGE("Change Package"),
  CHANGE_METHOD_SIGNATURE("Change Method Signature"),
  EXTRACT_VARIABLE("Extract Variable"),
  EXTRACT_ATTRIBUTE("Extract Field"),
  INLINE_VARIABLE("Inline Variable"),
  RENAME_VARIABLE("Rename Variable"),
  RENAME_PARAMETER("Rename Parameter"),
  RENAME_ATTRIBUTE("Rename Field"),
  MERGE_VARIABLE("Merge Variable"),
  MERGE_PARAMETER("Merge Parameter"),
  MERGE_ATTRIBUTE("Merge Field"),
  SPLIT_VARIABLE("Split Variable"),
  SPLIT_PARAMETER("Split Parameter"),
  SPLIT_ATTRIBUTE("Split Field"),
  REPLACE_VARIABLE_WITH_ATTRIBUTE("Replace Variable With Field"),
  PARAMETERIZE_VARIABLE("Parameterize Variable"),
  CHANGE_RETURN_TYPE("Change Return Type"),
  CHANGE_VARIABLE_TYPE("Change Variable Type"),
  CHANGE_PARAMETER_TYPE("Change Parameter Type"),
  CHANGE_ATTRIBUTE_TYPE("Change Field Type"),

  UKN("unknown");

  public String label;

  Operation(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
