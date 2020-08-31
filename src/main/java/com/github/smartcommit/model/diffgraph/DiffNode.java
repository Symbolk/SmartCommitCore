package com.github.smartcommit.model.diffgraph;

import com.github.smartcommit.util.Utils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

/** Nodes in the DiffViewGraph, which stand for one diff hunk */
public class DiffNode {
  private Integer id;
  private String index; // unique identifier (fileIndex:diffHunkIndex)
  private Integer fileIndex; // index of the changed file
  private Integer diffHunkIndex; // index of the diff hunk
  private String uuid; // fileID:diffHunkID

  // parent info (node id in the graph) to estimate the distance
  // in the order of: hunkNodeID, memberNodeID, classNodeID, packageNodeID
  private Map<String, Integer> baseHierarchy;
  private Map<String, Integer> currentHierarchy;

  public DiffNode(Integer id, String index, String uuid) {
    this.id = id;
    this.index = index;
    this.uuid = uuid;
    Pair<Integer, Integer> indices = Utils.parseIndices(index);
    this.fileIndex = indices.getLeft();
    this.diffHunkIndex = indices.getRight();
    this.baseHierarchy = new HashMap<>();
    this.currentHierarchy = new HashMap<>();
  }

  public Integer getId() {
    return id;
  }

  public String getIndex() {
    return index;
  }

  public Map<String, Integer> getBaseHierarchy() {
    return baseHierarchy;
  }

  public void setBaseHierarchy(Map<String, Integer> baseHierarchy) {
    this.baseHierarchy = baseHierarchy;
  }

  public Map<String, Integer> getCurrentHierarchy() {
    return currentHierarchy;
  }

  public void setCurrentHierarchy(Map<String, Integer> currentHierarchy) {
    this.currentHierarchy = currentHierarchy;
  }

  public Integer getFileIndex() {
    return fileIndex;
  }

  public Integer getDiffHunkIndex() {
    return diffHunkIndex;
  }

  public String getUUID() {
    return uuid;
  }
}
