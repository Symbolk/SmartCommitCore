package com.github.smartcommit.model.graph;

public enum EdgeType {
  /** file&folder level edges * */
  CONTAIN("contains"), // physical relation
  IMPORT("imports"),
  EXTEND("extends"),
  IMPLEMENT("implements"),
  /** inside-file edges * */
  // define field/terminal/constructor/inner type/constant
  DEFINE("defines"),
  /** across-node edges * */
  // inter-field/terminal edges
  ACCESS("access_field"),
  //  READ("reads field"),
  //  WRITE("writes field"),
  // call terminal/constructor
  CALL("calls method"),
  // declare/initialize object
  DECLARE("declares object"),
  INITIALIZE("initializes object");

  String label;

  EdgeType(String label) {
    this.label = label;
  }

  public String asString() {
    return this.label;
  }
}
