package com.github.smartcommit.model.entity;

import org.eclipse.jdt.core.dom.IMethodBinding;

import java.util.HashSet;
import java.util.Set;

public class EntityInfo {
  // def internal
  public Set<String> typeDefs = new HashSet<>();
  public Set<String> fieldDefs = new HashSet<>();
  public Set<String> methodDefs = new HashSet<>();

  // use internal
  public Set<String> typeUses = new HashSet<>();
  public Set<IMethodBinding> methodCalls = new HashSet<>();
  public Set<String> fieldUses = new HashSet<>();
  public Set<String> paraUses = new HashSet<>();
  public Set<String> localVarUses = new HashSet<>();

}
