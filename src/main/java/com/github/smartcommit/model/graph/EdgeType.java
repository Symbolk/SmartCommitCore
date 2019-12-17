package com.github.smartcommit.model.graph;

public enum EdgeType {
  /** file&folder level edges * */
  CONTAIN(true, "contains"), // physical relation
  IMPORT(false, "imports"),
  EXTEND(false, "extends"),
  IMPLEMENT(false, "implements"),
  /** inside-file edges * */
  // define field/terminal/constructor/inner type/constant
  DEFINE(true, "defines"),
  /** across-node edges * */
  // inter-field/terminal edges
  ACCESS(false, "access_field"),
  //  READ("reads field"),
  //  WRITE("writes field"),
  // call terminal/constructor
  CALL(false, "calls method"),
  // declare/initialize object
  DECLARE(false, "declares object"),
  INITIALIZE(false, "initializes object");

  Boolean isStructural;
  String label;

  EdgeType(Boolean isStructural, String label) {
    this.isStructural = isStructural;
    this.label = label;
  }

  public String asString() {
    return this.label;
  }

  public Boolean isStructural() {
    return this.isStructural;
  }
}
