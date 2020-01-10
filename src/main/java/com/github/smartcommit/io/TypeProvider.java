package com.github.smartcommit.io;

import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import org.jgrapht.io.Attribute;
import org.jgrapht.io.AttributeType;
import org.jgrapht.io.ComponentAttributeProvider;

import java.util.HashMap;
import java.util.Map;

public class TypeProvider implements ComponentAttributeProvider {
  @Override
  public Map<String, Attribute> getComponentAttributes(Object component) {
    if (component instanceof Node) {
      Node node = (Node) component;
      Map<String, Attribute> map = new HashMap<>();
      map.put("type", new NodeTypeAttribute(node));
      map.put("color", new NodeColorAttribute(node));
      map.put("shape", new NodeShapeAttribute(node));
      return map;
    }
    if (component instanceof Edge) {
      Edge edge = (Edge) component;
      Map<String, Attribute> map = new HashMap<>();
      map.put("type", new EdgeTypeAttribute(edge));
      map.put("color", new EdgeColorAttribute(edge));
      map.put("style", new EdgeStyleAttribute(edge));
      return map;
    }
    return null;
  }
}

class NodeShapeAttribute implements Attribute {
  private Node node;

  public NodeShapeAttribute(Node node) {
    this.node = node;
  }

  @Override
  public String getValue() {
    switch (node.getType()) {
      case PACKAGE:
        return "folder";
      case CLASS:
        return "component";
      case INTERFACE:
        return "polygon";
      case ENUM:
        return "septagon";
      case ANNOTATION:
        return "cds";
      case METHOD:
        return "ellipse";
      case FIELD:
        return "box";
      case HUNK:
        return "diamond";
      default:
        return "";
    }
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}

class NodeTypeAttribute implements Attribute {
  private Node node;

  public NodeTypeAttribute(Node node) {
    this.node = node;
  }

  @Override
  public String getValue() {
    return node.getType().asString();
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}

class NodeColorAttribute implements Attribute {
  private Node node;

  public NodeColorAttribute(Node node) {
    this.node = node;
  }

  @Override
  public String getValue() {
    return node.isInDiffHunk ? "red" : "black";
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}

class EdgeTypeAttribute implements Attribute {
  private Edge edge;

  public EdgeTypeAttribute(Edge edge) {
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

class EdgeColorAttribute implements Attribute {
  private Edge edge;

  public EdgeColorAttribute(Edge edge) {
    this.edge = edge;
  }

  @Override
  public String getValue() {
    return edge.getType().isStructural() ? "black" : "blue";
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}

class EdgeStyleAttribute implements Attribute {
  private Edge edge;

  public EdgeStyleAttribute(Edge edge) {
    this.edge = edge;
  }

  @Override
  public String getValue() {
    return edge.getType().isStructural() ? "solid" : "dashed";
  }

  @Override
  public AttributeType getType() {
    return AttributeType.STRING;
  }
}
