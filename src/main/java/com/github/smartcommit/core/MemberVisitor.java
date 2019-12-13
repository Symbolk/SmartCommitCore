package com.github.smartcommit.core;

import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.EdgeType;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.model.graph.NodeType;
import org.eclipse.jdt.core.dom.*;
import org.jgrapht.Graph;

import java.util.List;
import java.util.Optional;

public class MemberVisitor extends ASTVisitor {
  private Graph<Node, Edge> graph;

  public MemberVisitor(Graph<Node, Edge> graph) {
    this.graph = graph;
  }

  @Override
  public boolean visit(PackageDeclaration node) {
    Node pkgNode = getOrCreatePkgNode(node.getName().getFullyQualifiedName());
    return true;
  }

  @Override
  public boolean visit(TypeDeclaration type) {
    NodeType nodeType = type.isInterface() ? NodeType.INTERFACE : NodeType.CLASS;
    String qualifiedName = getQualifiedNameForType(type);
    Node typeNode =
        new Node(generateNodeID(), nodeType, type.getName().getIdentifier(), qualifiedName);
    //            type.getName().getFullyQualifiedName());
    graph.addVertex(typeNode);

    // find and link with the package node
    String packageName = getPackageName(type);
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
                qualifiedName + ":" + fragment.getName().getFullyQualifiedName());
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
              qualifiedName + ":" + methodDeclaration.getName().getFullyQualifiedName());
      graph.addVertex(methodNode);
      graph.addEdge(typeNode, methodNode, new Edge(generateEdgeID(), EdgeType.DEFINE));
      methodDeclaration.getBody().statements();
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
   * Get the fully qualified name of a type declaration
   *
   * @param type
   * @return
   */
  private static String getQualifiedNameForType(TypeDeclaration type) {
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

  private static String getPackageName(TypeDeclaration decl) {
    CompilationUnit root = (CompilationUnit) decl.getRoot();
    if (root.getPackage() != null) {
      PackageDeclaration pack = root.getPackage();
      return pack.getName().getFullyQualifiedName();
    }
    return "";
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
