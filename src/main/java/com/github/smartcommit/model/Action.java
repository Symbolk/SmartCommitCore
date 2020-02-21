package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.Operation;

/** One of the semantic change actions of the diff hunk */
public class Action {
  private Operation action;
  private String typeFrom = "";
  private String labelFrom = "";

  // if modify the type or label
  private String typeTo = "";
  private String labelTo = "";

  public Action(Operation action, String typeFrom, String labelFrom) {
    this.action = action;
    this.typeFrom = typeFrom;
    this.labelFrom = labelFrom;
    this.typeTo = "";
    this.labelTo = "";
  }

  public Action(Operation action, String typeFrom, String labelFrom, String typeTo, String labelTo) {
    this.action = action;
    this.typeFrom = typeFrom;
    this.labelFrom = labelFrom;
    this.typeTo = typeTo;
    this.labelTo = labelTo;
  }

  public Action() {}

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(action).append(" ");
    if (typeTo.isEmpty()) {
      builder.append(typeFrom);
      if (!labelFrom.isEmpty()) {
        builder.append(": ").append(labelFrom);
      }
    } else {
      builder.append(typeFrom);
      if (!labelFrom.isEmpty()) {
        builder.append(": ").append(labelFrom);
      }
      // append To if type or label changed
      if (!typeTo.equals(typeFrom) || !labelTo.equals(labelFrom)) {
        builder.append(" To ").append(typeTo);
        if (!labelTo.isEmpty()) {
          builder.append(": ").append(labelTo);
        }
      }
    }
    builder.append(".");

    return builder.toString();
  }
}
