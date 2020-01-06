package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;

import java.util.ArrayList;
import java.util.List;

public class EnumConstantInfo {
  public String name;
  public String belongTo;
  public List<String> arguments = new ArrayList<>();
  public String comment = "";

  // corresponding node in the graph
  public Node node;

  public String uniqueName() {
    return belongTo + ":" + name;
  }
}
