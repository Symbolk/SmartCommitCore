package com.github.smartcommit.intent.model;

public enum Intent {
  FIX("fix"),
  UNKNOWN("NA");

  public String label;

  Intent(String label) {
    this.label = label;
  }
}
