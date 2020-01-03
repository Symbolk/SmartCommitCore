package com.github.smartcommit.util;

import com.github.smartcommit.model.entity.*;
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
   * Collect information from interface declaration
   *
   * @param node
   * @return
   */
  public InterfaceInfo createInterfaceInfo(TypeDeclaration node) {
    InterfaceInfo interfaceInfo = new InterfaceInfo();
    interfaceInfo.name = node.getName().getFullyQualifiedName();
    interfaceInfo.fullName = NameResolver.getFullName(node);
    interfaceInfo.visibility = getVisibility(node);
    List<Type> superInterfaceList = node.superInterfaceTypes();
    for (Type superInterface : superInterfaceList)
      interfaceInfo.superInterfaceTypeList.add(NameResolver.getFullName(superInterface));
    if (node.getJavadoc() != null)
      interfaceInfo.comment =
          sourceContent.substring(
              node.getJavadoc().getStartPosition(),
              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    interfaceInfo.content =
        sourceContent.substring(
            node.getStartPosition(), node.getStartPosition() + node.getLength());
    return interfaceInfo;
  }

  /**
   * Collect information from class declaration
   *
   * @param node
   * @return
   */
  public ClassInfo createClassInfo(TypeDeclaration node) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.name = node.getName().getFullyQualifiedName();
    classInfo.fullName = NameResolver.getFullName(node);
    classInfo.visibility = getVisibility(node);
    classInfo.isAbstract = isAbstract(node);
    classInfo.isFinal = isFinal(node);
    classInfo.superClassType =
        node.getSuperclassType() == null
            ? "java.lang.Object"
            : NameResolver.getFullName(node.getSuperclassType());
    List<Type> superInterfaceList = node.superInterfaceTypes();
    for (Type superInterface : superInterfaceList) {
      classInfo.superInterfaceTypeList.add(NameResolver.getFullName(superInterface));
    }
    if (node.getJavadoc() != null) {
      classInfo.comment =
          sourceContent.substring(
              node.getJavadoc().getStartPosition(),
              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
      classInfo.content =
          sourceContent.substring(
              node.getStartPosition(), node.getStartPosition() + node.getLength());
    }
    return classInfo;
  }
  /**
   * Collect information from a FieldDeclaration. Each FieldDeclaration can declare multiple fields
   *
   * @param node
   * @param belongTo
   * @return
   */
  public List<FieldInfo> createFieldInfos(
      Integer fileIndex, FieldDeclaration node, String belongTo) {
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
      fieldInfo.fileIndex = fileIndex;
      fieldInfo.belongTo = belongTo;
      fieldInfo.name = fragment.getName().getFullyQualifiedName();
      fieldInfo.typeString = typeString;
      fieldInfo.types = types;
      fieldInfo.visibility = visibility;
      fieldInfo.isFinal = isFinal;
      fieldInfo.isStatic = isStatic;
      fieldInfo.comment = comment;
      parseFieldInitializer(fieldInfo, fragment.getInitializer());

      fieldInfos.add(fieldInfo);
    }
    return fieldInfos;
  }

  /**
   * Collect information from a MethodDeclaration
   *
   * @thanks_to https://github.com/linzeqipku/SnowGraph
   * @param node
   * @param belongTo
   * @return
   */
  public MethodInfo createMethodInfo(Integer fileIndex, MethodDeclaration node, String belongTo) {
    MethodInfo methodInfo = new MethodInfo();
    methodInfo.fileIndex = fileIndex;
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
   * Parse the field initializer expression to collect useful information.
   *
   * @param fieldInfo
   * @param expression
   */
  public void parseFieldInitializer(FieldInfo fieldInfo, Expression expression) {
    if (expression == null) {
      return;
    }
    //    System.out.println(
    //        expression.toString() + " : " +
    // Annotation.nodeClassForType(expression.getNodeType()));
    if (expression.getNodeType() == ASTNode.ARRAY_INITIALIZER) {
      List<Expression> expressions = ((ArrayInitializer) expression).expressions();
      for (Expression expression2 : expressions) {
        parseFieldInitializer(fieldInfo, expression2);
      }
    }
    if (expression.getNodeType() == ASTNode.TYPE_LITERAL) {
      fieldInfo.typeUses.addAll(getTypes(((TypeLiteral) expression).getType()));
    }
    if (expression.getNodeType() == ASTNode.CAST_EXPRESSION) {
      parseFieldInitializer(fieldInfo, ((CastExpression) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.CONDITIONAL_EXPRESSION) {
      parseFieldInitializer(fieldInfo, ((ConditionalExpression) expression).getExpression());
      parseFieldInitializer(fieldInfo, ((ConditionalExpression) expression).getElseExpression());
      parseFieldInitializer(fieldInfo, ((ConditionalExpression) expression).getThenExpression());
    }
    if (expression.getNodeType() == ASTNode.INFIX_EXPRESSION) {
      parseFieldInitializer(fieldInfo, ((InfixExpression) expression).getLeftOperand());
      parseFieldInitializer(fieldInfo, ((InfixExpression) expression).getRightOperand());
    }
    if (expression.getNodeType() == ASTNode.INSTANCEOF_EXPRESSION) {
      parseFieldInitializer(fieldInfo, ((InstanceofExpression) expression).getLeftOperand());
    }
    if (expression.getNodeType() == ASTNode.PARENTHESIZED_EXPRESSION) {
      parseFieldInitializer(fieldInfo, ((ParenthesizedExpression) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.POSTFIX_EXPRESSION) {
      parseFieldInitializer(fieldInfo, ((PostfixExpression) expression).getOperand());
    }
    if (expression.getNodeType() == ASTNode.PREFIX_EXPRESSION) {
      parseFieldInitializer(fieldInfo, ((PrefixExpression) expression).getOperand());
    }
    if (expression.getNodeType() == ASTNode.THIS_EXPRESSION) {
      parseFieldInitializer(fieldInfo, ((ThisExpression) expression).getQualifier());
    }
    if (expression.getNodeType() == ASTNode.METHOD_INVOCATION) {
      List<Expression> arguments = ((MethodInvocation) expression).arguments();
      IMethodBinding methodBinding = ((MethodInvocation) expression).resolveMethodBinding();
      if (methodBinding != null) fieldInfo.methodCalls.add(methodBinding);
      for (Expression exp : arguments) parseFieldInitializer(fieldInfo, exp);
      parseFieldInitializer(fieldInfo, ((MethodInvocation) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION) {
      IMethodBinding constructorBinding =
          ((ClassInstanceCreation) expression).resolveConstructorBinding();
      if (constructorBinding != null) {
        fieldInfo.typeUses.add(constructorBinding.getDeclaringClass().getQualifiedName());
      }
    }
    if (expression.getNodeType() == ASTNode.ASSIGNMENT) {
      parseFieldInitializer(fieldInfo, ((Assignment) expression).getLeftHandSide());
      parseFieldInitializer(fieldInfo, ((Assignment) expression).getRightHandSide());
    }
    if (expression.getNodeType() == ASTNode.QUALIFIED_NAME) {
      if (((QualifiedName) expression).getQualifier().resolveTypeBinding() != null) {
        String name =
            ((QualifiedName) expression).getQualifier().resolveTypeBinding().getQualifiedName()
                + ":"
                + ((QualifiedName) expression).getName().getIdentifier();
        fieldInfo.fieldUses.add(name);
      }
      parseFieldInitializer(fieldInfo, ((QualifiedName) expression).getQualifier());
    }
  }

  /**
   * Parse the method body block to collect useful information
   *
   * @thanks_to https://github.com/linzeqipku/SnowGraph
   * @param methodBody
   */
  private void parseMethodBody(MethodInfo methodInfo, Block methodBody) {
    if (methodBody == null) return;
    List<Statement> statementList = methodBody.statements();
    List<Statement> statements = new ArrayList<>();
    for (int i = 0; i < statementList.size(); i++) {
      statements.add(statementList.get(i));
    }

    for (int i = 0; i < statements.size(); i++) {

      Statement statement = statements.get(i);
      if (statement.getNodeType() == ASTNode.BLOCK) {
        List<Statement> blockStatements = ((Block) statement).statements();
        for (int j = 0; j < blockStatements.size(); j++) {
          statements.add(i + j + 1, blockStatements.get(j));
        }
        continue;
      }
      if (statement.getNodeType() == ASTNode.ASSERT_STATEMENT) {
        Expression expression = ((AssertStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
        expression = ((AssertStatement) statement).getMessage();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
      }

      if (statement.getNodeType() == ASTNode.DO_STATEMENT) {
        Expression expression = ((DoStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
        Statement doBody = ((DoStatement) statement).getBody();
        if (doBody != null) {
          statements.add(i + 1, doBody);
        }
      }
      if (statement.getNodeType() == ASTNode.ENHANCED_FOR_STATEMENT) {
        Expression expression = ((EnhancedForStatement) statement).getExpression();
        Type type = ((EnhancedForStatement) statement).getParameter().getType();
        methodInfo.typeUses.addAll(getTypes(type));
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
        Statement forBody = ((EnhancedForStatement) statement).getBody();
        if (forBody != null) {
          statements.add(i + 1, forBody);
        }
      }
      if (statement.getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
        Expression expression = ((ExpressionStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
      }
      if (statement.getNodeType() == ASTNode.FOR_STATEMENT) {
        List<Expression> list = ((ForStatement) statement).initializers();
        for (int j = 0; j < list.size(); j++) {
          parseExpressionInMethod(methodInfo, list.get(j));
        }
        Expression expression = ((ForStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
        Statement forBody = ((ForStatement) statement).getBody();
        if (forBody != null) {
          statements.add(i + 1, forBody);
        }
      }
      if (statement.getNodeType() == ASTNode.IF_STATEMENT) {
        Expression expression = ((IfStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
        Statement thenStatement = ((IfStatement) statement).getThenStatement();
        Statement elseStatement = ((IfStatement) statement).getElseStatement();
        if (elseStatement != null) {
          statements.add(i + 1, elseStatement);
        }
        if (thenStatement != null) {
          statements.add(i + 1, thenStatement);
        }
      }
      if (statement.getNodeType() == ASTNode.RETURN_STATEMENT) {
        Expression expression = ((ReturnStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
      }
      if (statement.getNodeType() == ASTNode.SWITCH_STATEMENT) {
        Expression expression = ((SwitchStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
        List<Statement> switchStatements = ((SwitchStatement) statement).statements();
        for (int j = 0; j < switchStatements.size(); j++) {
          statements.add(i + j + 1, switchStatements.get(j));
        }
      }
      if (statement.getNodeType() == ASTNode.THROW_STATEMENT) {
        Expression expression = ((ThrowStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
      }
      if (statement.getNodeType() == ASTNode.TRY_STATEMENT) {
        Statement tryStatement = ((TryStatement) statement).getBody();
        if (tryStatement != null) {
          statements.add(i + 1, tryStatement);
        }
        continue;
      }
      if (statement.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
        Type type = ((VariableDeclarationStatement) statement).getType();
        List<VariableDeclaration> list = ((VariableDeclarationStatement) statement).fragments();
        methodInfo.typeUses.addAll(getTypes(type));
        for (VariableDeclaration decStat : list) {
          parseExpressionInMethod(methodInfo, decStat.getInitializer());
        }
      }
      if (statement.getNodeType() == ASTNode.WHILE_STATEMENT) {
        Expression expression = ((WhileStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
        Statement whileBody = ((WhileStatement) statement).getBody();
        if (whileBody != null) {
          statements.add(i + 1, whileBody);
        }
      }

      if (statement.getNodeType() == ASTNode.CONSTRUCTOR_INVOCATION) {
        IMethodBinding constructorBinding =
            ((ConstructorInvocation) statement).resolveConstructorBinding();
        if (constructorBinding != null) {
          methodInfo.typeUses.add(constructorBinding.getDeclaringClass().getQualifiedName());
        }
      }
    }
  }

  /**
   * Parse the expressions to get method calls and filed uses
   *
   * @thanks_to https://github.com/linzeqipku/SnowGraph
   * @param expression
   */
  private void parseExpressionInMethod(MethodInfo methodInfo, Expression expression) {
    if (expression == null) {
      return;
    }

    if (expression.getNodeType() == ASTNode.ARRAY_INITIALIZER) {
      List<Expression> expressions = ((ArrayInitializer) expression).expressions();
      for (Expression expression2 : expressions) {
        parseExpressionInMethod(methodInfo, expression2);
      }
    }

    if (expression.getNodeType() == ASTNode.TYPE_LITERAL) {
      methodInfo.typeUses.addAll(getTypes(((TypeLiteral) expression).getType()));
    }

    if (expression.getNodeType() == ASTNode.CAST_EXPRESSION) {
      parseExpressionInMethod(methodInfo, ((CastExpression) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.CONDITIONAL_EXPRESSION) {
      parseExpressionInMethod(methodInfo, ((ConditionalExpression) expression).getExpression());
      parseExpressionInMethod(methodInfo, ((ConditionalExpression) expression).getElseExpression());
      parseExpressionInMethod(methodInfo, ((ConditionalExpression) expression).getThenExpression());
    }
    if (expression.getNodeType() == ASTNode.INFIX_EXPRESSION) {
      parseExpressionInMethod(methodInfo, ((InfixExpression) expression).getLeftOperand());
      parseExpressionInMethod(methodInfo, ((InfixExpression) expression).getRightOperand());
    }
    if (expression.getNodeType() == ASTNode.INSTANCEOF_EXPRESSION) {
      parseExpressionInMethod(methodInfo, ((InstanceofExpression) expression).getLeftOperand());
    }
    if (expression.getNodeType() == ASTNode.PARENTHESIZED_EXPRESSION) {
      parseExpressionInMethod(methodInfo, ((ParenthesizedExpression) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.POSTFIX_EXPRESSION) {
      parseExpressionInMethod(methodInfo, ((PostfixExpression) expression).getOperand());
    }
    if (expression.getNodeType() == ASTNode.PREFIX_EXPRESSION) {
      parseExpressionInMethod(methodInfo, ((PrefixExpression) expression).getOperand());
    }

    if (expression.getNodeType() == ASTNode.FIELD_ACCESS) {
      FieldAccess fieldAccess = (FieldAccess) expression;
      // support this. field access
      if (fieldAccess.getExpression().getNodeType() == ASTNode.THIS_EXPRESSION) {
        methodInfo.fieldUses.add(methodInfo.belongTo + ":" + fieldAccess.getName());
      } else {
        parseExpressionInMethod(methodInfo, ((FieldAccess) expression).getExpression());
      }
    }

    if (expression.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION) {
      IMethodBinding constructorBinding =
          ((ClassInstanceCreation) expression).resolveConstructorBinding();
      if (constructorBinding != null) {
        methodInfo.typeUses.add(constructorBinding.getDeclaringClass().getQualifiedName());
      }
    }
    if (expression.getNodeType() == ASTNode.METHOD_INVOCATION) {
      List<Expression> arguments = ((MethodInvocation) expression).arguments();
      IMethodBinding methodBinding = ((MethodInvocation) expression).resolveMethodBinding();
      if (methodBinding != null) methodInfo.methodCalls.add(methodBinding);
      for (Expression exp : arguments) parseExpressionInMethod(methodInfo, exp);
      parseExpressionInMethod(methodInfo, ((MethodInvocation) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.ASSIGNMENT) {
      parseExpressionInMethod(methodInfo, ((Assignment) expression).getLeftHandSide());
      parseExpressionInMethod(methodInfo, ((Assignment) expression).getRightHandSide());
    }
    if (expression.getNodeType() == ASTNode.QUALIFIED_NAME) {
      ITypeBinding typeBinding = ((QualifiedName) expression).getQualifier().resolveTypeBinding();
      if (typeBinding != null) {
        String name =
            typeBinding.getQualifiedName()
                + ":"
                + ((QualifiedName) expression).getName().getIdentifier();
        methodInfo.fieldUses.add(name);
      }
      parseExpressionInMethod(methodInfo, ((QualifiedName) expression).getQualifier());
    }
  }

  /**
   * Collect information from statements
   *
   * @param entityInfo
   * @param statement
   */
  public void parseStatement(EntityInfo entityInfo, Statement statement) {
    List<Statement> statements = new ArrayList<>();
    statements.add(statement);
    for (int i = 0; i < statements.size(); i++) {

      Statement current = statements.get(i);
      if (current.getNodeType() == ASTNode.BLOCK) {
        List<Statement> blockStatements = ((Block) current).statements();
        for (int j = 0; j < blockStatements.size(); j++) {
          statements.add(i + j + 1, blockStatements.get(j));
        }
        continue;
      }
      if (current.getNodeType() == ASTNode.ASSERT_STATEMENT) {
        Expression expression = ((AssertStatement) current).getExpression();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
        expression = ((AssertStatement) current).getMessage();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
      }

      if (current.getNodeType() == ASTNode.DO_STATEMENT) {
        Expression expression = ((DoStatement) current).getExpression();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
        Statement doBody = ((DoStatement) current).getBody();
        if (doBody != null) {
          statements.add(i + 1, doBody);
        }
      }
      if (current.getNodeType() == ASTNode.ENHANCED_FOR_STATEMENT) {
        Expression expression = ((EnhancedForStatement) current).getExpression();
        Type type = ((EnhancedForStatement) current).getParameter().getType();
        entityInfo.typeUses.addAll(getTypes(type));
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
        Statement forBody = ((EnhancedForStatement) current).getBody();
        if (forBody != null) {
          statements.add(i + 1, forBody);
        }
      }
      if (current.getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
        Expression expression = ((ExpressionStatement) current).getExpression();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
      }
      if (current.getNodeType() == ASTNode.FOR_STATEMENT) {
        List<Expression> list = ((ForStatement) current).initializers();
        for (int j = 0; j < list.size(); j++) {
          parseExpression(entityInfo, list.get(j));
        }
        Expression expression = ((ForStatement) current).getExpression();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
        Statement forBody = ((ForStatement) current).getBody();
        if (forBody != null) {
          statements.add(i + 1, forBody);
        }
      }
      if (current.getNodeType() == ASTNode.IF_STATEMENT) {
        Expression expression = ((IfStatement) current).getExpression();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
        Statement thenStatement = ((IfStatement) current).getThenStatement();
        Statement elseStatement = ((IfStatement) current).getElseStatement();
        if (elseStatement != null) {
          statements.add(i + 1, elseStatement);
        }
        if (thenStatement != null) {
          statements.add(i + 1, thenStatement);
        }
      }
      if (current.getNodeType() == ASTNode.RETURN_STATEMENT) {
        Expression expression = ((ReturnStatement) current).getExpression();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
      }
      if (current.getNodeType() == ASTNode.SWITCH_STATEMENT) {
        Expression expression = ((SwitchStatement) current).getExpression();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
        List<Statement> switchStatements = ((SwitchStatement) current).statements();
        for (int j = 0; j < switchStatements.size(); j++) {
          statements.add(i + j + 1, switchStatements.get(j));
        }
      }
      if (current.getNodeType() == ASTNode.THROW_STATEMENT) {
        Expression expression = ((ThrowStatement) current).getExpression();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
      }
      if (current.getNodeType() == ASTNode.TRY_STATEMENT) {
        Statement tryStatement = ((TryStatement) current).getBody();
        if (tryStatement != null) {
          statements.add(i + 1, tryStatement);
        }
        continue;
      }
      if (current.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
        Type type = ((VariableDeclarationStatement) current).getType();
        List<VariableDeclaration> list = ((VariableDeclarationStatement) current).fragments();
        entityInfo.typeUses.addAll(getTypes(type));
        for (VariableDeclaration decStat : list) {
          parseExpression(entityInfo, decStat.getInitializer());
        }
      }
      if (current.getNodeType() == ASTNode.WHILE_STATEMENT) {
        Expression expression = ((WhileStatement) current).getExpression();
        if (expression != null) {
          parseExpression(entityInfo, expression);
        }
        Statement whileBody = ((WhileStatement) current).getBody();
        if (whileBody != null) {
          statements.add(i + 1, whileBody);
        }
      }

      if (current.getNodeType() == ASTNode.CONSTRUCTOR_INVOCATION) {
        IMethodBinding constructorBinding =
            ((ConstructorInvocation) current).resolveConstructorBinding();
        if (constructorBinding != null) {
          entityInfo.typeUses.add(constructorBinding.getDeclaringClass().getQualifiedName());
        }
      }
    }
  }

  /**
   * Parse expression
   *
   * @param entityInfo
   * @param expression
   */
  private void parseExpression(EntityInfo entityInfo, Expression expression) {
    if (expression == null) {
      return;
    }

    if (expression.getNodeType() == ASTNode.ARRAY_INITIALIZER) {
      List<Expression> expressions = ((ArrayInitializer) expression).expressions();
      for (Expression expression2 : expressions) {
        parseExpression(entityInfo, expression2);
      }
    }

    if (expression.getNodeType() == ASTNode.TYPE_LITERAL) {
      entityInfo.typeUses.addAll(getTypes(((TypeLiteral) expression).getType()));
    }
    if (expression.getNodeType() == ASTNode.CAST_EXPRESSION) {
      parseExpression(entityInfo, ((CastExpression) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.CONDITIONAL_EXPRESSION) {
      parseExpression(entityInfo, ((ConditionalExpression) expression).getExpression());
      parseExpression(entityInfo, ((ConditionalExpression) expression).getElseExpression());
      parseExpression(entityInfo, ((ConditionalExpression) expression).getThenExpression());
    }
    if (expression.getNodeType() == ASTNode.INFIX_EXPRESSION) {
      parseExpression(entityInfo, ((InfixExpression) expression).getLeftOperand());
      parseExpression(entityInfo, ((InfixExpression) expression).getRightOperand());
    }
    if (expression.getNodeType() == ASTNode.INSTANCEOF_EXPRESSION) {
      parseExpression(entityInfo, ((InstanceofExpression) expression).getLeftOperand());
    }
    if (expression.getNodeType() == ASTNode.PARENTHESIZED_EXPRESSION) {
      parseExpression(entityInfo, ((ParenthesizedExpression) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.POSTFIX_EXPRESSION) {
      parseExpression(entityInfo, ((PostfixExpression) expression).getOperand());
    }
    if (expression.getNodeType() == ASTNode.PREFIX_EXPRESSION) {
      parseExpression(entityInfo, ((PrefixExpression) expression).getOperand());
    }

    if (expression.getNodeType() == ASTNode.FIELD_ACCESS) {
      FieldAccess fieldAccess = (FieldAccess) expression;
      // support this. field access
      if (fieldAccess.getExpression().getNodeType() == ASTNode.THIS_EXPRESSION) {
        entityInfo.fieldUses.add(
            fieldAccess.resolveFieldBinding().getDeclaringClass().getQualifiedName()
                + ":"
                + fieldAccess.getName());
      } else {
        parseExpression(entityInfo, ((FieldAccess) expression).getExpression());
      }
    }

    if (expression.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION) {
      IMethodBinding constructorBinding =
          ((ClassInstanceCreation) expression).resolveConstructorBinding();
      if (constructorBinding != null) {
        entityInfo.typeUses.add(constructorBinding.getDeclaringClass().getQualifiedName());
      }
    }
    if (expression.getNodeType() == ASTNode.METHOD_INVOCATION) {
      IMethodBinding methodBinding = ((MethodInvocation) expression).resolveMethodBinding();
      if (methodBinding != null) entityInfo.methodCalls.add(methodBinding);

      List<Expression> arguments = ((MethodInvocation) expression).arguments();
      for (Expression exp : arguments) {
        parseExpression(entityInfo, exp);
      }
      parseExpression(entityInfo, ((MethodInvocation) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.ASSIGNMENT) {
      parseExpression(entityInfo, ((Assignment) expression).getLeftHandSide());
      parseExpression(entityInfo, ((Assignment) expression).getRightHandSide());
    }
    if (expression.getNodeType() == ASTNode.QUALIFIED_NAME) {
      ITypeBinding typeBinding = ((QualifiedName) expression).getQualifier().resolveTypeBinding();
      if (typeBinding != null) {
        String name =
            typeBinding.getQualifiedName()
                + ":"
                + ((QualifiedName) expression).getName().getIdentifier();
        entityInfo.fieldUses.add(name);
      }
      parseExpression(entityInfo, ((QualifiedName) expression).getQualifier());
    }
    if (expression.getNodeType() == ASTNode.SIMPLE_NAME) {
      IBinding binding = ((SimpleName) expression).resolveBinding();
      if (binding != null && binding instanceof IVariableBinding) {
        IVariableBinding varBinding = ((IVariableBinding) binding);
        if (varBinding.isField()) {
          entityInfo.fieldUses.add(
              varBinding.getDeclaringClass().getQualifiedName() + ":" + binding.getName());
        } else if (varBinding.isParameter()) {
          entityInfo.paraUses.add(
              varBinding.getDeclaringMethod().getDeclaringClass().getQualifiedName()
                  + ":"
                  + varBinding.getDeclaringMethod().getName()
                  + ":"
                  + varBinding.getName());
        } else {
          entityInfo.localVarUses.add(
              varBinding.getDeclaringMethod().getDeclaringClass().getQualifiedName()
                  + ":"
                  + varBinding.getDeclaringMethod().getName()
                  + ":"
                  + varBinding.getName());
        }
      }
    }

    if (expression.getNodeType() == ASTNode.EXPRESSION_METHOD_REFERENCE) {
      IMethodBinding methodBinding =
          ((ExpressionMethodReference) expression).resolveMethodBinding();
      if (methodBinding != null) {
        entityInfo.methodCalls.add(methodBinding);
      }
    }
  }

  private Set<String> getTypes(Type oType) {
    Set<String> types = new HashSet<>();
    if (oType == null) return types;
    ITypeBinding typeBinding = oType.resolveBinding();
    // also record unresolved types
    if (typeBinding == null) {
      types.add(oType.toString());
      return types;
    }
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
