package com.github.smartcommit.model.graph;

public enum NodeType {
  COMPILATION_UNIT("compilation_unit"), // logical node to represent file

  // nonterminal
  CLASS("class"),
  INTERFACE("interface"),
  ENUM("enum"),
  ANNOTATION("@interface"), // annotation type declaration
  INNER_CLASS("class"),
  LOCAL_CLASS("local_class"),

  // terminal
  CONSTRUCTOR("constructor"),
  FIELD("field"),
  METHOD("method"),
  ENUM_CONSTANT("enum_constant"),
  INITIALIZER_BLOCK("initializer_block"),
  ANNOTATION_MEMBER("annotation_member");

  String label;

  NodeType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return this.label;
  }
}
