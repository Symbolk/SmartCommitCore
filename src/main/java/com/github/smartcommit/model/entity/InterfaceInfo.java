package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;

import java.util.ArrayList;
import java.util.List;

public class InterfaceInfo {
  public String name;
  public String fullName;
  public String visibility;
  public List<String> superInterfaceTypeList = new ArrayList<>();
  public String comment = "";
  public String content;

  public Node node;

  public String uniqueName() {
    return fullName;
  }
}
