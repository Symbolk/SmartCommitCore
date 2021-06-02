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
   * Get the qualified name of an anonymous declaration class declaration
   *
   * @param declaration
   * @return
   */
  public String getQualifiedNameForAnonyType(
      AnonymousClassDeclaration declaration, String superClassName) {
    ASTNode parent = declaration.getParent();
    // for inner declaration, resolve full name e.g.: A.B
    StringBuilder builder = new StringBuilder(superClassName);
    // get the method declaration where the class is declared in
    while (parent != null
        && parent.getClass() != MethodDeclaration.class
        && parent.getClass() != FieldDeclaration.class
        && parent.getClass() != Initializer.class) {
      parent = parent.getParent();
    }

    assert parent != null;
    if (parent instanceof MethodDeclaration) {
      MethodDeclaration parentMethod = (MethodDeclaration) parent;
      List<SingleVariableDeclaration> params = parentMethod.parameters();
      List<String> paramStringList = new ArrayList<>();
      for (SingleVariableDeclaration param : params) {
        String name = param.getName().getFullyQualifiedName();
        Type type = param.getType();
        String paramString = (isFinal(param) ? "final" : "") + " " + type.toString() + " " + name;
        paramStringList.add(paramString);
      }
      String paramString = String.join(", ", paramStringList).trim();
      builder.insert(0, parentMethod.getName() + "(" + paramString + ")" + ":");
    } else if (parent instanceof FieldDeclaration) {
      FieldDeclaration parentField = (FieldDeclaration) parent;
      VariableDeclarationFragment fragment =
          (VariableDeclarationFragment) parentField.fragments().get(0);
      builder.insert(0, fragment.getName() + ":");
    } else if (parent instanceof Initializer) {
      Initializer parentInitializer = (Initializer) parent;
      builder.insert(0, "INIT:");
    }

    while (parent != null && parent.getClass() != TypeDeclaration.class) {
      parent = parent.getParent();
    }
    if (parent != null) {
      builder.insert(0, getQualifiedNameForNamedType(((TypeDeclaration) parent)) + ":");
    }

    return builder.toString();
  }

  /**
   * Get the fully qualified name of a type declaration with name
   *
   * @param type
   * @return
   */
  public String getQualifiedNameForNamedType(AbstractTypeDeclaration type) {
    String name = type.getName().getIdentifier();
    ASTNode parent = type.getParent();
    // for inner type, resolve full name e.g.: A.B
    if (!type.isPackageMemberTypeDeclaration()) {
      while (parent != null && parent instanceof AbstractTypeDeclaration) {
        name = ((AbstractTypeDeclaration) parent).getName().getIdentifier() + "." + name;
        parent = parent.getParent();
      }
    }

    // resolve fully qualified name e.g.: some.package.A.B
    // if it is in a package
    if (type.getRoot() instanceof CompilationUnit) {
      String packageName = getPackageName(type);
      if (!packageName.isEmpty()) {
        name = packageName + "." + name;
      }
    }
    return name;
  }

  /**
   * Get the package name of a type, if it has
   *
   * @param decl
   * @return
   */
  public String getPackageName(BodyDeclaration decl) {
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
  public InterfaceInfo createInterfaceInfo(Integer fileIndex, TypeDeclaration node) {
    InterfaceInfo interfaceInfo = new InterfaceInfo();
    interfaceInfo.name = node.getName().getFullyQualifiedName();
    interfaceInfo.fileIndex = fileIndex;
    interfaceInfo.fullName = NameResolver.getFullName(node);
    interfaceInfo.visibility = getVisibility(node);
    List<Type> superInterfaceList = node.superInterfaceTypes();
    for (Type superInterface : superInterfaceList) {
      interfaceInfo.superInterfaceTypeList.add(NameResolver.getFullName(superInterface));
    }

    interfaceInfo.typeUses.addAll(interfaceInfo.superInterfaceTypeList);
    //    if (node.getJavadoc() != null){
    //      interfaceInfo.comment =
    //          sourceContent.substring(
    //              node.getJavadoc().getStartPosition(),
    //              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    //    }
    //    interfaceInfo.content =
    //        sourceContent.substring(
    //            node.getStartPosition(), node.getStartPosition() + node.getLength());
    return interfaceInfo;
  }

  /**
   * Collect information in an anonymous class
   *
   * @param declaration
   * @return
   */
  public ClassInfo createAnonyClassInfo(
      AnonymousClassDeclaration declaration, String superClassName, String qualifiedName) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.name = "";
    classInfo.isAnonymous = true;
    classInfo.fullName = qualifiedName;
    classInfo.superClassType = superClassName;
    classInfo.content =
        sourceContent.substring(
            declaration.getStartPosition(),
            declaration.getStartPosition() + declaration.getLength());
    return classInfo;
  }

  /**
   * Collect information from class declaration
   *
   * @param node
   * @return
   */
  public ClassInfo createClassInfo(Integer fileIndex, TypeDeclaration node) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.fileIndex = fileIndex;
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
    //    if (node.getJavadoc() != null) {
    //      classInfo.comment =
    //          sourceContent.substring(
    //              node.getJavadoc().getStartPosition(),
    //              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    //    }
    //    classInfo.content =
    //          sourceContent.substring(
    //              node.getStartPosition(), node.getStartPosition() + node.getLength());
    List<String> annotationUses = getAnnotationUses(node.modifiers());

    classInfo.typeUses.addAll(annotationUses);
    classInfo.typeUses.add(classInfo.superClassType);
    classInfo.typeUses.addAll(classInfo.superInterfaceTypeList);
    return classInfo;
  }

  /**
   * Collect information from enum declaration
   *
   * @param node
   * @return
   */
  public EnumInfo createEnumInfo(Integer fileIndex, EnumDeclaration node) {
    EnumInfo enumInfo = new EnumInfo();
    enumInfo.fileIndex = fileIndex;
    enumInfo.name = node.getName().getFullyQualifiedName();
    enumInfo.fullName = NameResolver.getFullName(node);
    enumInfo.visibility = getVisibility(node);
    // not used so disabled to save memory
    //    if (node.getJavadoc() != null) {
    //      enumInfo.comment =
    //              sourceContent.substring(
    //                      node.getJavadoc().getStartPosition(),
    //                      node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    //    }
    //
    //    enumInfo.content =
    //              sourceContent.substring(
    //                      node.getStartPosition(), node.getStartPosition() + node.getLength());
    return enumInfo;
  }

  public AnnotationInfo createAnnotationInfo(AnnotationTypeDeclaration node) {
    AnnotationInfo annotationInfo = new AnnotationInfo();
    annotationInfo.name = node.getName().getFullyQualifiedName();
    annotationInfo.fullName = NameResolver.getFullName(node);
    return annotationInfo;
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
    if (node.getJavadoc() != null) {
      comment =
          sourceContent.substring(
              node.getJavadoc().getStartPosition(),
              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    }

    List<String> annotationUses = getAnnotationUses(node.modifiers());
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
      fieldInfo.typeUses.addAll(annotationUses);
      parseFieldInitializer(fieldInfo, fragment.getInitializer());

      fieldInfos.add(fieldInfo);
    }
    return fieldInfos;
  }

  /**
   * Detect annotation types
   *
   * @param modifiers
   * @return
   */
  private List<String> getAnnotationUses(List modifiers) {
    List<String> annotationTypes = new ArrayList<>();
    for (int i = 0; i < modifiers.size(); i++) {
      if (modifiers.get(i) instanceof Annotation) {
        Annotation annotation = (Annotation) modifiers.get(i);
        annotationTypes.add(annotation.getTypeName().getFullyQualifiedName());
        // process nested annotations: e.g.
        // @Table(indexes = {@Index(columnList = "name", name = "premiumlist_name_idx")})
        if (annotation instanceof NormalAnnotation) {
          List nested = new ArrayList();
          for (Object v : ((NormalAnnotation) annotation).values()) {
            if (v instanceof MemberValuePair) {
              if (((MemberValuePair) v).getValue() instanceof ArrayInitializer) {
                ((ArrayInitializer) ((MemberValuePair) v).getValue())
                    .expressions()
                    .forEach(exp -> nested.add(exp));
              }
            }
          }
          if (!nested.isEmpty()) {
            annotationTypes.addAll(getAnnotationUses(nested));
          }
        }
      }
    }
    return annotationTypes;
  }

  /**
   * Collect information from an EnumConstantDeclaration
   *
   * @return
   */
  public EnumConstantInfo createEnumConstantInfo(
      Integer fileIndex, EnumConstantDeclaration node, String belongTo) {
    EnumConstantInfo enumConstantInfo = new EnumConstantInfo();
    enumConstantInfo.name = node.getName().getFullyQualifiedName();
    enumConstantInfo.belongTo = belongTo;
    if (node.getJavadoc() != null) {
      enumConstantInfo.comment =
          sourceContent.substring(
              node.getJavadoc().getStartPosition(),
              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    }
    for (Object obj : node.arguments()) {
      if (obj instanceof ASTNode) {
        enumConstantInfo.arguments.add(((ASTNode) obj).toString());
      }
    }
    return enumConstantInfo;
  }

  public AnnotationMemberInfo createAnnotationMemberInfo(
      Integer fileIndex, AnnotationTypeMemberDeclaration node, String belongTo) {
    AnnotationMemberInfo memberInfo = new AnnotationMemberInfo();
    memberInfo.name = node.getName().getFullyQualifiedName();
    memberInfo.belongTo = belongTo;
    memberInfo.type = node.getType().toString();
    if (node.getDefault() != null) {
      memberInfo.defaultValue = node.getDefault().toString();
    }
    return memberInfo;
  }

  /**
   * Collect info inside an initializer block, which act like a method declaration
   *
   * @param fileIndex
   * @param node
   * @param belongTo
   * @return
   */
  public InitializerInfo createInitializerInfo(
      Integer fileIndex, Initializer node, String belongTo) {
    InitializerInfo info = new InitializerInfo();
    info.fileIndex = fileIndex;
    info.isStatic = isStatic(node);
    info.belongTo = belongTo;
    if (node.getJavadoc() != null) {
      info.comment =
          sourceContent.substring(
              node.getJavadoc().getStartPosition(),
              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    }
    info.body = node.getBody().toString();
    parseInitializerBody(info, node.getBody());
    return info;
  }

  /**
   * Collect information from a MethodDeclaration
   *
   *
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
    methodInfo.isConstructor = node.isConstructor();
    methodInfo.isAbstract = isAbstract(node);
    methodInfo.isFinal = isFinal(node);
    methodInfo.isStatic = isStatic(node);
    methodInfo.isSynchronized = isSynchronized(node);
    methodInfo.content =
        sourceContent.substring(
            node.getStartPosition(), node.getStartPosition() + node.getLength());
    if (node.getJavadoc() != null) {
      methodInfo.comment =
          sourceContent.substring(
              node.getJavadoc().getStartPosition(),
              node.getJavadoc().getStartPosition() + node.getJavadoc().getLength());
    }
    methodInfo.belongTo = belongTo;

    List<String> annotationUses = getAnnotationUses(node.modifiers());
    methodInfo.typeUses.addAll(annotationUses);

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
   * Get the unique identifier of a method declaraion, including the parameter string
   *
   * @param node
   * @return
   */
  public String getUniqueNameForMethod(String belongTo, MethodDeclaration node) {
    List<SingleVariableDeclaration> params = node.parameters();
    List<String> paramStringList = new ArrayList<>();
    for (SingleVariableDeclaration param : params) {
      String name = param.getName().getFullyQualifiedName();
      Type type = param.getType();
      String paramString = (isFinal(param) ? "final" : "") + " " + type.toString() + " " + name;
      paramStringList.add(paramString);
    }
    String paramString = String.join(", ", paramStringList).trim();
    if (belongTo.trim().equals("")) {
      return node.getName() + "(" + paramString + ")";
    } else {
      return belongTo + ":" + node.getName() + "(" + paramString + ")";
    }
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
      List<ASTNode> extended = ((InfixExpression) expression).extendedOperands();
      for (ASTNode exp : extended) {
        if (exp instanceof Expression) {
          parseFieldInitializer(fieldInfo, (Expression) exp);
        }
      }
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
      if (methodBinding != null) {
        fieldInfo.methodCalls.add(methodBinding);
      }
      Expression caller = ((MethodInvocation) expression).getExpression();
      // support static method invocation
      if (caller instanceof Name && caller != null) {
        if (Character.isUpperCase(caller.toString().codePointAt(0))) {
          fieldInfo.typeUses.add(caller.toString());
        }
      }
      for (Expression exp : arguments) parseFieldInitializer(fieldInfo, exp);
      parseFieldInitializer(fieldInfo, ((MethodInvocation) expression).getExpression());
    }
    if (expression.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION) {
      IMethodBinding constructorBinding =
          ((ClassInstanceCreation) expression).resolveConstructorBinding();
      if (constructorBinding != null) {
        fieldInfo.typeUses.add(constructorBinding.getDeclaringClass().getQualifiedName());
      } else {
        fieldInfo.typeUses.addAll(getTypes(((ClassInstanceCreation) expression).getType()));
      }
      List<Expression> arguments = ((ClassInstanceCreation) expression).arguments();
      for (Expression exp : arguments) {
        parseExpression(fieldInfo, exp);
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
   * Parse the body of initializer block
   *
   * @param info
   * @param body
   */
  private void parseInitializerBody(InitializerInfo info, Block body) {
    if (body == null) return;
    List<Statement> statementList = body.statements();
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
      if (statement.getNodeType() == ASTNode.RETURN_STATEMENT) {
        Expression expression = ((ReturnStatement) statement).getExpression();
        if (expression != null) {
          parseExpression(info, expression);
        }
      }
      if (statement.getNodeType() == ASTNode.ASSERT_STATEMENT) {
        Expression expression = ((AssertStatement) statement).getExpression();
        if (expression != null) {
          parseExpression(info, expression);
        }
        expression = ((AssertStatement) statement).getMessage();
        if (expression != null) {
          parseExpression(info, expression);
        }
      }

      if (statement.getNodeType() == ASTNode.DO_STATEMENT) {
        Expression expression = ((DoStatement) statement).getExpression();
        if (expression != null) {
          parseExpression(info, expression);
        }
        Statement doBody = ((DoStatement) statement).getBody();
        if (doBody != null) {
          statements.add(i + 1, doBody);
        }
      }
      if (statement.getNodeType() == ASTNode.ENHANCED_FOR_STATEMENT) {
        Expression expression = ((EnhancedForStatement) statement).getExpression();
        Type type = ((EnhancedForStatement) statement).getParameter().getType();
        info.typeUses.addAll(getTypes(type));
        if (expression != null) {
          parseExpression(info, expression);
        }
        Statement forBody = ((EnhancedForStatement) statement).getBody();
        if (forBody != null) {
          statements.add(i + 1, forBody);
        }
      }
      if (statement.getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
        Expression expression = ((ExpressionStatement) statement).getExpression();
        if (expression != null) {
          parseExpression(info, expression);
        }
      }
      if (statement.getNodeType() == ASTNode.FOR_STATEMENT) {
        List<Expression> list = ((ForStatement) statement).initializers();
        for (int j = 0; j < list.size(); j++) {
          parseExpression(info, list.get(j));
        }
        Expression expression = ((ForStatement) statement).getExpression();
        if (expression != null) {
          parseExpression(info, expression);
        }
        Statement forBody = ((ForStatement) statement).getBody();
        if (forBody != null) {
          statements.add(i + 1, forBody);
        }
      }
      if (statement.getNodeType() == ASTNode.IF_STATEMENT) {
        Expression expression = ((IfStatement) statement).getExpression();
        if (expression != null) {
          parseExpression(info, expression);
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
      if (statement.getNodeType() == ASTNode.SWITCH_STATEMENT) {
        Expression expression = ((SwitchStatement) statement).getExpression();
        if (expression != null) {
          parseExpression(info, expression);
        }
        List<Statement> switchStatements = ((SwitchStatement) statement).statements();
        for (int j = 0; j < switchStatements.size(); j++) {
          statements.add(i + j + 1, switchStatements.get(j));
        }
      }
      if (statement.getNodeType() == ASTNode.THROW_STATEMENT) {
        Expression expression = ((ThrowStatement) statement).getExpression();
        if (expression != null) {
          parseExpression(info, expression);
        }
      }
      if (statement.getNodeType() == ASTNode.TRY_STATEMENT) {
        Statement tryStatement = ((TryStatement) statement).getBody();
        if (tryStatement != null) {
          statements.add(i + 1, tryStatement);
        }
        List<CatchClause> catchClauses = ((TryStatement) statement).catchClauses();
        if (catchClauses != null && !catchClauses.isEmpty()) {
          for (CatchClause catchClause : catchClauses) {
            info.typeUses.addAll(getTypes(catchClause.getException().getType()));
            // use a temp MethodInfo to collect information
            MethodInfo temp = new MethodInfo();
            temp.name = "CATCH";
            parseMethodBody(temp, catchClause.getBody());
            info.typeUses.addAll(temp.typeUses);
            info.fieldUses.addAll(temp.fieldUses);
            info.methodCalls.addAll(temp.methodCalls);
          }
        }
        continue;
      }
      if (statement.getNodeType() == ASTNode.VARIABLE_DECLARATION_STATEMENT) {
        Type type = ((VariableDeclarationStatement) statement).getType();
        List<VariableDeclaration> list = ((VariableDeclarationStatement) statement).fragments();
        info.typeUses.addAll(getTypes(type));
        for (VariableDeclaration decStat : list) {
          parseExpression(info, decStat.getInitializer());
        }
      }
      if (statement.getNodeType() == ASTNode.WHILE_STATEMENT) {
        Expression expression = ((WhileStatement) statement).getExpression();
        if (expression != null) {
          parseExpression(info, expression);
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
          info.typeUses.add(constructorBinding.getDeclaringClass().getQualifiedName());
        }
        List<Expression> arguments = ((ConstructorInvocation) statement).arguments();
        for (Expression exp : arguments) {
          parseExpression(info, exp);
        }
      }

      if (statement.getNodeType() == ASTNode.SYNCHRONIZED_STATEMENT) {
        SynchronizedStatement syncSt = ((SynchronizedStatement) statement);
        parseExpression(info, syncSt.getExpression());

        MethodInfo temp = new MethodInfo();
        temp.name = "SYNC";
        parseMethodBody(temp, syncSt.getBody());
        info.typeUses.addAll(temp.typeUses);
        info.fieldUses.addAll(temp.fieldUses);
        info.methodCalls.addAll(temp.methodCalls);
      }
    }
  }

  /**
   * Parse the method body block to collect useful information
   *
   *
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
      if (statement.getNodeType() == ASTNode.RETURN_STATEMENT) {
        Expression expression = ((ReturnStatement) statement).getExpression();
        if (expression != null) {
          parseExpressionInMethod(methodInfo, expression);
        }
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
        List<CatchClause> catchClauses = ((TryStatement) statement).catchClauses();
        if (catchClauses != null && !catchClauses.isEmpty()) {
          for (CatchClause catchClause : catchClauses) {
            methodInfo.typeUses.addAll(getTypes(catchClause.getException().getType()));
            // use a temp MethodInfo to collect information
            MethodInfo temp = new MethodInfo();
            temp.name = "CATCH";
            parseMethodBody(temp, catchClause.getBody());
            methodInfo.typeUses.addAll(temp.typeUses);
            methodInfo.fieldUses.addAll(temp.fieldUses);
            methodInfo.methodCalls.addAll(temp.methodCalls);
          }
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
        List<Expression> arguments = ((ConstructorInvocation) statement).arguments();
        for (Expression exp : arguments) {
          parseExpression(methodInfo, exp);
        }
      }

      if (statement.getNodeType() == ASTNode.SYNCHRONIZED_STATEMENT) {
        SynchronizedStatement syncSt = ((SynchronizedStatement) statement);
        parseExpressionInMethod(methodInfo, syncSt.getExpression());

        MethodInfo temp = new MethodInfo();
        temp.name = "SYNC";
        parseMethodBody(temp, syncSt.getBody());
        methodInfo.typeUses.addAll(temp.typeUses);
        methodInfo.fieldUses.addAll(temp.fieldUses);
        methodInfo.methodCalls.addAll(temp.methodCalls);
      }
    }
  }

  /**
   * Parse the expressions to get method calls and filed uses
   *
   *
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
      List<ASTNode> extended = ((InfixExpression) expression).extendedOperands();
      for (ASTNode exp : extended) {
        if (exp instanceof Expression) {
          parseExpressionInMethod(methodInfo, (Expression) exp);
        }
      }
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
      } else {
        methodInfo.typeUses.addAll(getTypes(((ClassInstanceCreation) expression).getType()));
      }
      List<Expression> arguments = ((ClassInstanceCreation) expression).arguments();
      for (Expression exp : arguments) {
        parseExpression(methodInfo, exp);
      }
    }
    if (expression.getNodeType() == ASTNode.METHOD_INVOCATION) {
      List<Expression> arguments = ((MethodInvocation) expression).arguments();
      IMethodBinding methodBinding = ((MethodInvocation) expression).resolveMethodBinding();
      if (methodBinding != null) methodInfo.methodCalls.add(methodBinding);
      // support static method invocation
      Expression caller = ((MethodInvocation) expression).getExpression();
      if (caller instanceof SimpleName && caller != null) {
        if (Character.isUpperCase(caller.toString().codePointAt(0))) {
          methodInfo.typeUses.add(caller.toString());
        }
      }
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
      } else {
        // support static field access
        String qualifier = ((QualifiedName) expression).getQualifier().getFullyQualifiedName();
        if (Character.isUpperCase(qualifier.codePointAt(0))) {
          methodInfo.typeUses.add(qualifier);
          methodInfo.fieldUses.add(
              qualifier + ":" + ((QualifiedName) expression).getName().getIdentifier());
        }
      }

      parseExpressionInMethod(methodInfo, ((QualifiedName) expression).getQualifier());
    }

    if (expression.getNodeType() == ASTNode.LAMBDA_EXPRESSION) {
      ASTNode body = ((LambdaExpression) expression).getBody();
      if (body instanceof Block) {
        // use a temp MethodInfo to collect information
        MethodInfo temp = new MethodInfo();
        temp.name = "ANONYMOUS";
        parseMethodBody(temp, (Block) body);
        methodInfo.typeUses.addAll(temp.typeUses);
        methodInfo.fieldUses.addAll(temp.fieldUses);
        methodInfo.methodCalls.addAll(temp.methodCalls);
      } else if (body instanceof Expression) {
        parseExpressionInMethod(methodInfo, (Expression) body);
      }
    }

    if (expression.getNodeType() == ASTNode.SIMPLE_NAME) {
      // resolve the simple name to determine whether it is a local var, para, or self field
      IBinding binding = ((SimpleName) expression).resolveBinding();
      if (binding != null && binding instanceof IVariableBinding) {
        if (((IVariableBinding) binding).isField()) {
          methodInfo.fieldUses.add(
              methodInfo.belongTo + ":" + ((SimpleName) expression).getIdentifier());
        }
      }
    }
  }

  /**
   * Collect information from statements
   *
   * @param entityInfo
   * @param statement
   */
  public void parseStatement(DeclarationInfo entityInfo, Statement statement) {
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
        List<CatchClause> catchClauses = ((TryStatement) current).catchClauses();
        if (catchClauses != null && !catchClauses.isEmpty()) {
          for (CatchClause catchClause : catchClauses) {
            entityInfo.typeUses.addAll(getTypes(catchClause.getException().getType()));
            // use a temp MethodInfo to collect information
            MethodInfo methodInfo = new MethodInfo();
            methodInfo.name = "CATCH";
            parseMethodBody(methodInfo, catchClause.getBody());
            entityInfo.typeUses.addAll(methodInfo.typeUses);
            entityInfo.fieldUses.addAll(methodInfo.fieldUses);
            entityInfo.methodCalls.addAll(methodInfo.methodCalls);
          }
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
        if (statement instanceof ConstructorInvocation) {
          List<Expression> arguments = ((ConstructorInvocation) statement).arguments();
          for (Expression exp : arguments) {
            parseExpression(entityInfo, exp);
          }
        }
      }

      if (statement.getNodeType() == ASTNode.SYNCHRONIZED_STATEMENT) {
        SynchronizedStatement syncSt = ((SynchronizedStatement) statement);
        parseExpression(entityInfo, syncSt.getExpression());

        MethodInfo temp = new MethodInfo();
        temp.name = "SYNC";
        parseMethodBody(temp, syncSt.getBody());
        entityInfo.typeUses.addAll(temp.typeUses);
        entityInfo.fieldUses.addAll(temp.fieldUses);
        entityInfo.methodCalls.addAll(temp.methodCalls);
      }
    }
  }

  /**
   * Parse expression
   *
   * @param entityInfo
   * @param expression
   */
  public void parseExpression(DeclarationInfo entityInfo, Expression expression) {
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
      List<ASTNode> extended = ((InfixExpression) expression).extendedOperands();
      for (ASTNode exp : extended) {
        if (exp instanceof Expression) {
          parseExpression(entityInfo, (Expression) exp);
        }
      }
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
        if (fieldAccess.resolveFieldBinding() != null) {
          entityInfo.fieldUses.add(
              fieldAccess.resolveFieldBinding().getDeclaringClass().getQualifiedName()
                  + ":"
                  + fieldAccess.getName());
        }

      } else {
        parseExpression(entityInfo, ((FieldAccess) expression).getExpression());
      }
    }

    if (expression.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION) {
      IMethodBinding constructorBinding =
          ((ClassInstanceCreation) expression).resolveConstructorBinding();
      if (constructorBinding != null) {
        entityInfo.typeUses.add(constructorBinding.getDeclaringClass().getQualifiedName());
      } else {
        entityInfo.typeUses.addAll(getTypes(((ClassInstanceCreation) expression).getType()));
      }
      List<Expression> arguments = ((ClassInstanceCreation) expression).arguments();
      for (Expression exp : arguments) {
        parseExpression(entityInfo, exp);
      }
    }
    if (expression.getNodeType() == ASTNode.METHOD_INVOCATION) {
      IMethodBinding methodBinding = ((MethodInvocation) expression).resolveMethodBinding();
      if (methodBinding != null) {
        entityInfo.methodCalls.add(methodBinding);
      }
      // support static method invocation
      Expression caller = ((MethodInvocation) expression).getExpression();
      if (caller instanceof Name && caller != null) {
        if (Character.isUpperCase(caller.toString().codePointAt(0))) {
          entityInfo.typeUses.add(caller.toString());
        }
      }
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

    // PersistenceModule.PERSISTENCE_UNIT_NAME
    if (expression.getNodeType() == ASTNode.QUALIFIED_NAME) {
      ITypeBinding typeBinding = ((QualifiedName) expression).getQualifier().resolveTypeBinding();
      if (typeBinding != null) {
        String name =
            typeBinding.getQualifiedName()
                + ":"
                + ((QualifiedName) expression).getName().getIdentifier();
        entityInfo.typeUses.add(typeBinding.getQualifiedName());
        entityInfo.fieldUses.add(name);
      }
      parseExpression(entityInfo, ((QualifiedName) expression).getQualifier());
    }
    if (expression.getNodeType() == ASTNode.SIMPLE_NAME) {
      IBinding binding = ((SimpleName) expression).resolveBinding();
      if (binding != null) {
        if (binding instanceof IVariableBinding) {
          IVariableBinding varBinding = ((IVariableBinding) binding);
          if (varBinding.isField()) {
            entityInfo.fieldUses.add(
                varBinding.getDeclaringClass().getQualifiedName() + ":" + binding.getName());
          } else if (varBinding.isParameter() && varBinding.getDeclaringMethod() != null) {
            entityInfo.paraUses.add(
                varBinding.getDeclaringMethod().getDeclaringClass().getQualifiedName()
                    + ":"
                    + varBinding.getDeclaringMethod().getName()
                    + ":"
                    + varBinding.getName());
          } else {
            if (varBinding.getDeclaringMethod() != null) {
              entityInfo.localVarUses.add(
                  varBinding.getDeclaringMethod().getDeclaringClass().getQualifiedName()
                      + ":"
                      + varBinding.getDeclaringMethod().getName()
                      + ":"
                      + varBinding.getName());

            } else {
              entityInfo.localVarUses.add(varBinding.getName());
            }
          }
        }
      }
    }

    // lambda expression (ANONYMOUS method declaration): ()->{}
    if (expression.getNodeType() == ASTNode.LAMBDA_EXPRESSION) {
      ASTNode body = ((LambdaExpression) expression).getBody();
      if (body instanceof Block) {
        // use a temp MethodInfo to collect information
        MethodInfo methodInfo = new MethodInfo();
        methodInfo.name = "ANONYMOUS";
        parseMethodBody(methodInfo, (Block) body);
        entityInfo.typeUses.addAll(methodInfo.typeUses);
        entityInfo.fieldUses.addAll(methodInfo.fieldUses);
        entityInfo.methodCalls.addAll(methodInfo.methodCalls);

      } else if (body instanceof Expression) {
        parseExpression(entityInfo, (Expression) body);
      }
    }

    // .forEach(System.out::println)
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
