package com.github.smartcommit.core;

import com.github.smartcommit.core.visitor.MemberVisitor;
import com.github.smartcommit.core.visitor.MyNodeFinder;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.EntityPool;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.model.entity.DeclarationInfo;
import com.github.smartcommit.model.entity.FieldInfo;
import com.github.smartcommit.model.entity.HunkInfo;
import com.github.smartcommit.model.entity.MethodInfo;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.EdgeType;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.model.graph.NodeType;
import com.github.smartcommit.util.JDTService;
import com.github.smartcommit.util.NameResolver;
import com.github.smartcommit.util.Utils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/** Build the semantic context graph of DiffHunks in Java files. */
public class GraphBuilder implements Callable<Graph<Node, Edge>> {

  private static final Logger logger = Logger.getLogger(GraphBuilder.class);
  private static final String JRE_PATH =
      System.getProperty("java.home") + File.separator + "lib/rt.jar";
  //  private static final String[] CLASS_PATH =
  // System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator));

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
        .allowingMultipleEdges(true) // allow multiple edges with different types
        .allowingSelfLoops(true) // allow recursion
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
    String[] encodings = new String[srcFolderPaths.length];
    Arrays.fill(encodings, "UTF-8");

    ASTParser parser = ASTParser.newParser(8);
    Map<String, String> options = JavaCore.getOptions();
    options.put(JavaCore.COMPILER_COMPLIANCE, "8");
    options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
    options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
    options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
    JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
    parser.setCompilerOptions(options);

    //        parser.setProject(WorkspaceUtilities.javaProject);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setEnvironment(new String[] {JRE_PATH}, srcFolderPaths, encodings, true);
    parser.setResolveBindings(true);
    parser.setBindingsRecovery(true);

