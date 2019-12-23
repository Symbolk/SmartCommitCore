package com.github.smartcommit.model.entity;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IMethodBinding;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class HunkInfo {
  public String index;
  public Integer fileIndex;
  public Integer hunkIndex;
  public Set<ASTNode> coveredNodes = new LinkedHashSet<>();

  // def
  public Set<String> typeDecls = new HashSet<>();
  public Set<String> fieldDecls = new HashSet<>();
  public Set<IMethodBinding> methodDecls = new HashSet<>();

  // use
  public Set<String> localVarTypes = new HashSet<>();
  public Set<IMethodBinding> methodCalls = new HashSet<>();
  public Set<String> fieldUses = new HashSet<>();

  public String uniqueName() {
    return fileIndex + ":" + hunkIndex;
  }
}
