package com.github.smartcommit.core;

import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

public class GraphBuilder implements Callable<Graph<Node, Edge>> {

  private String tempDirPath;

  public GraphBuilder(String tempDirPath) {
    this.tempDirPath = tempDirPath;
  }

  /**
   * Initialize an empty Graph
   *
   * @return
   */
  public static Graph<Node, Edge> initGraph() {
    return GraphTypeBuilder.<Node, Edge>directed()
        .allowingMultipleEdges(true)
        .allowingSelfLoops(true) // recursion
        .edgeClass(Edge.class)
        .weighted(true)
        .buildGraph();
  }

  /**
   * Build the graph from java files
   *
   * @return
   */
  @Override
  public Graph<Node, Edge> call() {
    Graph<Node, Edge> graph = initGraph();
    // parse diff java files into CUs
    final Map<ICompilationUnit, ASTNode> parsedCompilationUnits =
        new HashMap<ICompilationUnit, ASTNode>();
    ASTParser parser = ASTParser.newParser(AST.JLS9);

    Collection<File> javaFiles =
        FileUtils.listFiles(new File(tempDirPath), new String[] {"java"}, true);
    Set<String> srcPathSet = new HashSet<>();
    Set<String> srcFolderSet = new HashSet<>();
    for (File javaFile : javaFiles) {
      String srcPath = javaFile.getAbsolutePath();
      String srcFolderPath = javaFile.getParentFile().getAbsolutePath();
      srcPathSet.add(srcPath);
      srcFolderSet.add(srcFolderPath);
    }

    String[] srcPaths = new String[srcPathSet.size()];
    srcPathSet.toArray(srcPaths);
    String[] srcFolderPaths = new String[srcFolderSet.size()];
    srcFolderSet.toArray(srcFolderPaths);

    //        parser.setProject(WorkspaceUtilities.javaProject);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setEnvironment(null, srcFolderPaths, null, true);
    parser.setResolveBindings(true);
    Map<String, String> options = new Hashtable<>();
    options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
    options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
    options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
    parser.setCompilerOptions(options);
    parser.setBindingsRecovery(true);
    parser.createASTs(
        srcPaths,
        null,
        new String[] {},
        new FileASTRequestor() {
          @Override
          public void acceptAST(String sourceFilePath, CompilationUnit cu) {
            try {
              cu.accept(new MemberVisitor(graph));
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        },
        null);

    // visit members to build graph

    // expand or highlight diff subtrees
    return graph;
  }
}
