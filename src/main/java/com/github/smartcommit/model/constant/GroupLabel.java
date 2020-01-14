package com.github.smartcommit.model.constant;

public enum GroupLabel {
  NONJAVA("Non-Java-Changes"),
  REFORMAT("Reformat-Changes"),
  LINKED("Synatically-Linked-Changes"),
  OHTER("Other-Changes");

  public String label;

  GroupLabel(String label) {
    this.label = label;
  }
}
