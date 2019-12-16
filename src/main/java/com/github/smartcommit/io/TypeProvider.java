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
      map.put("shape", new NodeShapeAttribute(node));
      return map;
    }
    if (component instanceof Edge) {
      Edge edge = (Edge) component;
      Map<String, Attribute> map = new HashMap<>();
      map.put("type", new EdgeAttribute(edge));
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
//        return "circle";
        return "component";
      case METHOD:
        return "ellipse";
      case FIELD:
        return "box";
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

class EdgeAttribute implements Attribute {
  private Edge edge;

  public EdgeAttribute(Edge edge) {
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
