package com.github.smartcommit.model;

import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.Version;
import org.eclipse.jdt.core.dom.ASTNode;

import java.util.ArrayList;
import java.util.List;

public class Hunk {
  private Location location;
  private Version version;
  private ContentType contentType;
  private List<String> codeSnippet;
  // at or above statement level
  private List<ASTNode> coveredNodes;

  public Hunk(
      Version version, Location location, ContentType contentType, List<String> codeSnippet) {
    this.version = version;
    this.location = location;
    this.contentType = contentType;
    this.codeSnippet = codeSnippet;
    this.coveredNodes = new ArrayList<>();
  }

  public String getRelativeFilePath() {
    return location.getRelativeFilePath();
  }

  public Version getVersion() {
    return version;
  }

  public Integer getStartLine() {
    return location.getStartLine();
  }

  public Integer getEndLine() {
    return location.getEndLine();
  }

  public List<String> getCodeSnippet() {
    return codeSnippet;
  }

  public ContentType getContentType() {
    return contentType;
  }
}
