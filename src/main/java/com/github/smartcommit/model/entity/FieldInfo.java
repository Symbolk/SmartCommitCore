package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;

import java.util.Set;

public class FieldInfo extends MemberInfo {

  public String name;
  public String belongTo;
  public String typeString;
  public Set<String> types;
  public String visibility;
  public boolean isStatic;
  public boolean isFinal;
  public String comment = "";

  // corresponding node in the graph
  public Node node;

  public String uniqueName() {
    return belongTo + ":" + name;
  }
}
