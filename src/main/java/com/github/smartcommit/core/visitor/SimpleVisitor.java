package com.github.smartcommit.core.visitor;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;

import java.util.ArrayList;
import java.util.List;

public class SimpleVisitor extends ASTVisitor {

  private List<String> simpleTypes;
  private List<String> simpleNames;

  public SimpleVisitor() {
    this.simpleTypes = new ArrayList<>();
    this.simpleNames = new ArrayList<>();
  }

  @Override
  public boolean visit(SimpleType node) {
//    System.out.println("------Type--------");
    this.simpleTypes.add(node.getName().toString());
    ITypeBinding binding = node.resolveBinding();
    if (binding != null) {
      if (binding.isFromSource()) {
        System.out.println(binding.getQualifiedName());
      }
    }
    return true;
  }

  @Override
  public boolean visit(SimpleName node) {
//    System.out.println("------Name--------");
    this.simpleNames.add(node.getIdentifier());
    //    IBinding binding = node.resolveBinding();
    ITypeBinding binding = node.resolveTypeBinding();
    if (binding != null) {
      if (binding.isFromSource()) {
        System.out.println(binding.getQualifiedName());
      }
    }
    return true;
  }

    public List<String> getSimpleTypes() {
        return simpleTypes;
    }

    public List<String> getSimpleNames() {
        return simpleNames;
    }
}
