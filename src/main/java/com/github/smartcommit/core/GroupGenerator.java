package com.github.smartcommit.core;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.diffgraph.DiffEdge;
import com.github.smartcommit.model.diffgraph.DiffEdgeType;
import com.github.smartcommit.model.diffgraph.DiffNode;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
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

  private Map<String, String> alreadyGrouped;

  private Graph<Node, Edge> baseGraph;
  private Graph<Node, Edge> currentGraph;
  private Map<String, Group> generatedGroups;
  private Graph<DiffNode, DiffEdge> diffHunkGraph;

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

    this.alreadyGrouped = new HashMap<>();

    this.baseGraph = baseGraph;
    this.currentGraph = currentGraph;
    this.generatedGroups = new TreeMap<>();
    this.diffHunkGraph = initDiffViewGraph(diffHunks);
  }

  /** Put non-java hunks as a whole file in the first group, if there exists */
  public void analyzeNonJavaFiles() {
    List<String> nonJavaDiffHunks = new ArrayList<>();
    for (DiffFile diffFile : diffFiles) {
      if (!diffFile.getFileType().equals(FileType.JAVA)) {
        String diffHunkID = diffFile.getFileID() + ":" + diffFile.getFileID();
        nonJavaDiffHunks.add(diffHunkID);
      }
    }
    if (nonJavaDiffHunks.size() > 0) {
      String groupID = "group" + generatedGroups.size();
      nonJavaDiffHunks.forEach(id -> alreadyGrouped.put(id, groupID));
      Group nonJavaGroup = new Group(repoID, repoName, groupID, nonJavaDiffHunks);
      generatedGroups.put(groupID, nonJavaGroup);
    }
  }

  // TODO: group the import in fine-grained way
  public void analyzeImports() {}

  /** Group the remaining diff hunks in the last group */
  public void analyzeRemainingDiffHunks() {
    List<String> remainingDiffHunks = new ArrayList<>();
    for (DiffHunk diffHunk : diffHunks) {
      String id = diffHunk.getFileID() + ":" + diffHunk.getDiffHunkID();
      if (!alreadyGrouped.containsKey(id)) {
        remainingDiffHunks.add(id);
      }
    }

    if (remainingDiffHunks.size() > 0) {
      String groupID = "group" + generatedGroups.size();
      remainingDiffHunks.forEach(id -> alreadyGrouped.put(id, groupID));
      Group group = new Group(repoID, repoName, groupID, new ArrayList<>(remainingDiffHunks));
      generatedGroups.put(groupID, group);
    }
  }

  public void analyzeHardLinks() {
    Map<String, List<String>> unionHardLinks =
        combineHardLinks(analyzeDefUse(baseGraph), analyzeDefUse(currentGraph));
    for (Map.Entry<String, List<String>> entry : unionHardLinks.entrySet()) {
      String id1 = getDiffHunkIDFromIndex(diffFiles, entry.getKey());
      for (String target : entry.getValue()) {
        String id2 = getDiffHunkIDFromIndex(diffFiles, target);
        if (entry.getKey() != target) {
          diffHunkGraph.addEdge(
              findNodeByIndex(entry.getKey()),
              findNodeByIndex(target),
              new DiffEdge(generateEdgeID(), DiffEdgeType.HARD, 1.0));
          String groupID = groupTwoIDs(id1, id2);
          if (groupID != null) {
            alreadyGrouped.put(id1, groupID);
            alreadyGrouped.put(id2, groupID);
          }
        }
      }
    }
  }

  /** soft links: systematic edits and similar edits */
  public void analyzeSoftLinks() {
    for (int i = 0; i < diffHunks.size(); ++i) {
      for (int j = i + 1; j < diffHunks.size(); ++j) {
        DiffHunk diffHunk2 = diffHunks.get(i);
        DiffHunk diffHunk1 = diffHunks.get(j);
        if (diffHunk2.getBaseHunk().getCodeSnippet().size()
                == diffHunk1.getBaseHunk().getCodeSnippet().size()
            && diffHunk2.getCurrentHunk().getCodeSnippet().size()
                == diffHunk1.getCurrentHunk().getCodeSnippet().size()) {
          Double similarity = estimateSimilarity(diffHunk2, diffHunk1);
          boolean success =
              diffHunkGraph.addEdge(
                  findNodeByIndex(diffHunk2.getUniqueIndex()),
                  findNodeByIndex(diffHunk1.getUniqueIndex()),
                  new DiffEdge(generateEdgeID(), DiffEdgeType.SOFT, similarity));
          if (similarity >= threshold) {
            String id1 = diffHunk2.getFileID() + ":" + diffHunk2.getDiffHunkID();
            String id2 = diffHunk1.getFileID() + ":" + diffHunk1.getDiffHunkID();

            String groupID = groupTwoIDs(id1, id2);
            if (groupID != null) {
              alreadyGrouped.put(id1, groupID);
              alreadyGrouped.put(id2, groupID);
            }
          }
        }
      }
    }
  }

  private double estimateSimilarity(DiffHunk diffHunk, DiffHunk diffHunk1) {
    double baseSimi =
        Utils.computeStringSimilarity(
            Utils.convertListToString(diffHunk.getBaseHunk().getCodeSnippet()),
            Utils.convertListToString(diffHunk1.getBaseHunk().getCodeSnippet()));
    double currentSimi =
        Utils.computeStringSimilarity(
            Utils.convertListToString(diffHunk.getCurrentHunk().getCodeSnippet()),
            Utils.convertListToString(diffHunk1.getCurrentHunk().getCodeSnippet()));
    return (double) Math.round((baseSimi + currentSimi) / 2 * 100) / 100;
  }

  /**
   * Group the given two ids: if both visited, merge the two groups; if both not visited, create a
   * new group; if visited, find the one and add the other
   *
   * @param id1
   * @param id2
   */
  private String groupTwoIDs(String id1, String id2) {
    // the group id that finally the two diffhunks are assigned
    String groupID = null;
    if (alreadyGrouped.containsKey(id1) && alreadyGrouped.containsKey(id2)) {
      groupID = alreadyGrouped.get(id1);
      String groupID2 = alreadyGrouped.get(id2);
      if (groupID != groupID2) {
        // merge the later groups into the earlier one
        for (String tmp : generatedGroups.get(groupID2).getDiffHunks()) {
          generatedGroups.get(groupID).addDiffHunk(tmp);
        }
        generatedGroups.remove(id2);
      }
    } else if (alreadyGrouped.containsKey(id1)) {
      groupID = alreadyGrouped.get(id1);
      generatedGroups.get(groupID).addDiffHunk(id2);
    } else if (alreadyGrouped.containsKey(id2)) {
      groupID = alreadyGrouped.get(id2);
      generatedGroups.get(groupID).addDiffHunk(id1);
    } else {
      // both not visited
      Set<String> diffHunksInGroup = new HashSet<>();
      diffHunksInGroup.add(id1);
      diffHunksInGroup.add(id2);
      groupID = "group" + getGeneratedGroups().size();
      Group group = new Group(repoID, repoName, groupID, new ArrayList<>(diffHunksInGroup));
      generatedGroups.put(groupID, group);
    }
    return groupID;
  }

  private static Map<String, List<String>> combineHardLinks(
      Map<String, List<String>> baseHardLinks, Map<String, List<String>> currentHardLinks) {
    Map<String, List<String>> result = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : baseHardLinks.entrySet()) {
      List<String> others = new ArrayList<>();
      for (String s : entry.getValue()) {
        others.add(s);
      }
      if (currentHardLinks.containsKey(entry.getKey())) {
        for (String s : currentHardLinks.get(entry.getKey())) {
          others.add(s);
        }
      }
      result.put(entry.getKey(), others);
    }

    return result;
  }

  private Map<String, List<String>> analyzeDefUse(Graph<Node, Edge> graph) {
    Map<String, List<String>> defUseLinks = new HashMap<>();
    List<Node> hunkNodes =
        graph.vertexSet().stream().filter(node -> node.isInDiffHunk).collect(Collectors.toList());
    for (Node node : hunkNodes) {
      List<String> defHunkNodes = analyzeDef(graph, node, new HashSet<>());
      List<String> useHunkNodes = analyzeUse(graph, node, new HashSet<>());
      // record the links an return
      if (!defHunkNodes.isEmpty() || !useHunkNodes.isEmpty()) {
        if (!defUseLinks.containsKey(node.diffHunkIndex)) {
          defUseLinks.put(node.diffHunkIndex, new ArrayList<>());
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
      DiffNode diffNode = new DiffNode(nodeID++, diffHunk.getUniqueIndex());
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

  private Integer generateNodeID() {
    return this.diffHunkGraph.vertexSet().size() + 1;
  }

  private Integer generateEdgeID() {
    return this.diffHunkGraph.edgeSet().size() + 1;
  }

  public Map<String, Group> getGeneratedGroups() {
    return generatedGroups;
  }

  public Map<String, String> getAlreadyGrouped() {
    return alreadyGrouped;
  }
}