    // Vertex: create nodes and nesting edges while visiting the ASTs
    encodings = new String[srcPaths.length];
    Arrays.fill(encodings, "UTF-8");
    parser.createASTs(
        srcPaths,
        encodings,
        new String[] {},
        new FileASTRequestor() {
          @Override
          public void acceptAST(String sourceFilePath, CompilationUnit cu) {
            try {
              // get the corresponding diff file
              Version version = Version.BASE;
              if (sourceFilePath.contains(
                  File.separator + Version.CURRENT.asString() + File.separator)) {
                version = Version.CURRENT;
              }
              Optional<DiffFile> diffFileOpt = getDiffFileByPath(sourceFilePath, version);
              if (diffFileOpt.isPresent()) {
                DiffFile diffFile = diffFileOpt.get();
                Map<String, Pair<Integer, Integer>> hunksPosition =
                    computeHunksPosition(diffFile, cu, version);

                // collect type/field/method infos and create nodes
                JDTService jdtService =
                    new JDTService(FileUtils.readFileToString(new File(sourceFilePath)));
                cu.accept(new MemberVisitor(diffFile.getIndex(), entityPool, graph, jdtService));

                // collect hunk infos and create nodes
                createHunkInfos(version, diffFile.getIndex(), hunksPosition, cu, jdtService);
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        },
        null);

    // Edge: create inter-entity edges with the EntityPool and EntityInfo
    Map<String, MethodInfo> methodDecMap = entityPool.methodInfoMap;
    Map<String, FieldInfo> fieldDecMap = entityPool.fieldInfoMap;
    Map<String, HunkInfo> hunkMap = entityPool.hunkInfoMap;
    Map<IMethodBinding, MethodInfo> methodBindingMap = new HashMap<>();
    for (MethodInfo methodInfo : entityPool.methodInfoMap.values()) {
      methodBindingMap.put(methodInfo.methodBinding, methodInfo);
    }

    // 0. edges from type/interface/enum/annotation declaration
    Map<String, DeclarationInfo> topDecMap = new HashMap<>();
    topDecMap.putAll(entityPool.classInfoMap);
    topDecMap.putAll(entityPool.interfaceInfoMap);
    topDecMap.putAll(entityPool.enumInfoMap);
    topDecMap.putAll(entityPool.annotationInfoMap);
    for (DeclarationInfo info : topDecMap.values()) {
      // method invocation
      for (IMethodBinding methodCall : info.methodCalls) {
        MethodInfo targetMethodInfo = methodBindingMap.get(methodCall);
        if (targetMethodInfo != null) {
          createEdge(info.node, targetMethodInfo.node, EdgeType.CALL);
        }
      }

      // field access
      for (String fieldUse : info.fieldUses) {
        FieldInfo targetFieldInfo = fieldDecMap.get(fieldUse);
        if (targetFieldInfo != null) {
          createEdge(info.node, targetFieldInfo.node, EdgeType.ACCESS);
        }
      }
      // extends and implements
      for (String type : info.typeUses) {
        Optional<Node> typeDecNode = findTypeNode(type, info.fileIndex);
        typeDecNode.ifPresent(node -> createEdge(info.node, node, EdgeType.INITIALIZE));
      }
    }

    // member declarations
    // 1. edges from method declaration
    for (MethodInfo methodInfo : methodDecMap.values()) {
      Node methodDeclNode = methodInfo.node;
      // method invocation
      for (IMethodBinding methodCall : methodInfo.methodCalls) {
        MethodInfo targetMethodInfo = methodBindingMap.get(methodCall);
        if (targetMethodInfo != null) {
          createEdge(methodDeclNode, targetMethodInfo.node, EdgeType.CALL);
        }
      }

      // field access
      for (String fieldUse : methodInfo.fieldUses) {
        FieldInfo targetFieldInfo = fieldDecMap.get(fieldUse);
        if (targetFieldInfo != null) {
          createEdge(methodDeclNode, targetFieldInfo.node, EdgeType.ACCESS);
        }
      }

      // return type(s)
      for (String type : methodInfo.returnTypes) {
        Optional<Node> typeDecNode = findTypeNode(type, methodInfo.fileIndex);
        if (typeDecNode.isPresent()) {
          createEdge(methodDeclNode, typeDecNode.get(), EdgeType.RETURN);
        }
      }

      // param type
      for (String type : methodInfo.paramTypes) {
        Optional<Node> typeDecNode = findTypeNode(type, methodInfo.fileIndex);
        if (typeDecNode.isPresent()) {
          createEdge(methodDeclNode, typeDecNode.get(), EdgeType.PARAM);
        }
      }

      // local var type
      for (String type : methodInfo.typeUses) {
        Optional<Node> typeDecNode = findTypeNode(type, methodInfo.fileIndex);
        if (typeDecNode.isPresent()) {
          createEdge(methodDeclNode, typeDecNode.get(), EdgeType.INITIALIZE);
        }
      }
    }

    // 2. edges from field declaration
    for (FieldInfo fieldInfo : fieldDecMap.values()) {
      Node fieldDeclNode = fieldInfo.node;
      // field type
      for (String type : fieldInfo.types) {
        Optional<Node> typeDecNode = findTypeNode(type, fieldInfo.fileIndex);
        if (typeDecNode.isPresent()) {
          createEdge(fieldDeclNode, typeDecNode.get(), EdgeType.TYPE);
        }
      }

      // method invocation
      for (IMethodBinding methodCall : fieldInfo.methodCalls) {
        MethodInfo targetMethodInfo = methodBindingMap.get(methodCall);
        if (targetMethodInfo != null) {
          createEdge(fieldDeclNode, targetMethodInfo.node, EdgeType.CALL);
        }
      }

      // field access
      for (String fieldUse : fieldInfo.fieldUses) {
        FieldInfo targetFieldInfo = fieldDecMap.get(fieldUse);
        if (targetFieldInfo != null) {
          createEdge(fieldDeclNode, targetFieldInfo.node, EdgeType.ACCESS);
        }
      }

      // type instance creation
      for (String type : fieldInfo.typeUses) {
        Optional<Node> typeDecNode = findTypeNode(type, fieldInfo.fileIndex);
        if (typeDecNode.isPresent()) {
          createEdge(fieldDeclNode, typeDecNode.get(), EdgeType.INITIALIZE);
        }
      }
    }

    // 3. edges from hunk nodes
    for (HunkInfo hunkInfo : hunkMap.values()) {
      Node hunkNode = hunkInfo.node;
      // method invocation
      for (IMethodBinding methodCall : hunkInfo.methodCalls) {
        MethodInfo targetMethodInfo = methodBindingMap.get(methodCall);
        if (targetMethodInfo != null) {
          createEdge(hunkNode, targetMethodInfo.node, EdgeType.CALL);
        }
      }

      // field access
      for (String fieldUse : hunkInfo.fieldUses) {
        FieldInfo targetFieldInfo = fieldDecMap.get(fieldUse);
        if (targetFieldInfo != null) {
          createEdge(hunkNode, targetFieldInfo.node, EdgeType.ACCESS);
        }
      }

      // type uses
      for (String type : hunkInfo.typeUses) {
        Optional<Node> typeDecNode = findTypeNode(type, hunkInfo.fileIndex);
        if (typeDecNode.isPresent()) {
          createEdge(hunkNode, typeDecNode.get(), EdgeType.INITIALIZE);
        }
      }
    }

    return graph;
  }

  /**
   * Create an (logical) edge in the graph: if not exists, create; else increase the weight by one
   *
   * @param source
   * @param target
   * @param edgeType
   * @return whether the operation succeeds
   */
  private boolean createEdge(Node source, Node target, EdgeType edgeType) {
    int edgeCount = graph.edgeSet().size();
    boolean success = false;
    Set<Edge> edges = graph.getAllEdges(source, target);
    if (edges.isEmpty()) {
      success = graph.addEdge(source, target, new Edge(edgeCount++, edgeType));
    } else {
      // find the edge with the same type and increase the weight by one
      for (Edge e : edges) {
        if (e.getType().equals(edgeType)) {
          Integer lastWeight = e.getWeight();
          e.increaseWeight();
          success = (e.getWeight() - lastWeight == 1);
        }
      }
      // allow for multiple edges with different types
      if (!success) {
        success = graph.addEdge(source, target, new Edge(edgeCount++, edgeType));
      }
    }
    if (!success) {
      logger.warn("Unsuccessful edge creation: " + edgeType);
    }
    return success;
  }

  /**
   * Find the type declaration node
   *
   * @param type
   * @return
   */
  private Optional<Node> findTypeNode(String type, Integer fileIndex) {
    // for qualified name
    if (entityPool.classInfoMap.containsKey(type)) {
      return Optional.of(entityPool.classInfoMap.get(type).node);
    } else if (entityPool.interfaceInfoMap.containsKey(type)) {
      return Optional.of(entityPool.interfaceInfoMap.get(type).node);
    } else if (entityPool.enumInfoMap.containsKey(type)) {
      return Optional.of(entityPool.enumInfoMap.get(type).node);
    }

    // import must be within the same file
    if (entityPool.importInfoMap.containsKey(fileIndex)) {
      // qualified type
      Map<String, HunkInfo> type2HunkMap = entityPool.importInfoMap.get(fileIndex);
      if (type2HunkMap.containsKey(type)) {
        return Optional.of(type2HunkMap.get(type).node);
      } else {
        // unqualified type
        for (Map.Entry<String, HunkInfo> entry : type2HunkMap.entrySet()) {
          if (entry.getKey().endsWith(type)) {
            return Optional.of(entry.getValue().node);
          }
        }
      }
    }
    return Optional.empty();
  }
  /**
   * Collect info of the hunks in the current file
   *
   * @param hunksPosition
   * @param cu
   * @return
   */
  private void createHunkInfos(
      Version version,
      Integer fileIndex,
      Map<String, Pair<Integer, Integer>> hunksPosition,
      CompilationUnit cu,
      JDTService jdtService) {
    Map<String, HunkInfo> importType2HunkMap = new HashMap<>();
    for (String index : hunksPosition.keySet()) {
      // for each diff hunk, find and analyze covered nodes, create hunk node and info
      Set<ASTNode> coveredNodes = new LinkedHashSet<>();
      int startPos = hunksPosition.get(index).getLeft();
      int length = hunksPosition.get(index).getRight();
      if (length > 0) {
        MyNodeFinder nodeFinder = new MyNodeFinder(cu, startPos, length);
        for (ASTNode node : nodeFinder.getCoveredNodes()) {
          while (node != null
              && !(node instanceof ImportDeclaration
                  || node instanceof BodyDeclaration
                  || node instanceof Statement
                  || node instanceof Comment
                  || (node instanceof Expression && !(node instanceof Name)))) {
            node = node.getParent();
          }
          coveredNodes.add(node);
        }
      } else {
        continue;
      }

      // coveredNodes.isEmpty() --> added for BASE and deleted for CURRENT
      // if empty, process the next hunk
      if (coveredNodes.isEmpty()) {
        continue;
      }

      HunkInfo hunkInfo = new HunkInfo(index);
      hunkInfo.fileIndex = fileIndex;
      hunkInfo.coveredNodes = coveredNodes;

      // save covered nodes also in hunks
      Pair<Integer, Integer> indices = Utils.parseIndices(index);
      DiffHunk diffHunk = null;
      if (fileIndex < diffFiles.size())
        diffHunk = diffFiles.get(fileIndex).getDiffHunks().get(indices.getRight());
      if (diffHunk != null) {
        if (version.equals(Version.BASE)) {
          diffHunk.getBaseHunk().setCoveredNodes(new ArrayList<>(coveredNodes));
        } else {
          diffHunk.getCurrentHunk().setCoveredNodes(new ArrayList<>(coveredNodes));
        }
      }

      int nodeID = graph.vertexSet().size() + 1;
      int edgeID = graph.edgeSet().size() + 1;
      Node hunkNode = new Node(nodeID, NodeType.HUNK, hunkInfo.uniqueName(), hunkInfo.uniqueName());
      hunkNode.isInDiffHunk = true;
      hunkNode.diffHunkIndex = index;

      hunkInfo.node = hunkNode;
      graph.addVertex(hunkNode);

      boolean existInGraph = false;
      for (ASTNode astNode : coveredNodes) {
        if (astNode instanceof ImportDeclaration) {
          // save type defs in import statements
          hunkInfo.typeDefs.add(((ImportDeclaration) astNode).getName().toString());
        } else if (astNode instanceof Statement) {
          // add edge between used and the current node
          jdtService.parseStatement(hunkInfo, (Statement) astNode);
        } else if (astNode instanceof Expression) {
          jdtService.parseExpression(hunkInfo, (Expression) astNode);
        } else if (astNode instanceof BodyDeclaration) {
          Optional<Node> nodeOpt = Optional.empty();
          // find the corresponding nodeOpt in the entity pool (expected to exist)
          switch (astNode.getNodeType()) {
              // TODO: for type declarations, members should all be in diff
            case ASTNode.ANNOTATION_TYPE_DECLARATION:
              ITypeBinding annoBinding = ((AnnotationTypeDeclaration) astNode).resolveBinding();
              if (annoBinding != null && annoBinding.getQualifiedName().contains(".")) {
                nodeOpt =
                    findNodeByNameAndType(
                        annoBinding.getQualifiedName(), NodeType.ANNOTATION, true);
              } else {
                nodeOpt =
                    findNodeByNameAndType(
                        ((AnnotationTypeDeclaration) astNode).getName().getIdentifier(),
                        NodeType.ANNOTATION,
                        false);
              }
              if (nodeOpt.isPresent()) {
                existInGraph = true;
                Node node = nodeOpt.get();
                node.isInDiffHunk = true;
                node.diffHunkIndex = index;

                hunkInfo.typeDefs.add(node.getQualifiedName());
                graph.addEdge(hunkNode, node, new Edge(edgeID, EdgeType.CONTAIN));

              } else {
                logger.warn("ANNOTATION_TYPE_DECLARATION Not Found: " + astNode);
              }
              break;
            case ASTNode.ANNOTATION_TYPE_MEMBER_DECLARATION:
              IMethodBinding memberBinding =
                  ((AnnotationTypeMemberDeclaration) astNode).resolveBinding();
              if (memberBinding != null
                  && memberBinding.getDeclaringClass().getQualifiedName().contains(".")) {
                nodeOpt =
                    findNodeByNameAndType(
                        memberBinding.getDeclaringClass().getQualifiedName()
                            + ":"
                            + memberBinding.getName(),
                        NodeType.ANNOTATION_MEMBER,
                        true);
              } else {
                nodeOpt =
                    findNodeByNameAndType(
                        ((AnnotationTypeMemberDeclaration) astNode).getName().getIdentifier(),
                        NodeType.ANNOTATION_MEMBER,
                        false);
              }
              if (nodeOpt.isPresent()) {
                existInGraph = true;
                Node node = nodeOpt.get();
                node.isInDiffHunk = true;
                node.diffHunkIndex = index;

                graph.addEdge(hunkNode, node, new Edge(edgeID, EdgeType.CONTAIN));
              } else {
                logger.warn("ANNOTATION_TYPE_MEMBER_DECLARATION Not Found: " + astNode);
              }
              break;
            case ASTNode.ENUM_DECLARATION:
              ITypeBinding enumBinding = ((EnumDeclaration) astNode).resolveBinding();
              if (enumBinding != null && enumBinding.getQualifiedName().contains(".")) {
                nodeOpt =
                    findNodeByNameAndType(enumBinding.getQualifiedName(), NodeType.ENUM, true);
              } else {
                nodeOpt =
                    findNodeByNameAndType(
                        ((AbstractTypeDeclaration) astNode).getName().getIdentifier(),
                        NodeType.ENUM,
                        false);
              }
              if (nodeOpt.isPresent()) {
                existInGraph = true;
                Node node = nodeOpt.get();
                node.isInDiffHunk = true;
                node.diffHunkIndex = index;

                hunkInfo.typeDefs.add(node.getQualifiedName());
                graph.addEdge(hunkNode, node, new Edge(edgeID, EdgeType.CONTAIN));
              } else {
                logger.warn("ENUM_DECLARATION Not Found: " + astNode);
              }
              break;
            case ASTNode.TYPE_DECLARATION:
              ITypeBinding typeBinding = ((TypeDeclaration) astNode).resolveBinding();
              NodeType type =
                  ((TypeDeclaration) astNode).isInterface() ? NodeType.INTERFACE : NodeType.CLASS;
              if (typeBinding != null && typeBinding.getQualifiedName().contains(".")) {
                nodeOpt = findNodeByNameAndType(typeBinding.getQualifiedName(), type, true);
              } else {
                nodeOpt =
                    findNodeByNameAndType(
                        ((TypeDeclaration) astNode).getName().getIdentifier(), type, false);
              }

              if (nodeOpt.isPresent()) {
                existInGraph = true;
                Node node = nodeOpt.get();
                node.isInDiffHunk = true;
                node.diffHunkIndex = index;

                hunkInfo.typeDefs.add(node.getQualifiedName());
                graph.addEdge(hunkNode, node, new Edge(edgeID, EdgeType.CONTAIN));

              } else {
                logger.warn("TYPE_DECLARATION Not Found: " + astNode);
              }
              break;
            case ASTNode.ENUM_CONSTANT_DECLARATION:
              IVariableBinding varBinding = ((EnumConstantDeclaration) astNode).resolveVariable();
              if (varBinding != null
                  && varBinding.getDeclaringClass().getQualifiedName().contains(".")) {
                nodeOpt =
                    findNodeByNameAndType(
                        varBinding.getDeclaringClass().getQualifiedName()
                            + ":"
                            + ((EnumConstantDeclaration) astNode).getName().getFullyQualifiedName(),
                        NodeType.ENUM_CONSTANT,
                        true);
              } else {
                nodeOpt =
                    findNodeByNameAndType(
                        ((EnumConstantDeclaration) astNode).getName().getIdentifier(),
                        NodeType.ENUM_CONSTANT,
                        false);
              }

              if (nodeOpt.isPresent()) {
                existInGraph = true;
                Node node = nodeOpt.get();
                node.isInDiffHunk = true;
                node.diffHunkIndex = index;

                // consider constant as fields
                hunkInfo.fieldDefs.add(node.getQualifiedName());
                graph.addEdge(hunkNode, node, new Edge(edgeID, EdgeType.CONTAIN));

              } else {
                logger.warn("ENUM_CONSTANT_DECLARATION Not Found: " + astNode);
              }
              break;
            case ASTNode.FIELD_DECLARATION:
              List<VariableDeclarationFragment> fragments =
                  ((FieldDeclaration) astNode).fragments();
              //              hunkInfo.typeUses.addAll(
              //                  processAnnotations(((FieldDeclaration) astNode).modifiers()));
              for (VariableDeclarationFragment fragment : fragments) {
                IVariableBinding binding = fragment.resolveBinding();
                if (binding != null
                    && binding.getDeclaringClass() != null
                    && binding.getDeclaringClass().getQualifiedName().contains(".")) {
                  // use qualified name
                  nodeOpt =
                      findNodeByNameAndType(
                          binding.getDeclaringClass().getQualifiedName() + ":" + binding.getName(),
                          NodeType.FIELD,
                          true);
                } else {
                  // use simple name instead
                  nodeOpt =
                      findNodeByNameAndType(
                          fragment.getName().getFullyQualifiedName(), NodeType.FIELD, false);
                }
                if (nodeOpt.isPresent()) {
                  existInGraph = true;
                  Node node = nodeOpt.get();
                  node.isInDiffHunk = true;
                  node.diffHunkIndex = index;

                  hunkInfo.fieldDefs.add(node.getQualifiedName());
                  graph.addEdge(hunkNode, node, new Edge(edgeID, EdgeType.CONTAIN));

                } else {
                  logger.warn("FIELD_DECLARATION Not Found: " + astNode);
                }
              }
              break;
            case ASTNode.METHOD_DECLARATION:
              MethodDeclaration methodDeclaration = (MethodDeclaration) astNode;
              String uniqueMethodName = methodDeclaration.getName().getIdentifier();
              IMethodBinding methodBinding = methodDeclaration.resolveBinding();
              if (methodBinding != null
                  && methodBinding.getDeclaringClass() != null
                  && methodBinding.getDeclaringClass().getQualifiedName().contains(".")) {
                // get the unique name of the method, including the parameter string
                uniqueMethodName =
                    jdtService.getUniqueNameForMethod(
                        methodBinding.getDeclaringClass().getQualifiedName(), methodDeclaration);
                nodeOpt = findNodeByNameAndType(uniqueMethodName, NodeType.METHOD, true);
              } else {
                nodeOpt = findNodeByNameAndType(uniqueMethodName, NodeType.METHOD, false);
              }

              if (nodeOpt.isPresent()) {
                existInGraph = true;
                Node node = nodeOpt.get();
                node.isInDiffHunk = true;
                node.diffHunkIndex = index;

                hunkInfo.methodDefs.add(node.getQualifiedName());
                graph.addEdge(hunkNode, node, new Edge(edgeID, EdgeType.CONTAIN));

              } else {
                logger.warn("METHOD_DECLARATION Not Found: " + astNode);
              }
              break;
            case ASTNode.INITIALIZER:
              // parse all method calls/field access in initializer and create node
              // find node by identifier and create edge
              Initializer initializer = (Initializer) astNode;
              if (initializer.getParent() instanceof TypeDeclaration) {
                TypeDeclaration parent = ((TypeDeclaration) initializer.getParent());
                if (parent.resolveBinding() != null) {
                  String uniqueName = parent.resolveBinding().getQualifiedName() + ":INIT";
                  nodeOpt = findNodeByNameAndType(uniqueName, NodeType.INITIALIZER_BLOCK, true);
                } else {
                  String uniqueName = parent.getName().getFullyQualifiedName() + ":INIT";
                  nodeOpt = findNodeByNameAndType(uniqueName, NodeType.INITIALIZER_BLOCK, false);
                }
                if (nodeOpt.isPresent()) {
                  existInGraph = true;
                  Node node = nodeOpt.get();
                  node.isInDiffHunk = true;
                  node.diffHunkIndex = index;

                  //                  hunkInfo.methodDefs.add(node.getQualifiedName());
                  graph.addEdge(hunkNode, node, new Edge(edgeID, EdgeType.CONTAIN));

                } else {
                  logger.warn("INITIALIZER Not Found: " + astNode);
                }
              }
              break;
            default:
              logger.warn(
                  "Unconsidered type: " + Annotation.nodeClassForType(astNode.getNodeType()));
          }
        }
      }
      // create the HunkInfo node for hunks inside entities
      if (!existInGraph) {
        // find parent entity node (expected to exist) and create the contain edge
        Optional<Node> parentNodeOpt = findParentNode(coveredNodes);
        if (parentNodeOpt.isPresent()) {
          graph.addEdge(parentNodeOpt.get(), hunkNode, new Edge(edgeID, EdgeType.CONTAIN));
        }

        // save imported types into the entityPool
        if (!hunkInfo.typeDefs.isEmpty()) {
          for (String type : hunkInfo.typeDefs) {
            importType2HunkMap.put(type, hunkInfo);
          }
        }
      }

      // add HunkInfo into the pool
      entityPool.hunkInfoMap.put(hunkInfo.uniqueName(), hunkInfo);
    }
    if (!importType2HunkMap.isEmpty()) {
      entityPool.importInfoMap.put(fileIndex, importType2HunkMap);
    }
  }

  /**
   * Compute and construct a map to store the position of diff hunks inside current file
   *
   * @param diffFile
   * @param cu
   * @return
   */
  private Map<String, Pair<Integer, Integer>> computeHunksPosition(
      DiffFile diffFile, CompilationUnit cu, Version version) {
    Map<String, Pair<Integer, Integer>> indexToPositionMap = new HashMap<>();
    if (cu != null) {
      List<DiffHunk> diffHunksContainCode =
          diffFile.getDiffHunks().stream()
              .filter(diffHunk -> diffHunk.containsCode())
              .collect(Collectors.toList());
      for (DiffHunk diffHunk : diffHunksContainCode) {
        // compute the pos of all diff hunks that contains code
        int startPos = -1;
        int endPos = -1;
        // cu.getPosition(line, column)
        //   * @param line the one-based line number
        //	 * @param column the zero-based column number
        //	 * @return the 0-based character position in the source string
        switch (version) {
          case BASE:
            startPos = cu.getPosition(diffHunk.getBaseStartLine(), 0);
            endPos =
                cu.getPosition(
                    diffHunk.getBaseEndLine(),
                    diffHunk.getBaseHunk().getLastLineLength() > 0
                        ? diffHunk.getBaseHunk().getLastLineLength() - 1
                        : 0);
            break;
          case CURRENT:
            startPos = cu.getPosition(diffHunk.getCurrentStartLine(), 0);
            endPos =
                cu.getPosition(
                    diffHunk.getCurrentEndLine(),
                    diffHunk.getCurrentHunk().getLastLineLength() > 0
                        ? diffHunk.getCurrentHunk().getLastLineLength() - 1
                        : 0);
        }
        int length = endPos - startPos;
        // construct the location map
        indexToPositionMap.put(diffHunk.getUniqueIndex(), Pair.of(startPos, length));
      }
    }
    return indexToPositionMap;
  }

  /**
   * Find the nearest common ancestor entity in the ast and the node in the graph
   *
   * @param astNodes
   * @return
   */
  private Optional<Node> findParentNode(Set<ASTNode> astNodes) {
    // TODO: find the nearest common ancestor of the covered ast nodes
    ASTNode parentEntity = null;
    for (ASTNode astNode : astNodes) {
      while (astNode != null && !(astNode instanceof BodyDeclaration)) {
        astNode = astNode.getParent();
      }
      parentEntity = astNode;
    }
    if (parentEntity != null) {
      String identifier = "";
      switch (parentEntity.getNodeType()) {
        case ASTNode.TYPE_DECLARATION:
          identifier = ((TypeDeclaration) parentEntity).getName().getFullyQualifiedName();
          break;
        case ASTNode.FIELD_DECLARATION:
          List<VariableDeclarationFragment> fragments =
              ((FieldDeclaration) parentEntity).fragments();
          identifier = fragments.get(0).getName().getFullyQualifiedName();
          break;
        case ASTNode.METHOD_DECLARATION:
          identifier = ((MethodDeclaration) parentEntity).getName().getFullyQualifiedName();
          break;
      }
      String finalIdentifier = identifier;
      return graph.vertexSet().stream()
          .filter(node -> node.getIdentifier().equals(finalIdentifier))
          .findAny();
    }
    return Optional.empty();
  }

  /**
   * Find the corresponding node in graph by name (qualified name first, simple name if no qualified
   * name) and type
   *
   * @param name
   * @param type
   * @return
   */
  private Optional<Node> findNodeByNameAndType(
      String name, NodeType type, Boolean isQualifiedName) {
    if (isQualifiedName) {
      return graph.vertexSet().stream()
          .filter(node -> node.getType().equals(type) && node.getQualifiedName().equals(name))
          .findAny();
    } else {
      return graph.vertexSet().stream()
          .filter(node -> node.getType().equals(type) && node.getIdentifier().endsWith(name))
          .findAny();
    }
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
            .filter(
                diffFile ->
                    !diffFile.getBaseRelativePath().isEmpty()
                        && formattedPath.endsWith(diffFile.getBaseRelativePath()))
            .findAny();
      case CURRENT:
        return this.diffFiles.stream()
            .filter(
                diffFile ->
                    !diffFile.getCurrentRelativePath().isEmpty()
                        && formattedPath.endsWith(diffFile.getCurrentRelativePath()))
            .findAny();
      default:
        return Optional.empty();
    }
  }
}
