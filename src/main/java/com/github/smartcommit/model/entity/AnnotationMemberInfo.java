package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;

public class AnnotationMemberInfo {
  public String name;
  public String belongTo;
  public String type;
  public String defaultValue;

  public Node node;

  public String uniqueName() {
    return belongTo + ":" + name;
  }
}
