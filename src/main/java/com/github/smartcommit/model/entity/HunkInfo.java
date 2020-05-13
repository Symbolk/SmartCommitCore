package com.github.smartcommit.model.entity;

import org.eclipse.jdt.core.dom.ASTNode;

import java.util.LinkedHashSet;
import java.util.Set;

public class HunkInfo extends DeclarationInfo {
  public String identifier = "-1:-1";
  public Integer fileIndex = -1;
  public Integer hunkIndex = -1;
  public Set<ASTNode> coveredNodes = new LinkedHashSet<>();

  public HunkInfo(Integer fileIndex, Integer hunkIndex) {
    this.fileIndex = fileIndex;
    this.hunkIndex = hunkIndex;
    this.identifier = fileIndex + ":" + hunkIndex;
  }

  public HunkInfo(String identifier) {
    this.identifier = identifier;
    String[] indices = identifier.split(":");
    if (indices.length == 2) {
      this.fileIndex = Integer.valueOf(indices[0]);
      this.hunkIndex = Integer.valueOf(indices[1]);
    }
  }

  public String uniqueName() {
    return fileIndex + ":" + hunkIndex;
  }
}
