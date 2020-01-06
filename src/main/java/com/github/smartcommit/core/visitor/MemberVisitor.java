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

import java.util.List;
import java.util.Optional;

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
    String qualifiedName = jdtService.getQualifiedNameForType(node);
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

    List<AnnotationTypeMemberDeclaration> memberDeclarations = node.bodyDeclarations();
    for (AnnotationTypeMemberDeclaration declaration : memberDeclarations) {
      AnnotationMemberInfo memberInfo =
          jdtService.createAnnotationMemberInfo(fileIndex, declaration, qualifiedName);
      Node memberNode =
          new Node(
              generateNodeID(),
              NodeType.ANNOTATION_MEMBER,
              memberInfo.name,
              memberInfo.uniqueName());
      graph.addVertex(memberNode);
      graph.addEdge(enumNode, memberNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

      memberInfo.node = memberNode;
    }

    return true;
  }

  @Override
  public boolean visit(EnumDeclaration node) {
    String qualifiedName = jdtService.getQualifiedNameForType(node);
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
      if (nodeOpt.isPresent()) {
        graph.addEdge(nodeOpt.get(), enumNode, new Edge(generateEdgeID(), EdgeType.DEFINE));
      }
    }

    EnumInfo enumInfo = jdtService.createEnumInfo(node);
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

  @Override
  public boolean visit(TypeDeclaration type) {
    // create the node for the current type declaration
    NodeType nodeType = type.isInterface() ? NodeType.INTERFACE : NodeType.CLASS;
    //    nodeType = (type.isMemberTypeDeclaration() || type.isMemberTypeDeclaration()) ?
    // NodeType.INNER_CLASS : nodeType;
    String qualifiedNameForType = jdtService.getQualifiedNameForType(type);
    Node typeNode =
        new Node(generateNodeID(), nodeType, type.getName().getIdentifier(), qualifiedNameForType);
    graph.addVertex(typeNode);

    if (type.isInterface()) {
      InterfaceInfo interfaceInfo = jdtService.createInterfaceInfo(type);
      interfaceInfo.node = typeNode;
      entityPool.interfaceInfoMap.put(interfaceInfo.fullName, interfaceInfo);
    } else {
      ClassInfo classInfo = jdtService.createClassInfo(type);
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
      if (nodeOpt.isPresent()) {
        graph.addEdge(nodeOpt.get(), typeNode, new Edge(generateEdgeID(), EdgeType.DEFINE));
      }
    }

    // process the members inside the current type
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
