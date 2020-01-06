package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;

public class EnumInfo {
  public String name;
  public String fullName;
  public String visibility = "package";
  public String comment = "";
  public String content = "";

  public Node node;

  public String uniqueName() {
    return fullName;
  }
}
