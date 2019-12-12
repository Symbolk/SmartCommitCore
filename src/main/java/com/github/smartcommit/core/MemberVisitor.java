package com.github.smartcommit.core;

import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.EdgeType;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.model.graph.NodeType;
import org.eclipse.jdt.core.dom.*;
import org.jgrapht.Graph;

import java.util.List;

public class MemberVisitor extends ASTVisitor {
  private Graph<Node, Edge> graph;

  public MemberVisitor(Graph<Node, Edge> graph) {
    this.graph = graph;
  }

  @Override
  public boolean visit(TypeDeclaration declaration) {
    NodeType nodeType = declaration.isInterface() ? NodeType.INTERFACE : NodeType.CLASS;
    Node typeNode =
        new Node(
            generateNodeID(),
            nodeType,
            declaration.getName().getIdentifier(),
            declaration.getName().getFullyQualifiedName());
    graph.addVertex(typeNode);

    FieldDeclaration[] fieldDeclarations = declaration.getFields();
    for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
      List<VariableDeclarationFragment> fragments = fieldDeclaration.fragments();
      for (VariableDeclarationFragment fragment : fragments) {
        Node fieldNode =
            new Node(
                generateNodeID(),
                NodeType.FIELD,
                fragment.getName().getIdentifier(),
                fragment.getName().getFullyQualifiedName());
        graph.addVertex(fieldNode);
        graph.addEdge(typeNode, fieldNode, new Edge(generateEdgeID(), EdgeType.DEFINE));
      }
    }

    MethodDeclaration[] methodDeclarations = declaration.getMethods();
    for (MethodDeclaration methodDeclaration : methodDeclarations) {
      Node methodNode =
          new Node(
              generateNodeID(),
              NodeType.METHOD,
              methodDeclaration.getName().toString(),
              methodDeclaration.getName().getFullyQualifiedName());
      graph.addVertex(methodNode);
      graph.addEdge(typeNode, methodNode, new Edge(generateEdgeID(), EdgeType.DEFINE));
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
}
