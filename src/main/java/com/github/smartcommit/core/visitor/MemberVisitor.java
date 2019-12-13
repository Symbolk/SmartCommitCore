package com.github.smartcommit.core.visitor;

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
  private Graph<Node, Edge> graph;
  private JDTService service;

  public MemberVisitor(Graph<Node, Edge> graph, JDTService service) {
    this.graph = graph;
    this.service = service;
  }

  @Override
  public boolean visit(PackageDeclaration node) {
    Node pkgNode = getOrCreatePkgNode(node.getName().getFullyQualifiedName());
    return true;
  }

  @Override
  public boolean visit(TypeDeclaration type) {
    NodeType nodeType = type.isInterface() ? NodeType.INTERFACE : NodeType.CLASS;
    String qualifiedNameForType = service.getQualifiedNameForType(type);
    Node typeNode =
        new Node(generateNodeID(), nodeType, type.getName().getIdentifier(), qualifiedNameForType);
    //            type.getName().getFullyQualifiedName());
    graph.addVertex(typeNode);

    // find and link with the package node
    String packageName = service.getPackageName(type);
    if (!packageName.isEmpty()) {
      Node pkgNode = getOrCreatePkgNode(packageName);
      graph.addEdge(pkgNode, typeNode, new Edge(generateEdgeID(), EdgeType.CONTAIN));
    }

    // process the members inside the current type
    FieldDeclaration[] fieldDeclarations = type.getFields();
    for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
      List<VariableDeclarationFragment> fragments = fieldDeclaration.fragments();
      for (VariableDeclarationFragment fragment : fragments) {
        Node fieldNode =
            new Node(
                generateNodeID(),
                NodeType.FIELD,
                fragment.getName().getIdentifier(),
                qualifiedNameForType + ":" + fragment.getName().getFullyQualifiedName());
        graph.addVertex(fieldNode);
        graph.addEdge(typeNode, fieldNode, new Edge(generateEdgeID(), EdgeType.DEFINE));
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
      MethodInfo methodInfo = service.createMethodInfo(methodDeclaration, qualifiedNameForType);
      System.out.println(methodInfo);
      // build edges on graph
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
