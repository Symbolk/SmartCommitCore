package com.github.smartcommit.core;

import com.github.smartcommit.core.visitor.MemberVisitor;
import com.github.smartcommit.core.visitor.MyNodeFinder;
import com.github.smartcommit.io.GraphExporter;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.EntityPool;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.model.entity.*;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.EdgeType;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.model.graph.NodeType;
import com.github.smartcommit.util.JDTService;
import com.github.smartcommit.util.NameResolver;
import com.github.smartcommit.util.Utils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class GraphBuilder implements Callable<Graph<Node, Edge>> {

  private static final Logger logger = LoggerFactory.getLogger(GraphBuilder.class);

  private String srcDir;
  private List<DiffFile> diffFiles;
  private EntityPool entityPool;
  private Graph<Node, Edge> graph;

  public GraphBuilder(String srcDir) {
    this.srcDir = srcDir;
    this.diffFiles = new ArrayList<>();
  }

  public GraphBuilder(String srcDir, List<DiffFile> diffFiles) {
    this.srcDir = srcDir;
    this.diffFiles = diffFiles;
    this.entityPool = new EntityPool(srcDir);
    this.graph = initGraph();
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

    ASTParser parser = ASTParser.newParser(9);
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
            try {
              cu.accept(
                  new MemberVisitor(
                      entityPool,
                      graph,
                      new JDTService(FileUtils.readFileToString(new File(sourceFilePath)))));
              //              System.out.println(cu.getAST().hasBindingsRecovery());
              // collect hunk infos and nodes
              createHunkInfos(sourceFilePath, cu);
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
          graph.addEdge(
              methodDeclNode, interfaceInfo.node, new Edge(edgeCount++, EdgeType.INITIALIZE));
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
      for (String type : fieldInfo.typeInitializes) {
        ClassInfo targetClassInfo = classDecMap.get(type);
        if (targetClassInfo != null) {
          graph.addEdge(
              fieldDeclNode, targetClassInfo.node, new Edge(edgeCount++, EdgeType.INITIALIZE));
        }
        InterfaceInfo interfaceInfo = interfaceDecMap.get(type);
        if (interfaceInfo != null) {
          graph.addEdge(
              fieldDeclNode, interfaceInfo.node, new Edge(edgeCount++, EdgeType.INITIALIZE));
        }
      }
    }

    String graphDotString = GraphExporter.exportAsDotWithType(graph);
    return graph;
  }

  /**
   * Compute and construct a map to store the position of diff hunks inside current file
   *
   * @param sourceFilePath
   * @param cu
   * @return
   */
  private Map<String, Pair<Integer, Integer>> computeHunksPosition(
      String sourceFilePath, CompilationUnit cu) {
    Map<String, Pair<Integer, Integer>> indexToPositionMap = new HashMap<>();
    // get the current diff file
    Version version = Version.BASE;
    if (sourceFilePath.contains(File.separator + "b" + File.separator)) {
      version = Version.CURRENT;
    }
    Optional<DiffFile> diffFileOpt = getDiffFileByPath(sourceFilePath, version);
    if (cu != null && diffFileOpt.isPresent()) {
      DiffFile diffFile = diffFileOpt.get();

      List<DiffHunk> diffHunksContainCode =
          diffFile.getDiffHunks().stream()
              .filter(diffHunk -> diffHunk.containsCode())
              .collect(Collectors.toList());
      for (DiffHunk diffHunk : diffHunksContainCode) {
        // compute the pos of all diff hunks that contains code
        int startPos = -1;
        int endPos = -1;
        switch (version) {
          case BASE:
            startPos = cu.getPosition(diffHunk.getBaseStartLine(), 0);
            endPos = cu.getPosition(diffHunk.getBaseEndLine() + 1, 0);
            break;
          case CURRENT:
            startPos = cu.getPosition(diffHunk.getCurrentStartLine(), 0);
            endPos = cu.getPosition(diffHunk.getCurrentEndLine() + 1, 0);
        }
        int length = endPos - startPos;
        // construct the location map
        indexToPositionMap.put(
            diffFile.getIndex().toString() + ":" + diffHunk.getIndex().toString(),
            Pair.of(startPos, length));
      }
    }
    return indexToPositionMap;
  }

  /**
   * Collect info of the hunks in the current file
   *
   * @param sourceFilePath
   * @param cu
   * @return
   */
  private void createHunkInfos(String sourceFilePath, CompilationUnit cu) throws IOException {
    List<HunkInfo> hunkInfos = new ArrayList<>();
    Map<String, Pair<Integer, Integer>> diffHunkPositions =
        computeHunksPosition(sourceFilePath, cu);
    for (String index : diffHunkPositions.keySet()) {
      // for each diff hunk, find covered nodes
      Set<ASTNode> coveredNodes = new LinkedHashSet<>();
      int startPos = diffHunkPositions.get(index).getLeft();
      int length = diffHunkPositions.get(index).getRight();
      if (length > 0) {
        MyNodeFinder nodeFinder = new MyNodeFinder(cu, startPos, length);
        for (ASTNode node : nodeFinder.getCoveredNodes()) {
          while (node != null && !(node instanceof Statement || node instanceof BodyDeclaration)) {
            node = node.getParent();
          }
          coveredNodes.add(node);
        }
      }
      HunkInfo hunkInfo = new HunkInfo();
      hunkInfo.coveredNodes = coveredNodes;
      for (ASTNode astNode : coveredNodes) {
        if (astNode instanceof BodyDeclaration) {
          Optional<Node> nodeOpt = Optional.empty();
          // find the corresponding nodeOpt in the entity pool (expected to exist)
          switch (astNode.getNodeType()) {
            case ASTNode.TYPE_DECLARATION:
              nodeOpt =
                  findNodeByNameAndType(
                      ((TypeDeclaration) astNode).getName().getIdentifier(), NodeType.CLASS);
              if (nodeOpt.isPresent()) {
                nodeOpt.get().isInDiffHunk = true;
              } else {
                logger.error("Not Found: " + astNode);
              }
              break;
            case ASTNode.FIELD_DECLARATION:
              List<VariableDeclarationFragment> fragments =
                  ((FieldDeclaration) astNode).fragments();
              for (VariableDeclarationFragment fragment : fragments) {
                nodeOpt = findNodeByNameAndType(fragment.getName().getIdentifier(), NodeType.FIELD);
                if (nodeOpt.isPresent()) {
                  nodeOpt.get().isInDiffHunk = true;
                } else {
                  logger.error("Not Found: " + astNode);
                }
              }
              break;
            case ASTNode.METHOD_DECLARATION:
              nodeOpt =
                  findNodeByNameAndType(
                      ((MethodDeclaration) astNode).getName().getIdentifier(), NodeType.METHOD);
              if (nodeOpt.isPresent()) {
                nodeOpt.get().isInDiffHunk = true;
              } else {
                logger.error("Not Found: " + astNode);
              }
              break;
            default:
              logger.error("Other type: " + astNode.getNodeType());
          }
        } else if (astNode instanceof Statement) {
          JDTService jdtService = new JDTService(FileUtils.readFileToString(new File(sourceFilePath)));
          // create the HunkInfo node by collecting info
          if(jdtService!=null){
            System.out.println(astNode);
             // find parent entity node (expected to exist) and create the contain edge

          }
        }
      }
      // add HunkInfo into the pool
      entityPool.hunkInfoMap.put(hunkInfo.uniqueName(), hunkInfo);
    }
  }

  /**
   * Find the corresponding node in graph by name and type
   *
   * @param name
   * @param type
   * @return
   */
  private Optional<Node> findNodeByNameAndType(String name, NodeType type) {
    // TODO: use qualified name to eliminate false positive
    return graph.vertexSet().stream()
        .filter(node -> node.getType().equals(type) && node.getIdentifier().equals(name))
        .findAny();
  }

  /**
   * Given the absolute path, return the corresponding diff file
   *
   * @param absolutePath
   * @return
   */
  private Optional<DiffFile> getDiffFileByPath(String absolutePath, Version version) {
    String formattedPath = Utils.formatPath(absolutePath);
    switch (version) {
      case BASE:
        return this.diffFiles.stream()
            .filter(diffFile -> formattedPath.endsWith(diffFile.getBaseRelativePath()))
            .findAny();
      case CURRENT:
        return this.diffFiles.stream()
            .filter(diffFile -> formattedPath.endsWith(diffFile.getCurrentRelativePath()))
            .findAny();
      default:
        return Optional.empty();
    }
  }
}
