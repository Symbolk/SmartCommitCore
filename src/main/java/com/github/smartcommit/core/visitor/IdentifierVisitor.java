package com.github.smartcommit.core.visitor;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.util.IConstantValueAttribute;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IdentifierVisitor extends ASTVisitor {
  private List<String> invokedMethods;
  private List<String> declaredMethods;
  private List<String> declaredVars;
  private List<String> instantiatedClasses;
  private List<String> declaredFields;
  private List<String> accessedFields;

  public IdentifierVisitor() {
    this.invokedMethods = new ArrayList<>();
    this.declaredMethods = new ArrayList<>();
    this.declaredVars = new ArrayList<>();
    this.instantiatedClasses = new ArrayList<>();
    this.declaredFields = new ArrayList<>();
    this.accessedFields = new ArrayList<>();
  }

  @Override
  public boolean visit(MethodInvocation invocation) {
    IMethodBinding binding = invocation.resolveMethodBinding();
    if (binding != null) {
      ITypeBinding typeBinding = binding.getDeclaringClass();
      if (typeBinding.isFromSource()) {
        this.invokedMethods.add(typeBinding.getQualifiedName() + ":" + binding.toString());
      }
    } else {
      this.invokedMethods.add(invocation.getExpression().toString()+"."+invocation.getName().toString());
    }
    return true;
  }

  @Override
  public boolean visit(MethodDeclaration declaration) {
    IMethodBinding binding = declaration.resolveBinding();
    if (binding != null) {
      this.declaredMethods.add(
          binding.getDeclaringClass().getQualifiedName() + ":" + binding.toString());
    }
    return true;
  }

  @Override
  public boolean visit(ClassInstanceCreation creation) {
    IMethodBinding binding = creation.resolveConstructorBinding();
    if (binding != null) {
      ITypeBinding typeBinding = binding.getDeclaringClass();
      if (typeBinding.isFromSource()) {
        this.instantiatedClasses.add(typeBinding.getQualifiedName() + ":" + binding.toString());
      }
    }
    return true;
  }

  private List<String> processList(List list) {
    List<String> results = new ArrayList<>();
    for (Iterator iter = list.iterator(); iter.hasNext(); ) {
      VariableDeclarationFragment fragment = (VariableDeclarationFragment) iter.next();
      IVariableBinding binding = fragment.resolveBinding();
      if (binding != null && binding.getDeclaringClass() != null) {
        results.add(
            binding.getDeclaringClass().getQualifiedName()
                + ":"
                + binding.getDeclaringMethod().getName()
                + ":"
                + binding.toString());
      }
    }
    return results;
  }

  @Override
  public boolean visit(VariableDeclarationExpression declaration) {
    this.declaredVars.addAll(processList(declaration.fragments()));
    return true;
  }

  //  @Override
  //  public boolean visit(FieldDeclaration declaration) {
  //    this.declaredFields.addAll(processList(declaration.fragments()));
  //    return true;
  //  }

  @Override
  public boolean visit(FieldAccess access) {
    IVariableBinding binding = access.resolveFieldBinding();
    if (binding != null) {
      ITypeBinding typeBinding = binding.getDeclaringClass();
      if (typeBinding.isFromSource()) {
        this.accessedFields.add(
            typeBinding.getDeclaringClass().getQualifiedName() + ":" + binding.toString());
      }
    }
    return true;
  }

  public List<String> getInvokedMethods() {
    return invokedMethods;
  }

  public List<String> getDeclaredMethods() {
    return declaredMethods;
  }

  public List<String> getDeclaredVars() {
    return declaredVars;
  }

  public List<String> getInstantiatedClasses() {
    return instantiatedClasses;
  }
}
