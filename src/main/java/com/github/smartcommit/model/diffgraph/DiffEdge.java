package com.github.smartcommit.model.diffgraph;

public class DiffEdge {
  private Integer id;
  private DiffEdgeType type;
  private Double weight;

  public DiffEdge(Integer id, DiffEdgeType type, Double weight) {
    this.id = id;
    this.type = type;
    this.weight = weight;
  }

  public Integer getId() {
    return id;
  }

  public DiffEdgeType getType() {
    return type;
  }

  public Double getWeight() {
    return weight;
  }
}
