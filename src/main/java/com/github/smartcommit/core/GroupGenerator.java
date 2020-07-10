package com.github.smartcommit.core;

import com.github.smartcommit.io.DiffGraphExporter;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.constant.*;
import com.github.smartcommit.model.diffgraph.DiffEdge;
import com.github.smartcommit.model.diffgraph.DiffEdgeType;
import com.github.smartcommit.model.diffgraph.DiffNode;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.Utils;
import gr.uom.java.xmi.UMLModel;
import gr.uom.java.xmi.UMLModelASTReader;
import gr.uom.java.xmi.diff.CodeRange;
import gr.uom.java.xmi.diff.UMLModelDiff;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringMinerTimedOutException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class GroupGenerator {
  private static final Logger logger = Logger.getLogger(GroupGenerator.class);

  // meta data
  private String repoID;
  private String repoName;
  private Pair<String, String> srcDirs; // dirs to store the collected files
  private List<DiffFile> diffFiles;
  private List<DiffHunk> diffHunks;
  private Graph<Node, Edge> baseGraph;
  private Graph<Node, Edge> currentGraph;
  // map from the grouped diff hunk index to its group id
  private Map<String, String> indexToGroupMap;

  // outputs
  private Graph<DiffNode, DiffEdge> diffGraph;

  // options
  private boolean processNonJava = false;
  private boolean detectRefs = false;
  private double minSimilarity = 0.618D;
  private int maxDistance = 0;

  public GroupGenerator(
      String repoID,
      String repoName,
      Pair<String, String> srcDirs,
      List<DiffFile> diffFiles,
      List<DiffHunk> diffHunks,
      Graph<Node, Edge> baseGraph,
      Graph<Node, Edge> currentGraph) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.srcDirs = srcDirs;
    this.diffFiles = diffFiles;
    this.diffHunks = diffHunks;
    this.baseGraph = baseGraph;
    this.currentGraph = currentGraph;

    this.indexToGroupMap = new HashMap<>();
    this.diffGraph = initDiffGraph();
  }

  // hard -- topo sort + union find
  // soft -- similarity, distance
  // cross-version -- move, ref
  // cross-lang ref -- references

  private Graph<DiffNode, DiffEdge> initDiffGraph() {
    Graph<DiffNode, DiffEdge> diffGraph =
        GraphTypeBuilder.<DiffNode, DiffEdge>directed()
            .allowingMultipleEdges(true)
            .allowingSelfLoops(true)
            .edgeClass(DiffEdge.class)
            .weighted(true)
            .buildGraph();
    int nodeID = 0;

    List<Node> baseHunkNodes =
        baseGraph.vertexSet().stream()
            .filter(node -> node.isInDiffHunk)
            .collect(Collectors.toList());
    List<Node> currentHunkNodes =
        currentGraph.vertexSet().stream()
            .filter(node -> node.isInDiffHunk)
            .collect(Collectors.toList());

    for (DiffHunk diffHunk : diffHunks) {
      DiffNode diffNode = new DiffNode(nodeID++, diffHunk.getUniqueIndex(), diffHunk.getUUID());
      if (diffHunk.getFileType().equals(FileType.JAVA)) {
        Map<String, Integer> baseHierarchy =
            getHierarchy(baseGraph, baseHunkNodes, diffHunk.getUniqueIndex());
        Map<String, Integer> currentHierarchy =
            getHierarchy(currentGraph, currentHunkNodes, diffHunk.getUniqueIndex());
        if (!baseHierarchy.isEmpty()) {
          diffNode.setBaseHierarchy(baseHierarchy);
        }
        if (!currentHierarchy.isEmpty()) {
          diffNode.setCurrentHierarchy(currentHierarchy);
        }
      }

      diffGraph.addVertex(diffNode);
    }
    return diffGraph;
  }

  /** Build edges in the diff graph */
  public void buildDiffGraph() {

    // cache all links from base/current graph as a top order
    Map<String, Set<String>> hardLinks =
        Utils.mergeTwoMaps(analyzeDefUse(baseGraph), analyzeDefUse(currentGraph));

    List<DiffFile> nonJavaDiffFiles =
        diffFiles.stream()
            .filter(diffFile -> !diffFile.getFileType().equals(FileType.JAVA))
            .collect(Collectors.toList());
    Set<DiffHunk> others = new TreeSet<>(ascendingByIndexComparator());

    if (processNonJava) {
      // use tree set to keep unique and ordered
      Set<DiffHunk> doc = new TreeSet<>(ascendingByIndexComparator());
      Set<DiffHunk> config = new TreeSet<>(ascendingByIndexComparator());
      Set<DiffHunk> resource = new TreeSet<>(ascendingByIndexComparator());
      for (DiffFile diffFile : nonJavaDiffFiles) {
        // classify non-java changes into doc/config/resources/others
        String filePath =
            diffFile.getBaseRelativePath().isEmpty()
                ? diffFile.getCurrentRelativePath()
                : diffFile.getBaseRelativePath();
        if (Utils.isDocFile(filePath)) {
          doc.addAll(diffFile.getDiffHunks());
        } else if (Utils.isConfigFile(filePath)) {
          config.addAll(diffFile.getDiffHunks());
        } else if (Utils.isResourceFile(filePath)) {
          resource.addAll(diffFile.getDiffHunks());
        } else {
          others.addAll(diffFile.getDiffHunks());
        }
      }
      createEdges(doc, DiffEdgeType.DOC, 1.0);
      createEdges(config, DiffEdgeType.CONFIG, 1.0);
      createEdges(resource, DiffEdgeType.RESOURCE, 1.0);
      createEdges(others, DiffEdgeType.OTHERS, 1.0);
    } else {
      Map<String, Set<DiffHunk>> diffHunksByFileType = new HashMap<>();
      for (DiffFile diffFile : nonJavaDiffFiles) {
        String fileType =
            diffFile.getBaseRelativePath().isEmpty()
                ? Utils.getFileExtension(diffFile.getBaseRelativePath())
                : Utils.getFileExtension(diffFile.getCurrentRelativePath());
        if (!diffHunksByFileType.containsKey(fileType)) {
          diffHunksByFileType.put(fileType, new HashSet<>());
        }
        diffHunksByFileType.get(fileType).addAll(diffFile.getDiffHunks());
      }
      for (Map.Entry<String, Set<DiffHunk>> entry : diffHunksByFileType.entrySet()) {
        // group file according to file type
        createEdges(entry.getValue(), DiffEdgeType.NONJAVA, 1.0);
      }
    }

    // refactor
    if (detectRefs) {
      Set<DiffHunk> refDiffHunks = new TreeSet<>(ascendingByIndexComparator());
      ExecutorService service = Executors.newSingleThreadExecutor();
      Future<?> f = null;
      try {
        Runnable r = () -> groupRefactorings(refDiffHunks);
        f = service.submit(r);
        f.get(300, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        f.cancel(true);
        logger.warn(String.format("Ignore refactoring detection due to RM timeout: "), e);
      } catch (ExecutionException | InterruptedException e) {
        logger.warn(String.format("Ignore refactoring detection due to RM error: "), e);
        e.printStackTrace();
      } finally {
        service.shutdown();
      }
      if (!refDiffHunks.isEmpty()) {
        createEdges(refDiffHunks, DiffEdgeType.REFACTOR, 1.0);
      }
    }

    Set<DiffHunk> reformat = new TreeSet<>(ascendingByIndexComparator());

    for (int i = 0; i < diffHunks.size(); ++i) {
      DiffHunk diffHunk = diffHunks.get(i);
      // changes that does not actually change code and remove
      // reformat
      if (checkReformatting(diffHunk)) {
        reformat.add(diffHunk);
        continue;
      }

      // create edge according to hard links (that depends on the current)
      // in topo order
      if (diffHunk.getFileType().equals(FileType.JAVA)) {
        if (hardLinks.containsKey(diffHunk.getUniqueIndex())) {
          for (String target : hardLinks.get(diffHunk.getUniqueIndex())) {
            if (!target.equals(diffHunk.getUniqueIndex())) {
              createEdge(diffHunk.getUniqueIndex(), target, DiffEdgeType.DEPEND, 1.0);
            }
          }
        }
      }

      // estimate soft links (every two diff hunks)
      for (int j = i + 1; j < diffHunks.size(); j++) {
        DiffHunk diffHunk1 = diffHunks.get(j);
        if (!diffHunk1.getUniqueIndex().equals(diffHunk.getUniqueIndex())) {
          // similarity (textual+action)
          double similarity = estimateSimilarity(diffHunk, diffHunk1);
          if (similarity >= minSimilarity) {
            createEdge(
                diffHunk.getUniqueIndex(),
                diffHunk1.getUniqueIndex(),
                DiffEdgeType.SIMILAR,
                similarity);
          }
          // distance (1/n)
          int distance = estimateDistance(diffHunk, diffHunk1);
          if (distance > 0 && distance <= maxDistance) {
            createEdge(
                diffHunk.getUniqueIndex(),
                diffHunk1.getUniqueIndex(),
                DiffEdgeType.CLOSE,
                Utils.formatDouble((double) 1 / distance));
          }
          // cross-version but similar (moving or refactoring)
          // condition: same parent scope (file level for now), delete and add
          if (diffHunk.getFileIndex().equals(diffHunk1.getFileIndex())
              && !diffHunk.getChangeType().equals(ChangeType.MODIFIED)
              && !diffHunk1.getChangeType().equals(ChangeType.MODIFIED)) {
            similarity = estimateCrossVersionSimilarity(diffHunk, diffHunk1);
            if (similarity >= minSimilarity) {
              createEdge(
                  diffHunk.getUniqueIndex(),
                  diffHunk1.getUniqueIndex(),
                  DiffEdgeType.SIMILAR,
                  similarity);
            }
          }
        }
        // TODO: cross-lang dependency
        // detect references between configs and java
      }
    }
    createEdges(reformat, DiffEdgeType.REFORMAT, 1.0);
  }

  private Map<String, Set<String>> analyzeDefUse(Graph<Node, Edge> graph) {
    Map<String, Set<String>> defUseLinks = new HashMap<>();
    List<Node> hunkNodes =
        graph.vertexSet().stream().filter(node -> node.isInDiffHunk).collect(Collectors.toList());
    for (Node node : hunkNodes) {
      List<String> defHunkNodes = analyzeDef(graph, node, new HashSet<>());
      List<String> useHunkNodes = analyzeUse(graph, node, new HashSet<>());
      // record the links an return
      if (!defHunkNodes.isEmpty() || !useHunkNodes.isEmpty()) {
        if (!defUseLinks.containsKey(node.diffHunkIndex)) {
          defUseLinks.put(node.diffHunkIndex, new HashSet<>());
        }
        for (String s : defHunkNodes) {
          defUseLinks.get(node.diffHunkIndex).add(s);
        }
        for (String s : useHunkNodes) {
          defUseLinks.get(node.diffHunkIndex).add(s);
        }
      }
    }
    return defUseLinks;
  }

  /**
   * DFS to find all nodes point to the current in diff hunks
   *
   * @param graph
   * @param node
   * @param visited
   * @return
   */
  private List<String> analyzeDef(Graph<Node, Edge> graph, Node node, HashSet<Node> visited) {
    //    Graphs.predecessorListOf()
    List<String> res = new ArrayList<>();
    Set<Edge> inEdges =
        graph.incomingEdgesOf(node).stream()
            .filter(edge -> edge.getType().isStructural())
            .collect(Collectors.toSet());
    if (inEdges.isEmpty() || visited.contains(node)) {
      return res;
    }
    visited.add(node);
    for (Edge edge : inEdges) {
      Node srcNode = graph.getEdgeSource(edge);
      if (srcNode == node || visited.contains(node)) {
        continue;
      } else {
        if (srcNode.isInDiffHunk) {
          res.add(srcNode.diffHunkIndex);
        }
        res.addAll(analyzeDef(graph, srcNode, visited));
      }
    }
    return res;
  }

  /**
   * DFS to find all nodes that start from the current in diff hunks
   *
   * @param graph
   * @param node
   * @param visited
   * @return
   */
  private List<String> analyzeUse(Graph<Node, Edge> graph, Node node, HashSet<Node> visited) {
    List<String> res = new ArrayList<>();
    Set<Edge> outEdges =
        graph.outgoingEdgesOf(node).stream()
            .filter(edge -> !edge.getType().isStructural())
            .collect(Collectors.toSet());
    if (outEdges.isEmpty() || visited.contains(node)) {
      return res;
    }
    visited.add(node);
    for (Edge edge : outEdges) {
      Node tgtNode = graph.getEdgeTarget(edge);
      if (tgtNode == node || visited.contains(tgtNode)) {
        continue;
      } else {
        if (tgtNode.isInDiffHunk) {
          res.add(tgtNode.diffHunkIndex);
        }
        res.addAll(analyzeUse(graph, tgtNode, visited));
      }
    }
    return res;
  }

  /**
   * Accept a threshold to adjust and regenerate the result
   *
   * @param threshold
   */
  public Map<String, Group> generateGroups(Double threshold) {
    // remove edges under threshold from the diff graph
    Set<DiffEdge> edges = new HashSet<>(diffGraph.edgeSet());
    for (DiffEdge edge : edges) {
      if (edge.getWeight() < threshold) {
        diffGraph.removeEdge(edge);
      }
    }
    //    String diffGraphString = DiffGraphExporter.exportAsDotWithType(diffGraph);
    return generateGroups();
  }

  /**
   * Generate groups of related changes from the graph
   *
   * @return
   */
  public Map<String, Group> generateGroups() {
    //    String diffGraphString = DiffGraphExporter.exportAsDotWithType(diffGraph);

    Map<String, Group> generatedGroups = new LinkedHashMap<>();
    Set<DiffNode> individuals = new LinkedHashSet<>();
    Map<String, String> idToIndexMap = new HashMap<>();

    // partition of the current level (union-find/disjoint set)
    ConnectivityInspector inspector = new ConnectivityInspector(diffGraph);
    List<Set<DiffNode>> connectedSets = inspector.connectedSets();
    for (Set<DiffNode> diffNodesSet : connectedSets) {
      if (diffNodesSet.size() == 1) {
        // individual
        diffNodesSet.forEach(
            diffNode -> {
              individuals.add(diffNode);
              idToIndexMap.put(diffNode.getUUID(), diffNode.getIndex());
            });
      } else if (diffNodesSet.size() > 1) {
        Set<String> diffHunkIDs = new LinkedHashSet<>();
        // sort and deduplicate
        diffNodesSet =
            diffNodesSet.stream()
                .sorted(
                    Comparator.comparing(DiffNode::getFileIndex)
                        .thenComparing(DiffNode::getDiffHunkIndex))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<DiffEdgeType> edgeTypes = new ArrayList<>();
        for (DiffNode diffNode : diffNodesSet) {
          diffHunkIDs.add(diffNode.getUUID());
          diffGraph.outgoingEdgesOf(diffNode).stream()
              .forEach(diffEdge -> edgeTypes.add(diffEdge.getType()));
        }
        // get the most frequent edge type as the group label
        String groupID = createGroup(generatedGroups, diffHunkIDs, getIntentFromEdges(edgeTypes));
        updateIndexToGroupMap(diffNodesSet, groupID);
      }
    }

    assignIndividuals(generatedGroups, idToIndexMap, individuals);
    // group individuals with file type centrality
    Map<String, Set<String>> diffHunksByFileType = new HashMap<>();
    for (DiffNode node : individuals) {
      DiffFile file =
          diffFiles.stream()
              .filter(diffFile -> diffFile.getIndex().equals(node.getFileIndex()))
              .findFirst()
              .get();
      String fileType =
          file.getBaseRelativePath().isEmpty()
              ? Utils.getFileExtension(file.getBaseRelativePath())
              : Utils.getFileExtension(file.getCurrentRelativePath());
      if (!diffHunksByFileType.containsKey(fileType)) {
        diffHunksByFileType.put(fileType, new HashSet<>());
      }
      diffHunksByFileType.get(fileType).add(node.getUUID());
    }
    for (Map.Entry<String, Set<String>> entry : diffHunksByFileType.entrySet()) {
      createGroup(generatedGroups, entry.getValue(), GroupLabel.OTHER);
    }
    return generatedGroups;
  }

  private void updateIndexToGroupMap(Set<DiffNode> diffNodesSet, String groupID) {
    for (DiffNode diffNode : diffNodesSet) {
      this.indexToGroupMap.put(diffNode.getIndex(), groupID);
    }
  }

  private GroupLabel getIntentFromEdges(List<DiffEdgeType> edgeTypes) {
    if (edgeTypes.contains(DiffEdgeType.REFACTOR)) {
      return GroupLabel.REFACTOR;
    }
    DiffEdgeType edgeType = Utils.mostCommon(edgeTypes);
    switch (edgeType) {
      case SIMILAR:
        return GroupLabel.FIX;
      case MOVING:
      case REFORMAT:
        return GroupLabel.REFORMAT;
      case DOC:
        if (processNonJava) {
          return GroupLabel.DOC;
        }
      case RESOURCE:
        if (processNonJava) {
          return GroupLabel.RESOURCE;
        }
      case CONFIG:
        if (processNonJava) {
          return GroupLabel.CONFIG;
        }
      case NONJAVA:
        return GroupLabel.NONJAVA;
      case CLOSE:
      default:
        return GroupLabel.FEATURE;
    }
  }

  /**
   * Create a new group to group all diff hunk ids
   *
   * @param diffHunkIDs
   */
  private String createGroup(
      Map<String, Group> groups, Set<String> diffHunkIDs, GroupLabel intent) {
    if (!diffHunkIDs.isEmpty()) {
      String groupID = "group" + groups.size();
      Group group = new Group(repoID, repoName, groupID, new ArrayList<>(diffHunkIDs), intent);
      group.setCommitMsg(intent.toString().toLowerCase() + ": " + intent.label + " ...");
      groups.put(groupID, group);
      return groupID;
    }
    return "";
  }

  /** Add an individual diff hunk to its nearest group */
  private void assignIndividuals(
      Map<String, Group> groups, Map<String, String> idToIndexMap, Set<DiffNode> individuals) {
    Set<DiffNode> temp = new HashSet<>(individuals);
    for (DiffNode node : temp) {
      // find the file index and diff hunk index by node
      if (idToIndexMap.containsKey(node.getUUID())) {
        String index = idToIndexMap.get(node.getUUID());
        Pair<Integer, Integer> indices = Utils.parseIndices(index);
        // find the group of the nearest diff hunk
        // after sibling
        String after = indices.getLeft() + ":" + (indices.getRight() + 1);
        if (this.indexToGroupMap.containsKey(after)) {
          Group group = groups.get(this.indexToGroupMap.get(after));
          group.addDiffHunk(node.getUUID());
          this.indexToGroupMap.put(index, group.getGroupID());
          individuals.remove(node);
          continue;
        }
        // before sibling
        String before = indices.getLeft() + ":" + (indices.getRight() - 1);
        if (this.indexToGroupMap.containsKey(before)) {
          Group group = groups.get(this.indexToGroupMap.get(before));
          group.addDiffHunk(node.getUUID());
          this.indexToGroupMap.put(index, group.getGroupID());
          individuals.remove(node);
          continue;
        }
        // same parent file
        String parent = indices.getLeft() + ":0";
        if (this.indexToGroupMap.containsKey(parent)) {
          Group group = groups.get(this.indexToGroupMap.get(parent));
          group.addDiffHunk(node.getUUID());
          this.indexToGroupMap.put(index, group.getGroupID());
          individuals.remove(node);
          continue;
        }
      }
    }
  }

  /**
   * source --> target in order
   *
   * @param graph
   * @return
   */
  private Map<String, Set<String>> analyzeHardLinks(Graph<Node, Edge> graph) {
    Map<String, Set<String>> results = new HashMap<>();
    List<Node> diffEntities =
        graph.vertexSet().stream().filter(node -> node.isInDiffHunk).collect(Collectors.toList());
    for (Node node : diffEntities) {
      List<Node> defs =
          Graphs.predecessorListOf(graph, node).stream()
              .filter(n -> n.isInDiffHunk)
              .collect(Collectors.toList());
      if (!defs.isEmpty()) {
        for (Node u : defs) {
          if (u.getDiffHunkIndex().equals(node.getDiffHunkIndex())) {
            continue;
          } else if (!results.containsKey(node.diffHunkIndex)) {
            results.put(node.diffHunkIndex, new HashSet<>());
          }
          results.get(node.diffHunkIndex).add(u.getDiffHunkIndex());
        }
      }
      // direct one-hop dependency
      List<Node> uses =
          Graphs.successorListOf(graph, node).stream()
              .filter(n -> n.isInDiffHunk)
              .collect(Collectors.toList());
      if (!uses.isEmpty()) {
        for (Node u : uses) {
          if (u.getDiffHunkIndex().equals(node.getDiffHunkIndex())) {
            continue;
          } else if (!results.containsKey(node.diffHunkIndex)) {
            results.put(node.diffHunkIndex, new HashSet<>());
          }
          results.get(node.diffHunkIndex).add(u.getDiffHunkIndex());
        }
      }
    }
    return results;
  }

  /**
   * Build edges in the diff hunk graph for groups
   *
   * @param diffHunks
   * @param type
   */
  private void createEdges(Set<DiffHunk> diffHunks, DiffEdgeType type, double weight) {
    if (diffHunks.isEmpty()) {
      return;
    }

    List<DiffHunk> list = new ArrayList<>(diffHunks);
    if (diffHunks.size() == 1) {
      createEdge(list.get(0).getUniqueIndex(), list.get(0).getUniqueIndex(), type, weight);
    }
    // create groups and build edges in order
    for (int i = 0; i < list.size() - 1; i++) {
      if (i + 1 < list.size()) {
        createEdge(list.get(i).getUniqueIndex(), list.get(i + 1).getUniqueIndex(), type, weight);
      }
    }
  }

  /**
   * Create one edge in the diff graph
   *
   * @param source
   * @param target
   * @param type
   * @param weight
   */
  private void createEdge(String source, String target, DiffEdgeType type, double weight) {
    boolean success =
        diffGraph.addEdge(
            findNodeByIndex(source),
            findNodeByIndex(target),
            new DiffEdge(generateEdgeID(), type, weight));
    if (!success) {
      // in case of failure
      logger.error("Error when adding edge: " + source + "->" + target + "to diffGraph.");
    }
  }

  private Integer generateEdgeID() {
    return this.diffGraph.edgeSet().size() + 1;
  }

  private DiffNode findNodeByIndex(String index) {
    Optional<DiffNode> nodeOpt =
        diffGraph.vertexSet().stream()
            .filter(diffNode -> diffNode.getIndex().equals(index))
            .findAny();
    return nodeOpt.orElse(null);
  }

  private Set<DiffHunk> groupRefactorings(Set<DiffHunk> refDiffHunks) {
    //    Set<DiffHunk> refDiffHunks = new TreeSet<>(ascendingByIndexComparator());

    try {
      File rootFolder1 = new File(srcDirs.getLeft());
      File rootFolder2 = new File(srcDirs.getRight());

      UMLModel model1 = new UMLModelASTReader(rootFolder1).getUmlModel();
      UMLModel model2 = new UMLModelASTReader(rootFolder2).getUmlModel();
      UMLModelDiff modelDiff = model1.diff(model2);

      List<Refactoring> refactorings = modelDiff.getRefactorings();

      // for each refactoring, find the corresponding diff hunk
      for (Refactoring refactoring : refactorings) {
        // greedy style: put all refactorings into one group
        for (CodeRange range : refactoring.leftSide()) {
          Optional<DiffHunk> diffHunkOpt = getOverlappingDiffHunk(Version.BASE, range);
          if (diffHunkOpt.isPresent()) {
            DiffHunk diffHunk = diffHunkOpt.get();
            diffHunk.addRefAction(Utils.convertRefactoringToAction(refactoring));
            refDiffHunks.add(diffHunk);
          }
        }
        for (CodeRange range : refactoring.rightSide()) {
          Optional<DiffHunk> diffHunkOpt = getOverlappingDiffHunk(Version.CURRENT, range);
          if (diffHunkOpt.isPresent()) {
            DiffHunk diffHunk = diffHunkOpt.get();
            diffHunk.addRefAction(Utils.convertRefactoringToAction(refactoring));
            refDiffHunks.add(diffHunk);
          }
        }
      }
    } catch (RefactoringMinerTimedOutException | IOException e) {
      e.printStackTrace();
    }
    return refDiffHunks;
  }

  /**
   * Construct a comparator which rank the diffhunks firstly by fileIndex, secondly by diffHunkIndex
   *
   * @return
   */
  private Comparator<DiffHunk> ascendingByIndexComparator() {
    return Comparator.comparing(DiffHunk::getFileIndex).thenComparing(DiffHunk::getIndex);
  }

  /**
   * Compute textual similarity (and tree similarity) between two diff hunks
   *
   * @param diffHunk
   * @param diffHunk1
   * @return
   */
  private double estimateSimilarity(DiffHunk diffHunk, DiffHunk diffHunk1) {
    // ignore special cases: imports, empty, blank_lines
    if (diffHunk.getFileType().equals(FileType.JAVA)
        && diffHunk1.getFileType().equals(FileType.JAVA)) {
      if (diffHunk.getBaseHunk().getContentType().equals(ContentType.CODE)
          || diffHunk.getCurrentHunk().getContentType().equals(ContentType.CODE)) {
        // TODO check length to early stop, avoid too low similarity computation
        // TODO use tokens to compute instead of whole string
        // textual similarity
        double baseText =
            Utils.cosineStringSimilarity(
                Utils.convertListLinesToString(diffHunk.getBaseHunk().getCodeSnippet()),
                Utils.convertListLinesToString(diffHunk1.getBaseHunk().getCodeSnippet()));
        double currentText =
            Utils.cosineStringSimilarity(
                Utils.convertListLinesToString(diffHunk.getCurrentHunk().getCodeSnippet()),
                Utils.convertListLinesToString(diffHunk1.getCurrentHunk().getCodeSnippet()));
        // change action similarity
        double astSimi =
            Utils.computeListSimilarity(diffHunk.getAstActions(), diffHunk1.getAstActions());
        double refSimi =
            Utils.computeListSimilarity(diffHunk.getRefActions(), diffHunk1.getRefActions());
        return Utils.formatDouble((baseText + currentText + astSimi + refSimi) / 4);
      }
    }
    return 0D;
  }

  private double estimateCrossVersionSimilarity(DiffHunk diffHunk, DiffHunk diffHunk1) {
    // TODO: use token similarity instead
    String left =
        Utils.convertListLinesToString(
            diffHunk.getChangeType().equals(ChangeType.ADDED)
                ? diffHunk.getCurrentHunk().getCodeSnippet()
                : diffHunk.getBaseHunk().getCodeSnippet());
    String right =
        Utils.convertListLinesToString(
            diffHunk1.getChangeType().equals(ChangeType.ADDED)
                ? diffHunk1.getCurrentHunk().getCodeSnippet()
                : diffHunk1.getBaseHunk().getCodeSnippet());
    return Utils.formatDouble(Utils.tokenStringSimilarity(left, right));
  }

  /**
   * Estimate the location distance of two diff hunks, from both base and current
   *
   * @param diffHunk
   * @param diffHunk1
   * @return
   */
  private int estimateDistance(DiffHunk diffHunk, DiffHunk diffHunk1) {
    int distance = -1; // -1 means no way to compute distance
    if (diffHunk.getFileType().equals(FileType.JAVA)
        && diffHunk1.getFileType().equals(FileType.JAVA)) {
      Optional<DiffNode> diffNodeOpt1 =
          diffGraph.vertexSet().stream()
              .filter(node -> node.getIndex().equals(diffHunk.getUniqueIndex()))
              .findAny();
      Optional<DiffNode> diffNodeOpt2 =
          diffGraph.vertexSet().stream()
              .filter(node -> node.getIndex().equals(diffHunk1.getUniqueIndex()))
              .findAny();
      if (diffNodeOpt1.isPresent() && diffNodeOpt2.isPresent()) {
        DiffNode diffNode1 = diffNodeOpt1.get();
        DiffNode diffNode2 = diffNodeOpt2.get();
        int disBase = -1;
        int disCurrent = -1;
        if (!diffNode1.getBaseHierarchy().isEmpty() && !diffNode2.getBaseHierarchy().isEmpty()) {
          disBase = compareHierarchy(diffNode1.getBaseHierarchy(), diffNode2.getBaseHierarchy());
        }
        if (!diffNode1.getCurrentHierarchy().isEmpty()
            && !diffNode2.getCurrentHierarchy().isEmpty()) {
          disCurrent =
              compareHierarchy(diffNode1.getCurrentHierarchy(), diffNode2.getCurrentHierarchy());
        }
        // use the min distance
        if (disBase < 0) {
          distance = disCurrent;
        } else if (disCurrent < 0) {
          distance = disBase;
        } else {
          distance = Math.min(disBase, disCurrent);
        }
      }
    }
    return distance;
  }

  /**
   * Compare hierarchy to compute the location distance
   *
   * @return
   */
  private int compareHierarchy(Map<String, Integer> hier1, Map<String, Integer> hier2) {
    // TODO use index to estimate distance
    if (hier1.isEmpty() || hier2.isEmpty()) {
      return -1;
    }
    int res = 4;
    for (Map.Entry<String, Integer> entry : hier1.entrySet()) {
      if (hier2.containsKey(entry.getKey())) {
        if (hier2.get(entry.getKey()).equals(entry.getValue())) {
          int t = -1;
          switch (entry.getKey()) {
            case "hunk":
              t = 0;
              break;
            case "member":
              t = 1;
              break;
            case "class":
              t = 2;
              break;
            case "package":
              t = 3;
              break;
          }
          res = Math.min(res, t);
        }
      }
    }
    return res;
  }

  /**
   * Get the hierarchy for hunk nodes Hierarchy is a map of up-to-top parent ids
   *
   * @param graph
   * @param nodes
   * @param diffHunkIndex
   * @return
   */
  private Map<String, Integer> getHierarchy(
      Graph<Node, Edge> graph, List<Node> nodes, String diffHunkIndex) {
    Map<String, Integer> hierarchy = new HashMap<>();
    Optional<Node> nodeOpt =
        nodes.stream().filter(node -> node.getDiffHunkIndex().equals(diffHunkIndex)).findAny();
    if (nodeOpt.isPresent()) {
      Node node = nodeOpt.get();
      hierarchy.put("hunk", node.getId());
      // find parents from incoming edges
      findAncestors(graph, node, hierarchy);
    }
    return hierarchy;
  }

  /**
   * Find the hierarchical definition ancestors (memeber, class, package)
   *
   * @param graph
   * @param node
   * @param hierarchy
   */
  private void findAncestors(Graph<Node, Edge> graph, Node node, Map<String, Integer> hierarchy) {
    Set<Edge> incomingEdges =
        graph.incomingEdgesOf(node).stream()
            .filter(edge -> edge.getType().isStructural()) // contain or define
            .collect(Collectors.toSet());
    for (Edge edge : incomingEdges) {
      Node srcNode = graph.getEdgeSource(edge);
      switch (srcNode.getType()) {
        case CLASS:
        case INTERFACE:
        case ENUM:
        case ANNOTATION:
          hierarchy.put("class", srcNode.getId());
          findAncestors(graph, srcNode, hierarchy);
          break;
        case METHOD:
        case FIELD:
        case ENUM_CONSTANT:
        case ANNOTATION_MEMBER:
        case INITIALIZER_BLOCK:
          hierarchy.put("member", srcNode.getId());
          findAncestors(graph, srcNode, hierarchy);
          break;
        case PACKAGE:
          hierarchy.put("package", srcNode.getId());
          break;
      }
    }
  }

  /**
   * Try to find the overlapping diff hunk according to the code range
   *
   * @return
   */
  private Optional<DiffHunk> getOverlappingDiffHunk(Version version, CodeRange codeRange) {
    for (DiffFile diffFile : diffFiles) {
      if (!codeRange.getFilePath().isEmpty()
          && !diffFile.getRelativePathOf(version).isEmpty()
          && codeRange.getFilePath().endsWith(diffFile.getRelativePathOf(version))) {
        for (DiffHunk diffHunk : diffFile.getDiffHunks()) {
          Pair<Integer, Integer> hunkRange = diffHunk.getCodeRangeOf(version);
          // overlapping: !(b1 < a2 || b2 < a1) = (b1 >= a2 && b2 >= a1)
          if (codeRange.getEndLine() >= hunkRange.getLeft()
              && hunkRange.getRight() >= codeRange.getStartLine()) {
            // suppose that one range is related with only one diff hunk
            return Optional.of(diffHunk);
          }
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Check if a diff hunk only contains reformatting changes with whitespace, indentation,
   * punctuation, etc.
   *
   * @param diffHunk
   * @return
   */
  public boolean checkReformatting(DiffHunk diffHunk) {
    return Utils.convertListToStringNoFormat(diffHunk.getBaseHunk().getCodeSnippet())
        .equals(Utils.convertListToStringNoFormat(diffHunk.getCurrentHunk().getCodeSnippet()));
  }

  public void enableRefDetection(boolean enable) {
    this.detectRefs = enable;
  }

  public void processNonJavaChanges(boolean process) {
    this.processNonJava = process;
  }

  public void setMinSimilarity(double minSimilarity) {
    this.minSimilarity = minSimilarity;
  }

  public void setMaxDistance(int maxDistance) {
    this.maxDistance = maxDistance;
  }
}
