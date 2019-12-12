package com.github.smartcommit.model.graph;

public class Edge {
  private Integer id;
  private EdgeType type;
  //    private Integer frequency;

  public Edge(Integer id, EdgeType type) {
    this.id = id;
    this.type = type;
  }
}
