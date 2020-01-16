package com.github.smartcommit.core;

import com.github.smartcommit.io.DiffGraphExporter;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.constant.GroupLabel;
import com.github.smartcommit.model.diffgraph.DiffEdge;
import com.github.smartcommit.model.diffgraph.DiffEdgeType;
import com.github.smartcommit.model.diffgraph.DiffNode;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.model.graph.NodeType;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class GroupGenerator {

  private String repoID;
  private String repoName;
  private Double threshold;
  private List<DiffFile> diffFiles;
  private List<DiffHunk> diffHunks;

  private Graph<Node, Edge> baseGraph;
  private Graph<Node, Edge> currentGraph;
  private Map<String, Group> generatedGroups;
  private Graph<DiffNode, DiffEdge> diffHunkGraph;
  private Map<String, String> diffHunkID2GroupID;

  public GroupGenerator(
      String repoID,
      String repoName,
      Double threshold,
      List<DiffFile> diffFiles,
      List<DiffHunk> diffHunks,
      Graph<Node, Edge> baseGraph,
      Graph<Node, Edge> currentGraph) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.threshold = threshold;
    this.diffFiles = diffFiles;
    this.diffHunks = diffHunks;

    this.baseGraph = baseGraph;
    this.currentGraph = currentGraph;
    this.generatedGroups = new TreeMap<>();
    this.diffHunkGraph = initDiffViewGraph(diffHunks);

    this.diffHunkID2GroupID = new HashMap<>();
  }

  /** Put non-java hunks as a whole file in the first group, if there exists */
  public void analyzeNonJavaFiles() {
    Set<String> nonJavaDiffHunks = new LinkedHashSet<>();
    for (DiffFile diffFile : diffFiles) {
      if (!diffFile.getFileType().equals(FileType.JAVA)) {
        for (DiffHunk diffHunk : diffFile.getDiffHunks()) {
          nonJavaDiffHunks.add(diffHunk.getUUID());
        }
      }
    }
    if (nonJavaDiffHunks.size() > 0) {
      createGroup(nonJavaDiffHunks, GroupLabel.NONJAVA);
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
        if (!id1.equals(id2)
            && !diffHunkID2GroupID.containsKey(id1)
            && !diffHunkID2GroupID.containsKey(id2)) {
          diffHunkGraph.addEdge(
              findNodeByIndex(entry.getKey()),
              findNodeByIndex(target),
              new DiffEdge(generateEdgeID(), DiffEdgeType.HARD, 1.0));
        }
      }
    }
  }

  /**
   * Soft links:
   * <li>1. reformat-only diff hunks
   * <li>2. systematic edits and similar edits
   */
  public void analyzeSoftLinks() {
    Set<String> formatOnlyDiffHunks = new LinkedHashSet<>();
    for (int i = 0; i < diffHunks.size(); ++i) {
      DiffHunk diffHunk1 = diffHunks.get(i);
      // check format only diff hunks
      if (Utils.convertListToStringNoFormat(diffHunk1.getBaseHunk().getCodeSnippet())
          .equals(Utils.convertListToStringNoFormat(diffHunk1.getCurrentHunk().getCodeSnippet()))) {
        formatOnlyDiffHunks.add(diffHunk1.getUUID());
        continue;
      }
      // else, compare the diff hunk with other diff hunks
      for (int j = i + 1; j < diffHunks.size(); ++j) {
        DiffHunk diffHunk2 = diffHunks.get(j);
        if (diffHunk2.getBaseHunk().getCodeSnippet().size()
                == diffHunk1.getBaseHunk().getCodeSnippet().size()
            && diffHunk2.getCurrentHunk().getCodeSnippet().size()
                == diffHunk1.getCurrentHunk().getCodeSnippet().size()) {
          Double similarity = estimateSimilarity(diffHunk2, diffHunk1);
          if (similarity >= threshold
              && !formatOnlyDiffHunks.contains(diffHunk1.getUUID())
              && !formatOnlyDiffHunks.contains(diffHunk2.getUUID())) {
            boolean success =
                diffHunkGraph.addEdge(
                    findNodeByIndex(diffHunk2.getUniqueIndex()),
                    findNodeByIndex(diffHunk1.getUniqueIndex()),
                    new DiffEdge(generateEdgeID(), DiffEdgeType.SOFT, similarity));
          }
        }
      }
    }
    if (!formatOnlyDiffHunks.isEmpty()) {
      createGroup(formatOnlyDiffHunks, GroupLabel.REFORMAT);
    }
  }

  private double estimateSimilarity(DiffHunk diffHunk, DiffHunk diffHunk1) {
    // ignore special cases: imports, empty, blank_lines
    if (diffHunk.getBaseHunk().getContentType().equals(ContentType.CODE)
        || diffHunk.getCurrentHunk().getContentType().equals(ContentType.CODE)) {
      double baseSimi =
          Utils.computeStringSimilarity(
              Utils.convertListToStringNoFormat(diffHunk.getBaseHunk().getCodeSnippet()),
              Utils.convertListToStringNoFormat(diffHunk1.getBaseHunk().getCodeSnippet()));
      double currentSimi =
          Utils.computeStringSimilarity(
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
    Pair<Integer, Integer> indices = Utils.parseIndicesFromString(index);
    if (indices.getLeft() >= 0 && indices.getRight() >= 0) {
      DiffFile diffFile = diffFiles.get(indices.getLeft());
      return diffFile.getFileID()
          + ":"
          + diffFile.getDiffHunks().get(indices.getRight()).getDiffHunkID();
    }
    return ":";
  }

  public void exportGroupingResults(String outputDir) {
    // visualize the diff hunk graph
    String diffGraphString = DiffGraphExporter.exportAsDotWithType(diffHunkGraph);

    ConnectivityInspector inspector = new ConnectivityInspector(diffHunkGraph);
    List<Set<DiffNode>> connectedSets = inspector.connectedSets();
    Set<String> individuals = new LinkedHashSet<>();
    for (Set<DiffNode> diffNodesSet : connectedSets) {
      if (diffNodesSet.size() > 1) {
        Set<String> diffHunkIDs = new LinkedHashSet<>();
        diffNodesSet.forEach(diffNode -> diffHunkIDs.add(diffNode.getUUID()));
        createGroup(diffHunkIDs, GroupLabel.LINKED);
      } else {
        // assert: diffNodesSet.size()==1
        diffNodesSet.forEach(
            diffNode -> {
              if (!diffHunkID2GroupID.containsKey(diffNode.getUUID())) {
                individuals.add(diffNode.getUUID());
              }
            });
      }
    }

    createGroup(individuals, GroupLabel.OTHER);

    //    BiconnectivityInspector inspector1 =
    //            new BiconnectivityInspector(diffHunkGraph);
    //    inspector1.getConnectedComponents();

    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    for (Map.Entry<String, Group> entry : generatedGroups.entrySet()) {
      Utils.writeStringToFile(
          gson.toJson(entry.getValue()),
          outputDir
              + File.separator
              + "generated_groups"
              + File.separator
              + entry.getKey()
              + ".json");
      // the copy to accept the user feedback
      Utils.writeStringToFile(
          gson.toJson(entry.getValue()),
          outputDir + File.separator + "manual_groups" + File.separator + entry.getKey() + ".json");
    }
  }

  private Graph<DiffNode, DiffEdge> initDiffViewGraph(List<DiffHunk> diffHunks) {
    Graph<DiffNode, DiffEdge> diffViewGraph =
        GraphTypeBuilder.<DiffNode, DiffEdge>directed()
            .allowingMultipleEdges(false)
            .allowingSelfLoops(false)
            .edgeClass(DiffEdge.class)
            .weighted(true)
            .buildGraph();
    int nodeID = 0;
    for (DiffHunk diffHunk : diffHunks) {
      DiffNode diffNode = new DiffNode(nodeID++, diffHunk.getUniqueIndex(), diffHunk.getUUID());
      diffViewGraph.addVertex(diffNode);
    }
    return diffViewGraph;
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
  private void createGroup(Set<String> diffHunkIDs, GroupLabel label) {
    if (!diffHunkIDs.isEmpty()) {
      String groupID = "group" + generatedGroups.size();
      Group group = new Group(repoID, repoName, groupID, new ArrayList<>(diffHunkIDs), label);
      // bidirectional mapping
      diffHunkIDs.forEach(id -> diffHunkID2GroupID.put(id, groupID));
      generatedGroups.put(groupID, group);
    }
  }

  private Integer generateNodeID() {
    return this.diffHunkGraph.vertexSet().size() + 1;
  }

  private Integer generateEdgeID() {
    return this.diffHunkGraph.edgeSet().size() + 1;
  }

  public Map<String, Group> getGeneratedGroups() {
    return generatedGroups;
  }
}
