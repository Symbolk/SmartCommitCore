package com.github.smartcommit.core;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.SimpleName;

public class IdentifierVisitor extends ASTVisitor {
  @Override
  public boolean visit(SimpleName node) {
    System.out.println(node.getIdentifier());
    System.out.println(node.resolveBinding());
    System.out.println(node.resolveTypeBinding());
    System.out.println("--------------");
    return true;
  }
}
