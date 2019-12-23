package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;
import org.eclipse.jdt.core.dom.ASTNode;

import java.util.LinkedHashSet;
import java.util.Set;

public class HunkInfo extends EntityInfo {
  public String index;
  public Integer fileIndex;
  public Integer hunkIndex;
  public Set<ASTNode> coveredNodes = new LinkedHashSet<>();

  public Node node;

  public String uniqueName() {
    return fileIndex + ":" + hunkIndex;
  }
}
