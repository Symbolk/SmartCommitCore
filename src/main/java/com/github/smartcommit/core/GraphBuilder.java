package com.github.smartcommit.core;

import com.github.smartcommit.core.visitor.MemberVisitor;
import com.github.smartcommit.io.GraphExporter;
import com.github.smartcommit.model.EntityPool;
import com.github.smartcommit.model.entity.FieldInfo;
import com.github.smartcommit.model.entity.MethodInfo;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.EdgeType;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.JDTService;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

public class GraphBuilder implements Callable<Graph<Node, Edge>> {

  private String srcDir;

  public GraphBuilder(String srcDir) {
    this.srcDir = srcDir;
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
    EntityPool entityPool = new EntityPool(srcDir);
    Graph<Node, Edge> graph = initGraph();

    // get all java files by extension in the source directory
    Collection<File> javaFiles = FileUtils.listFiles(new File(srcDir), new String[] {"java"}, true);
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

    ASTParser parser = ASTParser.newParser(AST.JLS9);
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

    // Vertex: create nodes when visiting the ASTs
    parser.createASTs(
        srcPaths,
        null,
        new String[] {},
        new FileASTRequestor() {
          @Override
          public void acceptAST(String sourceFilePath, CompilationUnit cu) {
            try {
              cu.accept(
                  new MemberVisitor(
                      entityPool,
                      graph,
                      new JDTService(FileUtils.readFileToString(new File(sourceFilePath)))));
              System.out.println(cu.getAST().hasBindingsRecovery());
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        },
        null);

    // Edge: create inter-entity edges with the EntityPool and EntityInfo
    int edgeCount = graph.edgeSet().size();
    Map<String, MethodInfo> methodDecMap = entityPool.methodInfoMap;
    Map<String, FieldInfo> fieldDecMap = entityPool.fieldInfoMap;
    Map<IMethodBinding, MethodInfo> methodBindingMap = new HashMap<>();
    // create nodes for type/field/method
    for (MethodInfo methodInfo : entityPool.methodInfoMap.values()) {
      methodBindingMap.put(methodInfo.methodBinding, methodInfo);
    }

    for (MethodInfo methodInfo : methodDecMap.values()) {
      Set<IMethodBinding> methodCalls = methodInfo.methodCalls;
      for (IMethodBinding methodCall : methodCalls) {
        MethodInfo targetMethodInfo = methodBindingMap.get(methodCall);
        if (targetMethodInfo != null) {
          graph.addEdge(
              methodInfo.node, targetMethodInfo.node, new Edge(edgeCount++, EdgeType.CALL));
        }
      }

      Set<String> fieldUses = methodInfo.fieldUses;
      for (String fieldUse : fieldUses) {
        FieldInfo targetFieldInfo = fieldDecMap.get(fieldUse);
        if (targetFieldInfo != null) {
          graph.addEdge(
              methodInfo.node, targetFieldInfo.node, new Edge(edgeCount++, EdgeType.ACCESS));
        }
      }
    }

    String graphDotString = GraphExporter.exportAsDotWithType(graph);

    return graph;
  }
}
