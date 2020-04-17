package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;

/** More like a method with name "static" */
public class InitializerInfo extends DeclarationInfo {
  public boolean isStatic = false;
  public String comment = "";
  public String body = "";
  public String belongTo = "";
  public Node node;

  public String uniqueName() {
    return belongTo + ":INIT";
  }
}
