package com.github.smartcommit.core;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.constant.GroupLabel;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.model.diffgraph.DiffEdge;
import com.github.smartcommit.model.diffgraph.DiffEdgeType;
import com.github.smartcommit.model.diffgraph.DiffNode;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.model.graph.NodeType;
import com.github.smartcommit.util.Utils;
import gr.uom.java.xmi.UMLModel;
import gr.uom.java.xmi.UMLModelASTReader;
import gr.uom.java.xmi.diff.CodeRange;
import gr.uom.java.xmi.diff.UMLModelDiff;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringMinerTimedOutException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GroupGeneratorOld {
  private static final Logger logger = Logger.getLogger(GroupGeneratorOld.class);

  private String repoID;
  private String repoName;
  private Pair<String, String> srcDirs;
  private List<DiffFile> diffFiles;
  private List<DiffHunk> diffHunks;

  // options
  private Double similarityThreshold;
  private Integer distanceThreshold;

  private Graph<Node, Edge> baseGraph;
  private Graph<Node, Edge> currentGraph;
  private Map<String, Group> generatedGroups;
  private Graph<DiffNode, DiffEdge> diffHunkGraph;
  private Map<String, String> diffHunkID2GroupID;

  public GroupGeneratorOld(
      String repoID,
      String repoName,
      Pair<String, String> srcDirs,
      Double similarityThreshold,
      Integer distanceThreshold,
      List<DiffFile> diffFiles,
      List<DiffHunk> diffHunks,
      Graph<Node, Edge> baseGraph,
      Graph<Node, Edge> currentGraph) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.srcDirs = srcDirs;
    this.diffFiles = diffFiles;
    this.diffHunks = diffHunks;

    this.similarityThreshold = similarityThreshold;
    this.distanceThreshold = distanceThreshold;

    this.baseGraph = baseGraph;
    this.currentGraph = currentGraph;
    this.generatedGroups = new TreeMap<>();
    this.diffHunkGraph = initDiffViewGraph(diffHunks);

    this.diffHunkID2GroupID = new HashMap<>();
  }

  /** Put non-java hunks as a whole file in the first group, if there exists */
  public void analyzeNonJavaFiles() {
    Set<DiffHunk> nonJavaDiffHunks = new TreeSet<>(ascendingByIndexComparator());

    for (DiffFile diffFile : diffFiles) {
      if (!diffFile.getFileType().equals(FileType.JAVA)) {
        for (DiffHunk diffHunk : diffFile.getDiffHunks()) {
          nonJavaDiffHunks.add(diffHunk);
        }
      }
    }
    if (!nonJavaDiffHunks.isEmpty()) {
      Set<String> nonJavaDiffHunkIDs = new LinkedHashSet<>();
      nonJavaDiffHunks.forEach(diffHunk -> nonJavaDiffHunkIDs.add(diffHunk.getUUID()));
      createGroup(nonJavaDiffHunkIDs, GroupLabel.NONJAVA);
    }
  }

  // TODO: group the import in fine-grained way
  public void analyzeImports() {}

  public void analyzeHardLinks() {
    Map<String, Set<String>> unionHardLinks =
        mergeTwoMaps(analyzeDefUse(baseGraph), analyzeDefUse(currentGraph));
    for (Map.Entry<String, Set<String>> entry : unionHardLinks.entrySet()) {
      String id1 = getDiffHunkIDFromIndex(diffFiles, entry.getKey());
      for (String target : entry.getValue()) {
        String id2 = getDiffHunkIDFromIndex(diffFiles, target);
        if (!id1.equals(id2) && !checkIfGrouped(id1) && !checkIfGrouped(id2)) {
          boolean success =
              diffHunkGraph.addEdge(
                  findNodeByIndex(entry.getKey()),
                  findNodeByIndex(target),
                  new DiffEdge(generateEdgeID(), DiffEdgeType.DEPEND, 1.0));
          if (!success) {
            logger.error(
                "Error when adding edge: "
                    + findNodeByIndex(entry.getKey())
                    + "->"
                    + findNodeByIndex(target)
                    + "to diffHunkGraph.");
          }
        }
      }
    }
  }

  /**
   * Soft links:
   * <li>1. reformat-only diff hunks
   * <li>2. systematic edits and similar edits (TODO: to optimize)
   * <li>3. cross-version: moving</>
   */
  public void analyzeSoftLinks() {
    Set<DiffHunk> formatOnlyDiffHunks = new TreeSet<>(ascendingByIndexComparator());
    for (int i = 0; i < diffHunks.size(); ++i) {
      DiffHunk diffHunk1 = diffHunks.get(i);
      // check format only diff hunks
      if (Utils.convertListToStringNoFormat(diffHunk1.getBaseHunk().getCodeSnippet())
          .equals(Utils.convertListToStringNoFormat(diffHunk1.getCurrentHunk().getCodeSnippet()))) {
        formatOnlyDiffHunks.add(diffHunk1);
        continue;
      }
      // else, compare the diff hunk with other diff hunks
      for (int j = i + 1; j < diffHunks.size(); ++j) {
        DiffHunk diffHunk2 = diffHunks.get(j);
        if (!diffHunk1.getUniqueIndex().equals(diffHunk2.getUniqueIndex())) {
          // 1. compute the distance
          if (distanceThreshold > 0) {
            Integer distance = estimateDistance(diffHunk1, diffHunk2);
            if (distance >= 0 && distance <= distanceThreshold) {
              boolean success =
                  diffHunkGraph.addEdge(
                      findNodeByIndex(diffHunk2.getUniqueIndex()),
                      findNodeByIndex(diffHunk1.getUniqueIndex()),
                      new DiffEdge(generateEdgeID(), DiffEdgeType.CLOSE, new Double(0 - distance)));
              if (!success) {
                // in case of failure
                logger.error(
                    "Error when adding edge: "
                        + findNodeByIndex(diffHunk2.getUniqueIndex())
                        + "->"
                        + findNodeByIndex(diffHunk1.getUniqueIndex())
                        + "to diffHunkGraph.");
              }
            }
          }

          // 2. compute the similarity
          if (diffHunk2.getBaseHunk().getCodeSnippet().size()
                  == diffHunk1.getBaseHunk().getCodeSnippet().size()
              && diffHunk2.getCurrentHunk().getCodeSnippet().size()
                  == diffHunk1.getCurrentHunk().getCodeSnippet().size()) {
            Double similarity = estimateSimilarity(diffHunk2, diffHunk1);
            if (similarity >= similarityThreshold
                && !formatOnlyDiffHunks.contains(diffHunk1)
                && !formatOnlyDiffHunks.contains(diffHunk2)) {
              boolean success =
                  diffHunkGraph.addEdge(
                      findNodeByIndex(diffHunk2.getUniqueIndex()),
                      findNodeByIndex(diffHunk1.getUniqueIndex()),
                      new DiffEdge(generateEdgeID(), DiffEdgeType.SIMILAR, similarity));
              if (!success) {
                // in case of failure
                logger.error(
                    "Error when adding edge: "
                        + findNodeByIndex(diffHunk2.getUniqueIndex())
                        + "->"
                        + findNodeByIndex(diffHunk1.getUniqueIndex())
                        + "to diffHunkGraph.");
              }
            }
          }
        }
      }
    }
    if (!formatOnlyDiffHunks.isEmpty()) {
      Set<String> formatOnlyDiffHunkIDs = new LinkedHashSet<>();
      formatOnlyDiffHunks.forEach(diffHunk -> formatOnlyDiffHunkIDs.add(diffHunk.getUUID()));
      createGroup(formatOnlyDiffHunkIDs, GroupLabel.REFORMAT);
    }
  }

  /**
   * Estimate the location distance of two diff hunks, from both base and current
   *
   * @param diffHunk1
   * @param diffHunk2
   * @return
   */
  private Integer estimateDistance(DiffHunk diffHunk1, DiffHunk diffHunk2) {
    Optional<DiffNode> diffNodeOpt1 =
        diffHunkGraph.vertexSet().stream()
            .filter(node -> node.getIndex().equals(diffHunk1.getUniqueIndex()))
            .findAny();
    Optional<DiffNode> diffNodeOpt2 =
        diffHunkGraph.vertexSet().stream()
            .filter(node -> node.getIndex().equals(diffHunk2.getUniqueIndex()))
            .findAny();
    Integer distance = -1; // far away
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
    return distance;
  }

  /**
   * Compare hierarchy to compute the location distance
   *
   * @return
   */
  private int compareHierarchy(Map<String, Integer> hier1, Map<String, Integer> hier2) {
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

  private double estimateSimilarity(DiffHunk diffHunk, DiffHunk diffHunk1) {
    // ignore special cases: imports, empty, blank_lines
    if (diffHunk.getBaseHunk().getContentType().equals(ContentType.CODE)
        || diffHunk.getCurrentHunk().getContentType().equals(ContentType.CODE)) {
      double baseSimi =
          Utils.cosineStringSimilarity(
              Utils.convertListToStringNoFormat(diffHunk.getBaseHunk().getCodeSnippet()),
              Utils.convertListToStringNoFormat(diffHunk1.getBaseHunk().getCodeSnippet()));
      double currentSimi =
          Utils.cosineStringSimilarity(
              Utils.convertListToStringNoFormat(diffHunk.getCurrentHunk().getCodeSnippet()),
              Utils.convertListToStringNoFormat(diffHunk1.getCurrentHunk().getCodeSnippet()));
      return (double) Math.round((baseSimi + currentSimi) / 2 * 100) / 100;
    } else {
      return 0D;
    }
  }

  private static Map<String, Set<String>> mergeTwoMaps(
      Map<String, Set<String>> map1, Map<String, Set<String>> map2) {
    map2.forEach(
        (key, value) ->
            map1.merge(
                key,
                value,
                (v1, v2) -> {
                  v1.addAll(v2);
                  return v1;
                }));

    return map1;
  }

  private Map<String, Set<String>> analyzeDefUse2(Graph<Node, Edge> graph) {
    Map<String, Set<String>> results = new HashMap<>();

    ConnectivityInspector inspector = new ConnectivityInspector(graph);
    List<Node> hunkNodes =
        graph.vertexSet().stream()
            .filter(node -> node.getType().equals(NodeType.HUNK))
            .collect(Collectors.toList());
    for (int i = 0; i < hunkNodes.size(); ++i) {
      Node srcNode = hunkNodes.get(i);
      Set<String> connectedHunkNodes = new HashSet<>();
      for (int j = i + 1; j < hunkNodes.size(); ++j) {
        if (inspector.pathExists(srcNode, hunkNodes.get(j))) {
          connectedHunkNodes.add(hunkNodes.get(j).diffHunkIndex);
        }
      }
      if (!connectedHunkNodes.isEmpty()) {
        if (!results.containsKey(srcNode.diffHunkIndex)) {
          results.put(srcNode.diffHunkIndex, new HashSet<>());
        }
        results.get(srcNode.diffHunkIndex).addAll(connectedHunkNodes);
      }
    }
    return results;
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

  private List<String> analyzeDef(Graph<Node, Edge> graph, Node node, HashSet<Node> visited) {
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

  public Graph<DiffNode, DiffEdge> getDiffHunkGraph() {
    return diffHunkGraph;
  }

  private String getDiffHunkIDFromIndex(List<DiffFile> diffFiles, String index) {
    Pair<Integer, Integer> indices = Utils.parseIndices(index);
    if (indices.getLeft() >= 0 && indices.getRight() >= 0) {
      DiffFile diffFile = diffFiles.get(indices.getLeft());
      return diffFile.getFileID()
          + ":"
          + diffFile.getDiffHunks().get(indices.getRight()).getDiffHunkID();
    }
    return ":";
  }

  /**
   * Analyze refactorings with RMiner
   *
   * @param tempDir
   */
  public void analyzeRefactorings(String tempDir) {
    try {
      File rootFolder1 = new File(srcDirs.getLeft());
      File rootFolder2 = new File(srcDirs.getRight());

      UMLModel model1 = new UMLModelASTReader(rootFolder1).getUmlModel();
      UMLModel model2 = new UMLModelASTReader(rootFolder2).getUmlModel();
      UMLModelDiff modelDiff = model1.diff(model2);

      List<Refactoring> refactorings = modelDiff.getRefactorings();

      // for each refactoring, find the corresponding diff hunk
      Set<DiffHunk> refDiffHunks = new TreeSet<>(ascendingByIndexComparator());
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
      if (!refDiffHunks.isEmpty()) {
        Set<String> diffHunkIDs = new LinkedHashSet<>();
        refDiffHunks.forEach(diffHunk -> diffHunkIDs.add(diffHunk.getUUID()));
        createGroup(diffHunkIDs, GroupLabel.REFACTOR);
      }
    } catch (RefactoringMinerTimedOutException | IOException e) {
      e.printStackTrace();
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
   * Generate groups from the diff hunk graph
   *
   * @return
   */
  public Map<String, Group> generateGroups() {
    ConnectivityInspector inspector = new ConnectivityInspector(diffHunkGraph);
    List<Set<DiffNode>> connectedSets = inspector.connectedSets();
    Set<String> individuals = new LinkedHashSet<>();
    for (Set<DiffNode> diffNodesSet : connectedSets) {
      if (diffNodesSet.size() > 1) {
        Set<String> diffHunkIDs = new LinkedHashSet<>();
        // sort by increasing index (fileIndex:diffHunkIndex)
        diffNodesSet =
            diffNodesSet.stream()
                .sorted(
                    Comparator.comparing(DiffNode::getFileIndex)
                        .thenComparing(DiffNode::getDiffHunkIndex))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String existingGroupID = "";
        for (DiffNode diffNode : diffNodesSet) {
          // if not grouped yet, create a new group
          if (!checkIfGrouped(diffNode.getUUID())) {
            diffHunkIDs.add(diffNode.getUUID());
          } else {
            // if ONE of the nodes has been grouped, add others into the existing group and continue
            // to the next connected set
            diffHunkIDs = new LinkedHashSet<>();
            existingGroupID = diffHunkID2GroupID.get(diffNode.getUUID());
            break;
          }
        }
        if (!existingGroupID.isEmpty()) {
          // if ONE of the nodes has been grouped, add others into the existing group
          Group group = generatedGroups.get(existingGroupID);
          Set<String> temp = new LinkedHashSet<>();
          diffNodesSet.forEach(node -> temp.add(node.getUUID()));
          addToGroup(group, temp);
          // continue to the next connected set
          continue;
        } else if (!diffHunkIDs.isEmpty()) {
          createGroup(diffHunkIDs, GroupLabel.FEATURE);
        }
      } else {
        // assert: diffNodesSet.size()==1
        diffNodesSet.forEach(
            diffNode -> {
              if (!checkIfGrouped(diffNode.getUUID())) {
                individuals.add(diffNode.getUUID());
              }
            });
      }
    }

    // individual nodes are sorted by nature
    if (individuals.size() <= 3) {
      for (String individual : individuals) {
        createGroup(new HashSet<>(Arrays.asList(individual)), GroupLabel.OTHER);
      }
    } else {
      createGroup(individuals, GroupLabel.OTHER);
    }

    //    BiconnectivityInspector inspector1 =
    //            new BiconnectivityInspector(diffHunkGraph);
    //    inspector1.getConnectedComponents();
    return generatedGroups;
  }

  /**
   * Create and build the graph vertex set to represent and visualize links of diff hunks
   *
   * @param diffHunks
   * @return
   */
  private Graph<DiffNode, DiffEdge> initDiffViewGraph(List<DiffHunk> diffHunks) {
    Graph<DiffNode, DiffEdge> diffViewGraph =
        GraphTypeBuilder.<DiffNode, DiffEdge>directed()
            .allowingMultipleEdges(true)
            .allowingSelfLoops(false)
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

      diffViewGraph.addVertex(diffNode);
    }
    return diffViewGraph;
  }

  /**
   * Get the hierarchy for hunk nodes
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

  private DiffNode findNodeByIndex(String index) {
    Optional<DiffNode> nodeOpt =
        diffHunkGraph.vertexSet().stream()
            .filter(diffNode -> diffNode.getIndex().equals(index))
            .findAny();
    return nodeOpt.orElse(null);
  }

  /**
   * Create new group by appending after the current generateGroups
   *
   * @param diffHunkIDs
   */
  private void createGroup(Set<String> diffHunkIDs, GroupLabel intent) {
    if (!diffHunkIDs.isEmpty()) {
      String groupID = "group" + generatedGroups.size();
      Group group = new Group(repoID, repoName, groupID, new ArrayList<>(diffHunkIDs), intent);
      group.setCommitMsg(intent.label);
      // bidirectional mapping
      diffHunkIDs.forEach(id -> diffHunkID2GroupID.put(id, groupID));
      generatedGroups.put(groupID, group);
    }
  }

  /**
   * Add a set of diff hu
   *
   * @param group
   * @param diffHunkIDs
   */
  private void addToGroup(Group group, Set<String> diffHunkIDs) {
    for (String id : diffHunkIDs) {
      if (!group.getDiffHunkIDs().contains(id)) {
        group.getDiffHunkIDs().add(id);
        diffHunkID2GroupID.put(id, group.getGroupID());
      }
    }
  }

  private Integer generateNodeID() {
    return this.diffHunkGraph.vertexSet().size() + 1;
  }

  private Boolean checkIfGrouped(String diffHunkID) {
    return this.diffHunkID2GroupID.containsKey(diffHunkID);
  }

  /**
   * Construct a comparator which rank the diffhunks firstly by fileIndex, secondly by diffHunkIndex
   *
   * @return
   */
  private Comparator<DiffHunk> ascendingByIndexComparator() {
    return Comparator.comparing(DiffHunk::getFileIndex).thenComparing(DiffHunk::getIndex);
  }

  private Integer generateEdgeID() {
    return this.diffHunkGraph.edgeSet().size() + 1;
  }

  public Map<String, Group> getGeneratedGroups() {
    return generatedGroups;
  }
}
