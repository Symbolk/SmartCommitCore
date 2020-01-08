package com.github.smartcommit.io;

import com.github.smartcommit.model.diffgraph.DiffEdge;
import com.github.smartcommit.model.diffgraph.DiffNode;
import org.jgrapht.io.Attribute;
import org.jgrapht.io.AttributeType;
import org.jgrapht.io.ComponentAttributeProvider;

import java.util.HashMap;
import java.util.Map;

public class DiffTypeProvider implements ComponentAttributeProvider {
  @Override
  public Map<String, Attribute> getComponentAttributes(Object component) {
    if (component instanceof DiffNode) {
      DiffNode node = (DiffNode) component;
      Map<String, Attribute> map = new HashMap<>();
      map.put("color", new DiffNodeColorAttribute(node));
      map.put("shape", new DiffNodeShapeAttribute(node));
      return map;
    }
    if (component instanceof DiffEdge) {
      DiffEdge edge = (DiffEdge) component;
      Map<String, Attribute> map = new HashMap<>();
      map.put("type", new DiffEdgeTypeAttribute(edge));
      map.put("color", new DiffEdgeColorAttribute(edge));
      map.put("style", new DiffEdgeStyleAttribute(edge));
      return map;
    }
    return null;
  }
}

class DiffNodeShapeAttribute implements Attribute {
  private DiffNode node;

  public DiffNodeShapeAttribute(DiffNode node) {
    this.node = node;
  }

  @Override
  public String getValue() {
    return "record";
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}

class DiffNodeColorAttribute implements Attribute {
  private DiffNode node;

  public DiffNodeColorAttribute(DiffNode node) {
    this.node = node;
  }

  @Override
  public String getValue() {
    return "blue";
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}

class DiffEdgeTypeAttribute implements Attribute {
  private DiffEdge edge;

  public DiffEdgeTypeAttribute(DiffEdge edge) {
    this.edge = edge;
  }

  @Override
  public String getValue() {
    return edge.getType().asString();
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}

class DiffEdgeColorAttribute implements Attribute {
  private DiffEdge edge;

  public DiffEdgeColorAttribute(DiffEdge edge) {
    this.edge = edge;
  }

  @Override
  public String getValue() {
    return edge.getType().isConstraint() ? "red" : "black";
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}

class DiffEdgeStyleAttribute implements Attribute {
  private DiffEdge edge;

  public DiffEdgeStyleAttribute(DiffEdge edge) {
    this.edge = edge;
  }

  @Override
  public String getValue() {
    return edge.getType().isConstraint() ? "solid" : "dashed";
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}
