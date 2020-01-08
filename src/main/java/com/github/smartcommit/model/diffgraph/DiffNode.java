package com.github.smartcommit.model.diffgraph;

/** Nodes in the DiffViewGraph, which stand for one diff hunk */
public class DiffNode {
  private Integer id;
  private String index; // identifier

  public DiffNode(Integer id, String index) {
    this.id = id;
    this.index = index;
  }

  public Integer getId() {
    return id;
  }

  public String getIndex() {
    return index;
  }
}
