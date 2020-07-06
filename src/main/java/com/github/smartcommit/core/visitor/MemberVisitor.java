package com.github.smartcommit.core.visitor;

import com.github.smartcommit.model.EntityPool;
import com.github.smartcommit.model.entity.*;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.EdgeType;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.model.graph.NodeType;
import com.github.smartcommit.util.JDTService;
import org.eclipse.jdt.core.dom.*;
import org.jgrapht.Graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MemberVisitor extends ASTVisitor {
  private Integer fileIndex;
  private EntityPool entityPool;
  private Graph<Node, Edge> graph;
  private JDTService jdtService;

  public MemberVisitor(
      Integer fileIndex, EntityPool entityPool, Graph<Node, Edge> graph, JDTService jdtService) {
    this.fileIndex = fileIndex;
    this.entityPool = entityPool;
    this.graph = graph;
    this.jdtService = jdtService;
  }

  @Override
  public boolean visit(PackageDeclaration node) {
    getOrCreatePkgNode(node.getName().getFullyQualifiedName());
    return true;
  }

  @Override
  public boolean visit(AnnotationTypeDeclaration node) {
    // Annotation types are a form of interface
    String qualifiedName = jdtService.getQualifiedNameForNamedType(node);
    Node enumNode =
        new Node(
            generateNodeID(), NodeType.ANNOTATION, node.getName().getIdentifier(), qualifiedName);
    graph.addVertex(enumNode);

    if (node.isPackageMemberTypeDeclaration()) {
      String packageName = jdtService.getPackageName(node);
      if (!packageName.isEmpty()) {
        Node pkgNode = getOrCreatePkgNode(packageName);
        graph.addEdge(pkgNode, enumNode, new Edge(generateEdgeID(), EdgeType.CONTAIN));
      }
    }
    AnnotationInfo annotationInfo = jdtService.createAnnotationInfo(node);
    annotationInfo.node = enumNode;
    entityPool.annotationInfoMap.put(annotationInfo.fullName, annotationInfo);

    // support all child types of BodyDeclaration, including: AnnotationTypeMemberDeclaration,
    // EnumDeclaration, etc.
    List<BodyDeclaration> bodyDeclarations =
        ((List<?>) node.bodyDeclarations())
            .stream()
                .filter(BodyDeclaration.class::isInstance)
                .map(BodyDeclaration.class::cast)
                .collect(Collectors.toList());
    // annotation type element declarations, which look a lot like methods
    for (BodyDeclaration member : bodyDeclarations) {
      if (member instanceof AnnotationTypeMemberDeclaration) {
        AnnotationMemberInfo memberInfo =
            jdtService.createAnnotationMemberInfo(
                fileIndex, (AnnotationTypeMemberDeclaration) member, qualifiedName);
        Node memberNode =
            new Node(
                generateNodeID(),
                NodeType.ANNOTATION_MEMBER,
                memberInfo.name,
                memberInfo.uniqueName());
        graph.addVertex(memberNode);
        graph.addEdge(enumNode, memberNode, new Edge(generateEdgeID(), EdgeType.DEFINE));
        memberInfo.node = memberNode;
      } else if (member instanceof EnumDeclaration) {
        visit((EnumDeclaration) member);
      }
    }

    return true;
  }

  @Override
  public boolean visit(EnumDeclaration node) {
    String qualifiedName = jdtService.getQualifiedNameForNamedType(node);
    Node enumNode =
        new Node(generateNodeID(), NodeType.ENUM, node.getName().getIdentifier(), qualifiedName);
    graph.addVertex(enumNode);

    if (node.isPackageMemberTypeDeclaration()) {
      String packageName = jdtService.getPackageName(node);
      if (!packageName.isEmpty()) {
        Node pkgNode = getOrCreatePkgNode(packageName);
        graph.addEdge(pkgNode, enumNode, new Edge(generateEdgeID(), EdgeType.CONTAIN));
      }
    } else if (node.isLocalTypeDeclaration() || node.isMemberTypeDeclaration()) {
      String parentTypeName = qualifiedName.replace("." + node.getName().getIdentifier(), "");
      Optional<Node> nodeOpt = getParentTypeNode(parentTypeName);
      nodeOpt.ifPresent(
          value -> graph.addEdge(value, enumNode, new Edge(generateEdgeID(), EdgeType.DEFINE)));
    }

    EnumInfo enumInfo = jdtService.createEnumInfo(fileIndex, node);
    enumInfo.node = enumNode;
    entityPool.enumInfoMap.put(enumInfo.fullName, enumInfo);

    for (Object obj : node.enumConstants()) {
      if (obj instanceof EnumConstantDeclaration) {
        EnumConstantInfo enumConstantInfo =
            jdtService.createEnumConstantInfo(
                fileIndex, (EnumConstantDeclaration) obj, qualifiedName);
        Node enumConstantNode =
            new Node(
                generateNodeID(),
                NodeType.ENUM_CONSTANT,
                enumConstantInfo.name,
                enumConstantInfo.uniqueName());
        graph.addVertex(enumConstantNode);
        graph.addEdge(enumNode, enumConstantNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

        enumConstantInfo.node = enumConstantNode;
        entityPool.enumConstantInfoMap.put(enumConstantInfo.uniqueName(), enumConstantInfo);
      }
    }

    for (Object obj : node.bodyDeclarations()) {
      if (obj instanceof FieldDeclaration) {
        // each field declaration can declare multiple fields with the common properties
        List<FieldInfo> fieldInfos =
            jdtService.createFieldInfos(fileIndex, (FieldDeclaration) obj, qualifiedName);
        for (FieldInfo fieldInfo : fieldInfos) {
          Node fieldNode =
              new Node(generateNodeID(), NodeType.FIELD, fieldInfo.name, fieldInfo.uniqueName());
          graph.addVertex(fieldNode);
          graph.addEdge(enumNode, fieldNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

          fieldInfo.node = fieldNode;

          entityPool.fieldInfoMap.put(fieldInfo.uniqueName(), fieldInfo);
        }
      } else if (obj instanceof MethodDeclaration) {
        MethodInfo methodInfo =
            jdtService.createMethodInfo(fileIndex, (MethodDeclaration) obj, qualifiedName);
        Node methodNode =
            new Node(generateNodeID(), NodeType.METHOD, methodInfo.name, methodInfo.uniqueName());
        graph.addVertex(methodNode);
        graph.addEdge(enumNode, methodNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

        methodInfo.node = methodNode;
        entityPool.methodInfoMap.put(methodInfo.uniqueName(), methodInfo);
      }
    }

    return true;
  }

  /**
   * Visit and process anonymous class declaration, which can be declared in
   * initializer/field/method
   *
   * <p>e.g. c.b.a:Field:SuperClass c.b.a:Method():SuperClass c.b.a::SuperClass
   *
   * @param declaration
   * @return
   */
  @Override
  public boolean visit(AnonymousClassDeclaration declaration) {
    // create vertex
    String superClassName = "";
    if (declaration.getParent() instanceof ClassInstanceCreation) {
      superClassName = ((ClassInstanceCreation) declaration.getParent()).getType().toString();
    } else if (declaration.getParent() instanceof EnumConstantDeclaration) {
      superClassName = ((EnumConstantDeclaration) declaration.getParent()).getName().toString();
    }

    String qualifiedName = jdtService.getQualifiedNameForAnonyType(declaration, superClassName);
    Node node = new Node(generateNodeID(), NodeType.ANONY_CLASS, superClassName, qualifiedName);
    graph.addVertex(node);
    // parse info
    ClassInfo classInfo =
        jdtService.createAnonyClassInfo(declaration, superClassName, qualifiedName);
    classInfo.node = node;
    entityPool.classInfoMap.put(classInfo.fullName, classInfo);

    // find parent node and create an edge
    if (qualifiedName.lastIndexOf(":") != -1) {
      Optional<Node> parentNodeOpt =
          getParentMemberNode(qualifiedName.substring(0, qualifiedName.lastIndexOf(":")));
      parentNodeOpt.ifPresent(
          parentNode ->
              graph.addEdge(parentNode, node, new Edge(generateEdgeID(), EdgeType.DEFINE)));
    }
    // parse member declarations and create vertices
    List<Initializer> initializers =
        ((List<?>) declaration.bodyDeclarations())
            .stream()
                .filter(Initializer.class::isInstance)
                .map(Initializer.class::cast)
                .collect(Collectors.toList());
    if (!initializers.isEmpty()) {
      for (Initializer initializer : initializers) {
        InitializerInfo info =
            jdtService.createInitializerInfo(fileIndex, initializer, qualifiedName);
        Node initNode =
            new Node(
                generateNodeID(), NodeType.INITIALIZER_BLOCK, info.uniqueName(), info.uniqueName());
        graph.addVertex(initNode);
        graph.addEdge(node, initNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

        info.node = initNode;

        entityPool.initBlockInfoMap.put(info.uniqueName(), info);
      }
    }

    List<FieldDeclaration> fieldDeclarations =
        ((List<?>) declaration.bodyDeclarations())
            .stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .collect(Collectors.toList());
    for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
      // each field declaration can declare multiple fields with the common properties
      List<FieldInfo> fieldInfos =
          jdtService.createFieldInfos(fileIndex, fieldDeclaration, qualifiedName);
      for (FieldInfo fieldInfo : fieldInfos) {
        Node fieldNode =
            new Node(generateNodeID(), NodeType.FIELD, fieldInfo.name, fieldInfo.uniqueName());
        graph.addVertex(fieldNode);
        graph.addEdge(node, fieldNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

        fieldInfo.node = fieldNode;

        entityPool.fieldInfoMap.put(fieldInfo.uniqueName(), fieldInfo);
      }
    }

    List<MethodDeclaration> methodDeclarations =
        ((List<?>) declaration.bodyDeclarations())
            .stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .collect(Collectors.toList());
    for (MethodDeclaration methodDeclaration : methodDeclarations) {
      MethodInfo methodInfo =
          jdtService.createMethodInfo(fileIndex, methodDeclaration, qualifiedName);
      Node methodNode =
          new Node(generateNodeID(), NodeType.METHOD, methodInfo.name, methodInfo.uniqueName());
      graph.addVertex(methodNode);
      graph.addEdge(node, methodNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

      methodInfo.node = methodNode;
      entityPool.methodInfoMap.put(methodInfo.uniqueName(), methodInfo);
    }

    return true;
  }

  @Override
  public boolean visit(TypeDeclaration type) {
    // create the node for the current type declaration
    NodeType nodeType = type.isInterface() ? NodeType.INTERFACE : NodeType.CLASS;
    //    nodeType = (type.isMemberTypeDeclaration() || type.isMemberTypeDeclaration()) ?
    // NodeType.INNER_CLASS : nodeType;
    String qualifiedNameForType = jdtService.getQualifiedNameForNamedType(type);
    Node typeNode =
        new Node(generateNodeID(), nodeType, type.getName().getIdentifier(), qualifiedNameForType);
    graph.addVertex(typeNode);

    if (type.isInterface()) {
      InterfaceInfo interfaceInfo = jdtService.createInterfaceInfo(fileIndex, type);
      interfaceInfo.node = typeNode;
      entityPool.interfaceInfoMap.put(interfaceInfo.fullName, interfaceInfo);
    } else {
      ClassInfo classInfo = jdtService.createClassInfo(fileIndex, type);
      classInfo.node = typeNode;
      entityPool.classInfoMap.put(classInfo.fullName, classInfo);
    }

    // find and link with the package node or the parent type node
    if (type.isPackageMemberTypeDeclaration()) {
      String packageName = jdtService.getPackageName(type);
      if (!packageName.isEmpty()) {
        Node pkgNode = getOrCreatePkgNode(packageName);
        graph.addEdge(pkgNode, typeNode, new Edge(generateEdgeID(), EdgeType.CONTAIN));
      }
    } else if (type.isLocalTypeDeclaration() || type.isMemberTypeDeclaration()) {
      String parentTypeName =
          qualifiedNameForType.replace("." + type.getName().getIdentifier(), "");
      Optional<Node> nodeOpt = getParentTypeNode(parentTypeName);
      nodeOpt.ifPresent(
          node -> graph.addEdge(node, typeNode, new Edge(generateEdgeID(), EdgeType.DEFINE)));
    }

    // process the members inside the current type
    List<Initializer> initializers = new ArrayList<>();
    for (Object child : type.bodyDeclarations()) {
      if (child instanceof Initializer) {
        initializers.add((Initializer) child);
      }
    }
    if (!initializers.isEmpty()) {
      for (Initializer initializer : initializers) {
        InitializerInfo info =
            jdtService.createInitializerInfo(fileIndex, initializer, qualifiedNameForType);
        Node initNode =
            new Node(
                generateNodeID(), NodeType.INITIALIZER_BLOCK, info.uniqueName(), info.uniqueName());
        graph.addVertex(initNode);
        graph.addEdge(typeNode, initNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

        info.node = initNode;

        entityPool.initBlockInfoMap.put(info.uniqueName(), info);
      }
    }

    FieldDeclaration[] fieldDeclarations = type.getFields();
    for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
      // each field declaration can declare multiple fields with the common properties
      List<FieldInfo> fieldInfos =
          jdtService.createFieldInfos(fileIndex, fieldDeclaration, qualifiedNameForType);
      for (FieldInfo fieldInfo : fieldInfos) {
        Node fieldNode =
            new Node(generateNodeID(), NodeType.FIELD, fieldInfo.name, fieldInfo.uniqueName());
        graph.addVertex(fieldNode);
        graph.addEdge(typeNode, fieldNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

        fieldInfo.node = fieldNode;

        entityPool.fieldInfoMap.put(fieldInfo.uniqueName(), fieldInfo);
      }
    }

    MethodDeclaration[] methodDeclarations = type.getMethods();
    for (MethodDeclaration methodDeclaration : methodDeclarations) {
      MethodInfo methodInfo =
          jdtService.createMethodInfo(fileIndex, methodDeclaration, qualifiedNameForType);
      Node methodNode =
          new Node(generateNodeID(), NodeType.METHOD, methodInfo.name, methodInfo.uniqueName());
      graph.addVertex(methodNode);
      graph.addEdge(typeNode, methodNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

      methodInfo.node = methodNode;
      entityPool.methodInfoMap.put(methodInfo.uniqueName(), methodInfo);
    }

    return true;
  }

  /**
   * Start from 1, 0 preserved for the project
   *
   * @return
   */
  private Integer generateNodeID() {
    return this.graph.vertexSet().size() + 1;
  }

  private Integer generateEdgeID() {
    return this.graph.edgeSet().size() + 1;
  }

  /**
   * Find the parent type node by qualified name in the graph
   *
   * @param qualifiedName
   * @return
   */
  private Optional<Node> getParentTypeNode(String qualifiedName) {
    return graph.vertexSet().stream()
        .filter(
            node ->
                (node.getType().equals(NodeType.CLASS) || node.getType().equals(NodeType.INTERFACE))
                    && node.getQualifiedName().equals(qualifiedName))
        .findAny();
  }

  /**
   * Find the parent method or field node of anonymous class
   *
   * @param qualifiedName
   * @return
   */
  private Optional<Node> getParentMemberNode(String qualifiedName) {
    return graph.vertexSet().stream()
        .filter(
            node ->
                (node.getType().equals(NodeType.METHOD)
                    || node.getType().equals(NodeType.FIELD)
                    || node.getType().equals(NodeType.INITIALIZER_BLOCK)))
        .filter(node -> node.getQualifiedName().equals(qualifiedName))
        .findAny();
  }

  /**
   * Return the package node if exists, or create it if not exists
   *
   * @param packageName
   * @return
   */
  private Node getOrCreatePkgNode(String packageName) {
    Optional<Node> pkgNodeOpt =
        graph.vertexSet().stream()
            .filter(
                node ->
                    node.getType().equals(NodeType.PACKAGE)
                        && node.getQualifiedName().equals(packageName))
            .findAny();
    if (pkgNodeOpt.isPresent()) {
      return pkgNodeOpt.get();
    } else {
      // create if not exist
      Node pkgNode = new Node(generateNodeID(), NodeType.PACKAGE, packageName, packageName);
      graph.addVertex(pkgNode);
      return pkgNode;
    }
  }
}
