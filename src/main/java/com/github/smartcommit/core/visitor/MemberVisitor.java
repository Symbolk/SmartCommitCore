package com.github.smartcommit.core.visitor;

import com.github.smartcommit.model.EntityPool;
import com.github.smartcommit.model.entity.ClassInfo;
import com.github.smartcommit.model.entity.FieldInfo;
import com.github.smartcommit.model.entity.InterfaceInfo;
import com.github.smartcommit.model.entity.MethodInfo;
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
  private EntityPool entityPool;
  private Graph<Node, Edge> graph;
  private JDTService jdtService;

  public MemberVisitor(EntityPool entityPool, Graph<Node, Edge> graph, JDTService jdtService) {
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
  public boolean visit(TypeDeclaration type) {
    if (type.isInterface()) {
      InterfaceInfo interfaceInfo = jdtService.createInterfaceInfo(type);
      entityPool.interfaceInfoMap.put(interfaceInfo.fullName, interfaceInfo);
    } else {
      ClassInfo classInfo = jdtService.createClassInfo(type);
      entityPool.classInfoMap.put(classInfo.fullName, classInfo);
    }
    // create the node for the current type declaration
    NodeType nodeType = type.isInterface() ? NodeType.INTERFACE : NodeType.CLASS;
    String qualifiedNameForType = jdtService.getQualifiedNameForType(type);
    Node typeNode =
        new Node(generateNodeID(), nodeType, type.getName().getIdentifier(), qualifiedNameForType);
    graph.addVertex(typeNode);

    // find and link with the package node
    String packageName = jdtService.getPackageName(type);
    if (!packageName.isEmpty()) {
      Node pkgNode = getOrCreatePkgNode(packageName);
      graph.addEdge(pkgNode, typeNode, new Edge(generateEdgeID(), EdgeType.CONTAIN));
    }

    // process the members inside the current type
    FieldDeclaration[] fieldDeclarations = type.getFields();
    for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
      // each field declaration can declare multiple fields with the common properties
      List<FieldInfo> fieldInfos =
          jdtService.createFieldInfos(fieldDeclaration, qualifiedNameForType);
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
      Node methodNode =
          new Node(
              generateNodeID(),
              NodeType.METHOD,
              methodDeclaration.getName().getFullyQualifiedName(),
              qualifiedNameForType + ":" + methodDeclaration.getName().getFullyQualifiedName());
      graph.addVertex(methodNode);
      graph.addEdge(typeNode, methodNode, new Edge(generateEdgeID(), EdgeType.DEFINE));

      MethodInfo methodInfo = jdtService.createMethodInfo(methodDeclaration, qualifiedNameForType);
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
