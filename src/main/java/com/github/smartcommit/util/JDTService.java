package com.github.smartcommit.util;

import com.github.smartcommit.model.entity.FieldInfo;
import com.github.smartcommit.model.entity.MethodInfo;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class JDTService {
  private String sourceContent;

  public JDTService(String sourceContent) {
    this.sourceContent = sourceContent;
  }

  /**
   * Get all declaration descendants of an ASTNode
   *
   * @param node
   * @return
   */
  public List<BodyDeclaration> getDescendants(ASTNode node) {
    List<BodyDeclaration> descendants = new ArrayList<BodyDeclaration>();
    List list = node.structuralPropertiesForType();
    for (int i = 0; i < list.size(); i++) {
      Object child = node.getStructuralProperty((StructuralPropertyDescriptor) list.get(i));
      if (child instanceof List) {
        for (Iterator it = ((List) child).listIterator(); it.hasNext(); ) {
          Object child2 = it.next();
          if (child2 instanceof BodyDeclaration) {
            descendants.add((BodyDeclaration) child2);
            descendants.addAll(getDescendants((ASTNode) child2));
          }
        }
      }
      if (child instanceof BodyDeclaration) {
        descendants.add((BodyDeclaration) child);
      }
    }
    return descendants;
  }

  /**
   * Get only the direct children of an ASTNode
   *
   * @param node
   * @return
   */
  public List<ASTNode> getChildren(ASTNode node) {
    List<ASTNode> children = new ArrayList<ASTNode>();
    List list = node.structuralPropertiesForType();
    for (int i = 0; i < list.size(); i++) {
      Object child = node.getStructuralProperty((StructuralPropertyDescriptor) list.get(i));
      if (child instanceof ASTNode) {
        children.add((ASTNode) child);
      }
    }
    return children;
  }

  /**
   * Get the fully qualified name of a type declaration
   *
   * @param type
   * @return
   */
  public String getQualifiedNameForType(TypeDeclaration type) {
    String name = type.getName().getIdentifier();
    ASTNode parent = type.getParent();
    // resolve full name e.g.: A.B
    while (parent != null && parent.getClass() == TypeDeclaration.class) {
      name = ((TypeDeclaration) parent).getName().getIdentifier() + "." + name;
      parent = parent.getParent();
    }
    // resolve fully qualified name e.g.: some.package.A.B
    if (type.getRoot().getClass() == CompilationUnit.class) {
      name = getPackageName(type) + "." + name;
    }
    return name;
  }

  /**
   * Get the package name of a type, if it has
   *
   * @param decl
   * @return
   */
  public String getPackageName(TypeDeclaration decl) {
    CompilationUnit root = (CompilationUnit) decl.getRoot();
    if (root.getPackage() != null) {
      PackageDeclaration pack = root.getPackage();
      return pack.getName().getFullyQualifiedName();
    }
    return "";
  }

  /**
   * Collect information from a FieldDeclaration. Each FieldDeclaration can declare multiple fields
   *
   * @param node
   * @param belongTo
   * @return
   */
  public List<FieldInfo> createFieldInfos(FieldDeclaration node, String belongTo) {
    List<FieldInfo> fieldInfos = new ArrayList<>();
    Type type = node.getType();
    Set<String> types = getTypes(type);
    String typeString = type.toString();
    String visibility = getVisibility(node);
    boolean isStatic = isStatic(node);
    boolean isFinal = isFinal(node);
    String comment = "";
    if (node.getJavadoc() != null)
      comment =
          sourceContent.substring(
              node.getJavadoc().getStartPosition(),
              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    List<VariableDeclarationFragment> fragments = node.fragments();
    for (VariableDeclarationFragment fragment : fragments) {

      FieldInfo fieldInfo = new FieldInfo();
      fieldInfo.belongTo = belongTo;
      fieldInfo.name = fragment.getName().getFullyQualifiedName();
      fieldInfo.typeString = typeString;
      fieldInfo.types = types;
      fieldInfo.visibility = visibility;
      fieldInfo.isFinal = isFinal;
      fieldInfo.isStatic = isStatic;
      fieldInfo.comment = comment;
      fieldInfos.add(fieldInfo);
    }
    return fieldInfos;
  }

  /**
   * Collect information from a MethodDeclaration
   *
   * @borrowed_from https://github.com/linzeqipku/SnowGraph
   * @param node
   * @param belongTo
   * @return
   */
  public MethodInfo createMethodInfo(MethodDeclaration node, String belongTo) {
    MethodInfo methodInfo = new MethodInfo();
    methodInfo.methodBinding = node.resolveBinding();
    methodInfo.name = node.getName().getFullyQualifiedName();
    Type returnType = node.getReturnType2();
    methodInfo.returnString = returnType == null ? "void" : returnType.toString();
    methodInfo.returnTypes = getTypes(returnType);
    methodInfo.visibility = getVisibility(node);
    methodInfo.isConstruct = node.isConstructor();
    methodInfo.isAbstract = isAbstract(node);
    methodInfo.isFinal = isFinal(node);
    methodInfo.isStatic = isStatic(node);
    methodInfo.isSynchronized = isSynchronized(node);
    methodInfo.content =
        sourceContent.substring(
            node.getStartPosition(), node.getStartPosition() + node.getLength());
    if (node.getJavadoc() != null)
      methodInfo.comment =
          sourceContent.substring(
              node.getJavadoc().getStartPosition(),
              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    methodInfo.belongTo = belongTo;
    List<SingleVariableDeclaration> params = node.parameters();
    List<String> paramStringList = new ArrayList<>();
    for (SingleVariableDeclaration param : params) {
      String name = param.getName().getFullyQualifiedName();
      Type type = param.getType();
      String paramString = (isFinal(param) ? "final" : "") + " " + type.toString() + " " + name;
      paramStringList.add(paramString);
      methodInfo.paramTypes.addAll(getTypes(type));
    }
    methodInfo.paramString = String.join(", ", paramStringList).trim();
    List<Type> expList = node.thrownExceptionTypes();
    for (Type exp : expList) {
      String name = NameResolver.getFullName(exp);
      methodInfo.exceptionThrows.add(name);
    }
    parseMethodBody(methodInfo, node.getBody());
    return methodInfo;
  }

  /**
   * Parse the method body block to collect useful information
   *
   * @borrowed_from https://github.com/linzeqipku/SnowGraph
   * @param methodBody
   */
  public void parseMethodBody(MethodInfo methodInfo, Block methodBody) {
    if (methodBody == null) return;
    List<Statement> statementList = methodBody.statements();
    List<Statement> statements = new ArrayList<>();
    for (int i = 0; i < statementList.size(); i++) {
      statements.add(statementList.get(i));
    }
    for (int i = 0; i < statements.size(); i++) {

      if (statements.get(i).getNodeType() == ASTNode.BLOCK) {
        List<Statement> blockStatements = ((Block) statements.get(i)).statements();
        for (int j = 0; j < blockStatements.size(); j++) {
          statements.add(i + j + 1, blockStatements.get(j));
        }
        continue;
      }
      if (statements.get(i).getNodeType() == ASTNode.ASSERT_STATEMENT) {
        Expression expression = ((AssertStatement) statements.get(i)).getExpression();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
        expression = ((AssertStatement) statements.get(i)).getMessage();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
      }

      if (statements.get(i).getNodeType() == ASTNode.DO_STATEMENT) {
        Expression expression = ((DoStatement) statements.get(i)).getExpression();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
        Statement doBody = ((DoStatement) statements.get(i)).getBody();
        if (doBody != null) {
          statements.add(i + 1, doBody);
        }
      }
      if (statements.get(i).getNodeType() == ASTNode.ENHANCED_FOR_STATEMENT) {
        Expression expression = ((EnhancedForStatement) statements.get(i)).getExpression();
        Type type = ((EnhancedForStatement) statements.get(i)).getParameter().getType();
        methodInfo.localVarTypes.addAll(getTypes(type));
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
        Statement forBody = ((EnhancedForStatement) statements.get(i)).getBody();
        if (forBody != null) {
          statements.add(i + 1, forBody);
        }
      }
      if (statements.get(i).getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
        Expression expression = ((ExpressionStatement) statements.get(i)).getExpression();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
      }
      if (statements.get(i).getNodeType() == ASTNode.FOR_STATEMENT) {
        List<Expression> list = ((ForStatement) statements.get(i)).initializers();
        for (int j = 0; j < list.size(); j++) {
          parseExpression(methodInfo, list.get(j));
        }
        Expression expression = ((ForStatement) statements.get(i)).getExpression();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
        Statement forBody = ((ForStatement) statements.get(i)).getBody();
        if (forBody != null) {
          statements.add(i + 1, forBody);
        }
      }
      if (statements.get(i).getNodeType() == ASTNode.IF_STATEMENT) {
        Expression expression = ((IfStatement) statements.get(i)).getExpression();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
        Statement thenStatement = ((IfStatement) statements.get(i)).getThenStatement();
        Statement elseStatement = ((IfStatement) statements.get(i)).getElseStatement();
        if (elseStatement != null) {
          statements.add(i + 1, elseStatement);
        }
        if (thenStatement != null) {
          statements.add(i + 1, thenStatement);
        }
      }
      if (statements.get(i).getNodeType() == ASTNode.RETURN_STATEMENT) {
        Expression expression = ((ReturnStatement) statements.get(i)).getExpression();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
      }
      if (statements.get(i).getNodeType() == ASTNode.SWITCH_STATEMENT) {
        Expression expression = ((SwitchStatement) statements.get(i)).getExpression();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
        List<Statement> switchStatements = ((SwitchStatement) statements.get(i)).statements();
        for (int j = 0; j < switchStatements.size(); j++) {
          statements.add(i + j + 1, switchStatements.get(j));
        }
      }
      if (statements.get(i).getNodeType() == ASTNode.THROW_STATEMENT) {
        Expression expression = ((ThrowStatement) statements.get(i)).getExpression();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
      }
      if (statements.get(i).getNodeType() == ASTNode.TRY_STATEMENT) {
        Statement tryStatement = ((TryStatement) statements.get(i)).getBody();
        if (tryStatement != null) {
          statements.add(i + 1, tryStatement);
        }
        continue;
      }
      if (statements.get(i).getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
        Type type = ((VariableDeclarationStatement) statements.get(i)).getType();
        List<VariableDeclaration> list =
            ((VariableDeclarationStatement) statements.get(i)).fragments();
        methodInfo.localVarTypes.addAll(getTypes(type));
        for (VariableDeclaration decStat : list) {
          parseExpression(methodInfo, decStat.getInitializer());
        }
      }
      if (statements.get(i).getNodeType() == ASTNode.WHILE_STATEMENT) {
        Expression expression = ((WhileStatement) statements.get(i)).getExpression();
        if (expression != null) {
          parseExpression(methodInfo, expression);
        }
        Statement whileBody = ((WhileStatement) statements.get(i)).getBody();
        if (whileBody != null) {
          statements.add(i + 1, whileBody);
        }
      }
    }
  }

  /**
   * Parse the expressions to get method calls and filed uses
   *
   * @borrowed_from https://github.com/linzeqipku/SnowGraph
   * @param expression
   */
  private void parseExpression(MethodInfo methodInfo, Expression expression) {
    if (expression == null) {
      return;
    }
    System.out.println(
        expression.toString() + " : " + Annotation.nodeClassForType(expression.getNodeType()));
    if (expression.getNodeType() == ASTNode.ARRAY_INITIALIZER) {
      List<Expression> expressions = ((ArrayInitializer) expression).expressions();
      for (Expression expression2 : expressions) {
        parseExpression(methodInfo, expression2);
      }
    }
    if (expression.getNodeType() == ASTNode.CAST_EXPRESSION) {
      parseExpression(methodInfo, ((CastExpression) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.CONDITIONAL_EXPRESSION) {
      parseExpression(methodInfo, ((ConditionalExpression) expression).getExpression());
      parseExpression(methodInfo, ((ConditionalExpression) expression).getElseExpression());
      parseExpression(methodInfo, ((ConditionalExpression) expression).getThenExpression());
    }
    if (expression.getNodeType() == ASTNode.INFIX_EXPRESSION) {
      parseExpression(methodInfo, ((InfixExpression) expression).getLeftOperand());
      parseExpression(methodInfo, ((InfixExpression) expression).getRightOperand());
    }
    if (expression.getNodeType() == ASTNode.INSTANCEOF_EXPRESSION) {
      parseExpression(methodInfo, ((InstanceofExpression) expression).getLeftOperand());
    }
    if (expression.getNodeType() == ASTNode.PARENTHESIZED_EXPRESSION) {
      parseExpression(methodInfo, ((ParenthesizedExpression) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.POSTFIX_EXPRESSION) {
      parseExpression(methodInfo, ((PostfixExpression) expression).getOperand());
    }
    if (expression.getNodeType() == ASTNode.PREFIX_EXPRESSION) {
      parseExpression(methodInfo, ((PrefixExpression) expression).getOperand());
    }
    if (expression.getNodeType() == ASTNode.THIS_EXPRESSION) {
      parseExpression(methodInfo, ((ThisExpression) expression).getQualifier());
    }
    if (expression.getNodeType() == ASTNode.METHOD_INVOCATION) {
      List<Expression> arguments = ((MethodInvocation) expression).arguments();
      IMethodBinding methodBinding = ((MethodInvocation) expression).resolveMethodBinding();
      if (methodBinding != null) methodInfo.methodCalls.add(methodBinding);
      for (Expression exp : arguments) parseExpression(methodInfo, exp);
      parseExpression(methodInfo, ((MethodInvocation) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.ASSIGNMENT) {
      parseExpression(methodInfo, ((Assignment) expression).getLeftHandSide());
      parseExpression(methodInfo, ((Assignment) expression).getRightHandSide());
    }
    if (expression.getNodeType() == ASTNode.QUALIFIED_NAME) {
      if (((QualifiedName) expression).getQualifier().resolveTypeBinding() != null) {
        String name =
            ((QualifiedName) expression).getQualifier().resolveTypeBinding().getQualifiedName()
                + "."
                + ((QualifiedName) expression).getName().getIdentifier();
        methodInfo.fieldUses.add(name);
      }
      parseExpression(methodInfo, ((QualifiedName) expression).getQualifier());
    }
  }

  private Set<String> getTypes(Type oType) {
    Set<String> types = new HashSet<>();
    if (oType == null) return types;
    ITypeBinding typeBinding = oType.resolveBinding();
    if (typeBinding == null) return types;
    String str = typeBinding.getQualifiedName();
    String[] eles = str.split("[^A-Za-z0-9_\\.]+");
    for (String e : eles) {
      if (e.equals("extends")) continue;
      types.add(e);
    }
    return types;
  }

  private String getVisibility(BodyDeclaration decl) {
    int modifiers = decl.getModifiers();
    if (Modifier.isPrivate(modifiers)) return "private";
    if (Modifier.isProtected(modifiers)) return "protected";
    if (Modifier.isPublic(modifiers)) return "public";
    return "package";
  }

  private boolean isAbstract(BodyDeclaration decl) {
    int modifiers = decl.getModifiers();
    return (Modifier.isAbstract(modifiers));
  }

  private boolean isFinal(BodyDeclaration decl) {
    int modifiers = decl.getModifiers();
    return (Modifier.isFinal(modifiers));
  }

  private boolean isFinal(SingleVariableDeclaration decl) {
    int modifiers = decl.getModifiers();
    return (Modifier.isFinal(modifiers));
  }

  private boolean isStatic(BodyDeclaration decl) {
    int modifiers = decl.getModifiers();
    return (Modifier.isStatic(modifiers));
  }

  private boolean isSynchronized(BodyDeclaration decl) {
    int modifiers = decl.getModifiers();
    return (Modifier.isSynchronized(modifiers));
  }
}
