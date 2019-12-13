package com.github.smartcommit.model.entity;

import org.eclipse.jdt.core.dom.IMethodBinding;

import java.util.HashSet;
import java.util.Set;

public class MethodInfo {
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
  public Set<String> localVarTypes = new HashSet<>();
  public Set<IMethodBinding> methodCalls = new HashSet<>();
  public Set<String> fieldUses = new HashSet<>();
  public Set<String> exceptionThrows = new HashSet<>();

  public IMethodBinding methodBinding;

  public String hashName() {
    return belongTo + "." + name + "(" + paramString + ")";
  }
}
