package com.github.smartcommit.core;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.graph.DiffNode;
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
  private List<DiffFile> diffFiles;
  private List<DiffHunk> diffHunks;

  private Set<String> visited;

  private Graph<Node, Edge> baseGraph;
  private Graph<Node, Edge> currentGraph;
  private Map<String, Group> generatedGroups;
  private Graph<DiffNode, Edge> diffHunkGraph;

  public GroupGenerator(
      String repoID,
      String repoName,
      List<DiffFile> diffFiles,
      List<DiffHunk> diffHunks,
      Graph<Node, Edge> baseGraph,
      Graph<Node, Edge> currentGraph) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.diffFiles = diffFiles;
    this.diffHunks = diffHunks;

    this.visited = new HashSet<>();

    this.baseGraph = baseGraph;
    this.currentGraph = currentGraph;
    this.generatedGroups = new HashMap<>();
    this.diffHunkGraph = initDiffViewGraph(diffHunks);
  }

  /** Put non-java hunks as a whole file in the first group, if there exists */
  public void analyzeNonJavaFiles() {
    List<String> nonJavaDiffHunks = new ArrayList<>();
    for (DiffFile diffFile : diffFiles) {
      if (!diffFile.getFileType().equals(FileType.JAVA)) {
        String diffHunkID = diffFile.getFileID() + ":" + diffFile.getFileID();
        nonJavaDiffHunks.add(diffHunkID);
        visited.add(diffHunkID);
      }
    }
    if (nonJavaDiffHunks.size() > 0) {
      String groupID = "group" + generatedGroups.size();
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
      if (visited.contains(id)) {
        continue;
      } else {
        remainingDiffHunks.add(id);
      }
    }

    String groupID = "group" + generatedGroups.size();
    Group group = new Group(repoID, repoName, groupID, new ArrayList<>(remainingDiffHunks));
    generatedGroups.put(groupID, group);
  }

  public void analyzeHardLinks() {
    Map<String, List<String>> unionHardLinks =
        combineHardLinks(analyzeDefUse(baseGraph), analyzeDefUse(currentGraph));
    for (Map.Entry<String, List<String>> entry : unionHardLinks.entrySet()) {
      Set<String> diffHunksInGroup = new HashSet<>();
      String diffHunkID = getDiffHunkIDFromIndex(diffFiles, entry.getKey());
      diffHunksInGroup.add(diffHunkID);
      visited.add(diffHunkID);

      for (String target : entry.getValue()) {
        diffHunkID = getDiffHunkIDFromIndex(diffFiles, target);
        diffHunksInGroup.add(diffHunkID);
        visited.add(diffHunkID);
      }
      String groupID = "group" + generatedGroups.size();
      Group group = new Group(repoID, repoName, groupID, new ArrayList<>(diffHunksInGroup));
      generatedGroups.put(groupID, group);
    }
  }

  /** soft links: systematic edits and similar edits */
  public void analyzeSoftLinks() {
    for (DiffHunk diffHunk : diffHunks) {
      for (DiffHunk diffHunk1 : diffHunks) {
        if (diffHunk1 != diffHunk
            && diffHunk.getBaseHunk().getCodeSnippet().size()
                == diffHunk1.getBaseHunk().getCodeSnippet().size()
            && diffHunk.getCurrentHunk().getCodeSnippet().size()
                == diffHunk1.getCurrentHunk().getCodeSnippet().size()) {
          addDiffHunkIntoGroup(repoID, repoName, generatedGroups, diffHunk, diffHunk1);
        }
      }
    }
  }

  private void addDiffHunkIntoGroup(
      String repoID,
      String repoName,
      Map<String, Group> groups,
      DiffHunk diffHunk,
      DiffHunk diffHunk1) {
    String id = diffHunk.getFileID() + ":" + diffHunk.getDiffHunkID();
    String id1 = diffHunk1.getFileID() + ":" + diffHunk1.getDiffHunkID();

    boolean added = false;
    for (Map.Entry<String, Group> entry : groups.entrySet()) {
      List<String> existingDiffHunks = entry.getValue().getDiffHunks();
      if (existingDiffHunks.contains(id) || existingDiffHunks.contains(id1)) {
        entry.getValue().addDiffHunk(id);
        entry.getValue().addDiffHunk(id1);
        added = true;
      }
    }
    if (!added) {
      Set<String> diffHunksInGroup = new HashSet<>();
      diffHunksInGroup.add(id);
      diffHunksInGroup.add(id1);
      visited.add(id);
      visited.add(id1);
      String groupID = "group" + groups.values().size();
      Group group = new Group(repoID, repoName, groupID, new ArrayList<>(diffHunksInGroup));
      groups.put(groupID, group);
    }
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
      List<String> defHunkNodes = analyzeDef(graph, node);
      List<String> useHunkNodes = analyzeUse(graph, node);
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

  private List<String> analyzeUse(Graph<Node, Edge> graph, Node node) {
    List<String> res = new ArrayList<>();
    Set<Edge> outEdges =
        graph.outgoingEdgesOf(node).stream()
            .filter(edge -> !edge.getType().isStructural())
            .collect(Collectors.toSet());
    if (outEdges.isEmpty()) {
      return res;
    }
    for (Edge edge : outEdges) {
      Node tgtNode = graph.getEdgeTarget(edge);
      if (tgtNode != node) {

        if (tgtNode.isInDiffHunk) {
          res.add(tgtNode.diffHunkIndex);
        }
        res.addAll(analyzeUse(graph, tgtNode));
      }
    }
    return res;
  }

  private List<String> analyzeDef(Graph<Node, Edge> graph, Node node) {
    List<String> res = new ArrayList<>();
    Set<Edge> inEdges =
        graph.incomingEdgesOf(node).stream()
            .filter(edge -> edge.getType().isStructural())
            .collect(Collectors.toSet());
    if (inEdges.isEmpty()) {
      return res;
    }
    for (Edge edge : inEdges) {
      Node srcNode = graph.getEdgeSource(edge);
      if (srcNode != node) {
        if (srcNode.isInDiffHunk) {
          res.add(srcNode.diffHunkIndex);
        }
        res.addAll(analyzeDef(graph, srcNode));
      }
    }
    return res;
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
    }
  }

  private Graph<DiffNode, Edge> initDiffViewGraph(List<DiffHunk> diffHunks) {
    Graph<DiffNode, Edge> diffViewGraph =
        GraphTypeBuilder.<DiffNode, Edge>directed()
            .allowingMultipleEdges(true)
            .allowingSelfLoops(false)
            .edgeClass(Edge.class)
            .weighted(true)
            .buildGraph();
    for (DiffHunk diffHunk : diffHunks) {
      DiffNode diffNode =
          new DiffNode(diffHunk.getFileIndex().toString(), diffHunk.getIndex().toString());
      diffViewGraph.addVertex(diffNode);
    }
    return diffViewGraph;
  }
}
