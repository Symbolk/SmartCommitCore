package com.github.smartcommit.model;

/** Description for diff hunk */
public class Description {
  // add, delete, modify
  private String action = "";
  private String targetTypeFrom = "";
  private String targetLabelFrom = "";

  // if modify change the type or label
  private String targetTypeTo = "";
  private String targetLabelTo = "";

  public Description(String action, String targetTypeFrom, String targetLabelFrom) {
    this.action = action;
    this.targetTypeFrom = targetTypeFrom;
    this.targetLabelFrom = targetLabelFrom;
    this.targetTypeTo = "";
    this.targetLabelTo = "";
  }

  public Description(
      String action,
      String targetTypeFrom,
      String targetLabelFrom,
      String targetTypeTo,
      String targetLabelTo) {
    this.action = action;
    this.targetTypeFrom = targetTypeFrom;
    this.targetLabelFrom = targetLabelFrom;
    this.targetTypeTo = targetTypeTo;
    this.targetLabelTo = targetLabelTo;
  }

  public Description() {}

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(action).append(" ");
    if (targetTypeTo.isEmpty()) {
      builder.append(targetTypeFrom);
      if (!targetLabelFrom.isEmpty()) {
        builder.append(": ").append(targetLabelFrom);
      }
    } else {
      builder.append(targetTypeFrom);
      if (!targetLabelFrom.isEmpty()) {
        builder.append(": ").append(targetLabelFrom);
      }
      // append To if type or label changed
      if (!targetTypeTo.equals(targetTypeFrom) || !targetLabelTo.equals(targetLabelFrom)) {
        builder.append(" To ").append(targetTypeTo);
        if (!targetLabelTo.isEmpty()) {
          builder.append(": ").append(targetLabelTo);
        }
      }
    }
    builder.append(".");

    return builder.toString();
  }
}
