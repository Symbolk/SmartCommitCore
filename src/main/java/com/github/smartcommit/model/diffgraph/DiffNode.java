package com.github.smartcommit.model.diffgraph;

/** Nodes in the DiffViewGraph, which stand for one diff hunk */
public class DiffNode {
  private Integer id;
  private String index; // identifier
  private String uuid; // fileID:diffHunkID

  public DiffNode(Integer id, String index, String uuid) {
    this.id = id;
    this.index = index;
    this.uuid = uuid;
  }

  public Integer getId() {
    return id;
  }

  public String getIndex() {
    return index;
  }

  public String getUuid() {
    return uuid;
  }
}
