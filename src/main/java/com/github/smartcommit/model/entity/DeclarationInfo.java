package com.github.smartcommit.model.entity;

import com.github.smartcommit.model.graph.Node;
import org.eclipse.jdt.core.dom.IMethodBinding;

import java.util.HashSet;
import java.util.Set;

/**
 * Information collected in declarations (e.g. type, field, method/constructor, annotation member,
 * enum constant)
 */
public class DeclarationInfo {
  // which file the entity belongs to
  public Integer fileIndex;
  // corresponding node in the graph
  public Node node;

  // def internal
  public Set<String> typeDefs = new HashSet<>(); // AbstractType, including Type, Enum, Annotation
  public Set<String> fieldDefs = new HashSet<>();
  public Set<String> methodDefs = new HashSet<>();

  // use internal
  public Set<String> typeUses = new HashSet<>(); // AbstractType, including Type, Enum, Annotation
  public Set<IMethodBinding> methodCalls = new HashSet<>();
  public Set<String> fieldUses = new HashSet<>();
  public Set<String> paraUses = new HashSet<>();
  public Set<String> localVarUses = new HashSet<>();
}
