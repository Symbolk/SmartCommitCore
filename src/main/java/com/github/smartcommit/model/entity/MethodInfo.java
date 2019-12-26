package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;
import org.eclipse.jdt.core.dom.IMethodBinding;

import java.util.HashSet;
import java.util.Set;

public class MethodInfo extends EntityInfo {
  public String visibility;
  public boolean isConstruct;
  public boolean isAbstract;
  public boolean isFinal;
  public boolean isStatic;
  public boolean isSynchronized;

  public String name;
  public String belongTo;
  public String returnString;
  public Set<String> returnTypes = new HashSet<>();

  public String content;
  public String comment = "";
  public String paramString;
  public Set<String> paramTypes = new HashSet<>();
  public Set<String> exceptionThrows = new HashSet<>();

  // corresponding node in the graph
  public Node node;

  public IMethodBinding methodBinding;

  public String uniqueName() {
    return belongTo + ":" + name + "(" + paramString + ")";
  }
}
