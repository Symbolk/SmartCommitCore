package com.github.smartcommit.model;

public class Location {
  private String relativeFilePath;
  private Integer startLine;
  private Integer endLine;

  public Location(String relativeFilePath, Integer startLine, Integer endLine) {
    this.relativeFilePath = relativeFilePath;
    this.startLine = startLine;
    this.endLine = endLine;
  }

  public String getRelativeFilePath() {
    return relativeFilePath;
  }

  public Integer getStartLine() {
    return startLine;
  }

  public Integer getEndLine() {
    return endLine;
  }
}
