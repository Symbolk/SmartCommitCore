package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;
import org.eclipse.jdt.core.dom.IMethodBinding;

import java.util.HashSet;
import java.util.Set;

public class FieldInfo {

  public String name;
  public String belongTo;
  public String typeString;
  public Set<String> types;
  public String visibility;
  public boolean isStatic;
  public boolean isFinal;
  public String comment = "";
  public Set<String> typeInitializes = new HashSet<>();
  public Set<IMethodBinding> methodCalls = new HashSet<>();
  public Set<String> fieldUses = new HashSet<>();

  // corresponding node in the graph
  public Node node;

  public String uniqueName() {
    return belongTo + "." + name;
  }
}
