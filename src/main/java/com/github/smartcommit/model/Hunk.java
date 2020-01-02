package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.Version;

import java.util.List;

public class Hunk {
  private String relativeFilePath;
  private Integer startLine;
  private Integer endLine;
  private Version version;
  private ContentType contentType;
  private List<String> codeSnippet;

  public Hunk(
      Version version,
      String relativeFilePath,
      Integer startLine,
      Integer endLine,
      ContentType contentType,
      List<String> codeSnippet) {
    this.version = version;
    this.relativeFilePath = relativeFilePath;
    this.startLine = startLine;
    this.endLine = endLine;
    this.contentType = contentType;
    this.codeSnippet = codeSnippet;
  }

  public Version getVersion() {
    return version;
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

  public List<String> getCodeSnippet() {
    return codeSnippet;
  }

  public ContentType getContentType() {
    return contentType;
  }
}
