package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.Version;

import java.util.List;

public class Hunk {
  private String relativeFilePath;
  private Version version;
  private Integer startLine;
  private Integer endLine;
  private ContentType contentType;
  private List<String> codeSnippet;

  public Hunk(
      String relativeFilePath,
      Version version,
      Integer startLine,
      Integer endLine,
      ContentType contentType,
      List<String> codeSnippet) {
    this.relativeFilePath = relativeFilePath;
    this.version = version;
    this.startLine = startLine;
    this.endLine = endLine;
    this.contentType = contentType;
    this.codeSnippet = codeSnippet;
  }

  public String getRelativeFilePath() {
    return relativeFilePath;
  }

  public Version getVersion() {
    return version;
  }

  public Integer getStartLine() {
    return startLine;
  }

  public Integer getEndLine() {
    return endLine;
  }

  public List<String> getCodeSnippet() {
    return codeSnippet;
  }

  public ContentType getContentType() {
    return contentType;
  }
}
