package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.Operation;

/** One of the semantic change actions of the diff hunk */
public class Action {
  private Operation operation;
  private String typeFrom = "";
  private String labelFrom = "";

  // if modify the type or label
  private String typeTo = "";
  private String labelTo = "";

  public Action(Operation operation, String typeFrom, String labelFrom) {
    this.operation = operation;
    this.typeFrom = typeFrom;
    this.labelFrom = labelFrom;
    this.typeTo = "";
    this.labelTo = "";
  }

  public Action(
      Operation operation, String typeFrom, String labelFrom, String typeTo, String labelTo) {
    this.operation = operation;
    this.typeFrom = typeFrom;
    this.labelFrom = labelFrom;
    this.typeTo = typeTo;
    this.labelTo = labelTo;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(operation);
    builder
        .append(typeFrom.isEmpty() ? "" : " " + typeFrom)
        .append(labelFrom.isEmpty() ? "" : ":" + labelFrom);
    builder
        .append(typeTo.isEmpty() ? "" : " To:" + typeTo)
        .append(labelTo.isEmpty() ? "" : ":" + labelTo);
    builder.append(".");

    return builder.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof Action)) {
      return false;
    }

    Action a = (Action) obj;
    return a.operation.equals(this.operation)
        && a.typeFrom.equals(this.typeFrom)
        && a.typeTo.equals(this.typeTo)
        && a.labelFrom.equals(this.labelFrom)
        && a.labelTo.equals(this.labelTo);
  }
}
