package com.github.smartcommit.model.diffgraph;

import com.github.smartcommit.util.Utils;
import org.apache.commons.lang3.tuple.Pair;

/** Nodes in the DiffViewGraph, which stand for one diff hunk */
public class DiffNode {
  private Integer id;
  private String index; // identifier (fileIndex:diffHunkIndex)
  private Integer fileIndex;
  private Integer diffHunkIndex;
  private String uuid; // fileID:diffHunkID

  public DiffNode(Integer id, String index, String uuid) {
    this.id = id;
    this.index = index;
    this.uuid = uuid;
    Pair<Integer, Integer> indices = Utils.parseIndicesFromString(index);
    this.fileIndex = indices.getLeft();
    this.diffHunkIndex = indices.getRight();
  }

  public Integer getId() {
    return id;
  }

  public String getIndex() {
    return index;
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
