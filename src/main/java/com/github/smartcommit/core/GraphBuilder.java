package com.github.smartcommit.core;

import com.github.smartcommit.core.visitor.MemberVisitor;
import com.github.smartcommit.io.GraphExporter;
import com.github.smartcommit.model.EntityPool;
import com.github.smartcommit.model.entity.ClassInfo;
import com.github.smartcommit.model.entity.FieldInfo;
import com.github.smartcommit.model.entity.InterfaceInfo;
import com.github.smartcommit.model.entity.MethodInfo;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.EdgeType;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.JDTService;
import com.github.smartcommit.util.NameResolver;
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
    NameResolver.setSrcPathSet(srcPathSet);
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

    // Vertex: create nodes and nesting edges while visiting the ASTs
    parser.createASTs(
        srcPaths,
        null,
        new String[] {},
        new FileASTRequestor() {
          @Override
          public void acceptAST(String sourceFilePath, CompilationUnit cu) {
            // get all diff hunks in the current file
            // get all covered statement nodes in each diff hunk
            // create nodes for statements in the graph
            // create edges for statements
            try {
              cu.accept(
                  new MemberVisitor(
                      entityPool,
                      graph,
                      new JDTService(FileUtils.readFileToString(new File(sourceFilePath)))));
              //              System.out.println(cu.getAST().hasBindingsRecovery());
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        },
        null);

    // Edge: create inter-entity edges with the EntityPool and EntityInfo
    int edgeCount = graph.edgeSet().size();
    Map<String, ClassInfo> classDecMap = entityPool.classInfoMap;
    Map<String, InterfaceInfo> interfaceDecMap = entityPool.interfaceInfoMap;
    Map<String, MethodInfo> methodDecMap = entityPool.methodInfoMap;
    Map<String, FieldInfo> fieldDecMap = entityPool.fieldInfoMap;
    Map<IMethodBinding, MethodInfo> methodBindingMap = new HashMap<>();
    for (MethodInfo methodInfo : entityPool.methodInfoMap.values()) {
      methodBindingMap.put(methodInfo.methodBinding, methodInfo);
    }

    // edges from method declaration
    for (MethodInfo methodInfo : methodDecMap.values()) {
      Node methodDeclNode = methodInfo.node;
      // method invocation
      for (IMethodBinding methodCall : methodInfo.methodCalls) {
        MethodInfo targetMethodInfo = methodBindingMap.get(methodCall);
        if (targetMethodInfo != null) {
          graph.addEdge(
              methodDeclNode, targetMethodInfo.node, new Edge(edgeCount++, EdgeType.CALL));
        }
      }

      // field access
      for (String fieldUse : methodInfo.fieldUses) {
        FieldInfo targetFieldInfo = fieldDecMap.get(fieldUse);
        if (targetFieldInfo != null) {
          graph.addEdge(
              methodDeclNode, targetFieldInfo.node, new Edge(edgeCount++, EdgeType.ACCESS));
        }
      }

      // param type
      for (String param : methodInfo.paramTypes) {
        ClassInfo targetClassInfo = classDecMap.get(param);
        if (targetClassInfo != null) {
          graph.addEdge(
              methodDeclNode, targetClassInfo.node, new Edge(edgeCount++, EdgeType.PARAM));
        }
        InterfaceInfo interfaceInfo = interfaceDecMap.get(param);
        if (interfaceInfo != null) {
          graph.addEdge(methodDeclNode, interfaceInfo.node, new Edge(edgeCount++, EdgeType.PARAM));
        }
      }

      // local var type
      for (String localVarType : methodInfo.localVarTypes) {
        ClassInfo targetClassInfo = classDecMap.get(localVarType);
        if (targetClassInfo != null) {
          graph.addEdge(
              methodDeclNode, targetClassInfo.node, new Edge(edgeCount++, EdgeType.INITIALIZE));
        }
        InterfaceInfo interfaceInfo = interfaceDecMap.get(localVarType);
        if (interfaceInfo != null) {
          graph.addEdge(methodDeclNode, interfaceInfo.node, new Edge(edgeCount++, EdgeType.INITIALIZE));
        }
      }
    }

    // edges from field declaration
    for (FieldInfo fieldInfo : fieldDecMap.values()) {
      Node fieldDeclNode = fieldInfo.node;
      // field type
      for (String type : fieldInfo.types) {
        ClassInfo targetClassInfo = classDecMap.get(type);
        if (targetClassInfo != null) {
          graph.addEdge(fieldDeclNode, targetClassInfo.node, new Edge(edgeCount++, EdgeType.TYPE));
        }
        InterfaceInfo interfaceInfo = interfaceDecMap.get(type);
        if (interfaceInfo != null) {
          graph.addEdge(fieldDeclNode, interfaceInfo.node, new Edge(edgeCount++, EdgeType.TYPE));
        }
      }

      // method invocation
      for (IMethodBinding methodCall : fieldInfo.methodCalls) {
        MethodInfo targetMethodInfo = methodBindingMap.get(methodCall);
        if (targetMethodInfo != null) {
          graph.addEdge(fieldDeclNode, targetMethodInfo.node, new Edge(edgeCount++, EdgeType.CALL));
        }
      }

      // type instance creation
      for (String localVarType : fieldInfo.typeInitializes) {
        ClassInfo targetClassInfo = classDecMap.get(localVarType);
        if (targetClassInfo != null) {
          graph.addEdge(fieldDeclNode, targetClassInfo.node, new Edge(edgeCount++, EdgeType.INITIALIZE));
        }
        InterfaceInfo interfaceInfo = interfaceDecMap.get(localVarType);
        if (interfaceInfo != null) {
          graph.addEdge(fieldDeclNode, interfaceInfo.node, new Edge(edgeCount++, EdgeType.INITIALIZE));
        }
      }
    }

    String graphDotString = GraphExporter.exportAsDotWithType(graph);
    return graph;
  }
}
