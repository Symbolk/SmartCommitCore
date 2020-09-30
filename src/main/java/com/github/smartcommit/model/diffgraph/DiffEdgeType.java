package com.github.smartcommit.model.diffgraph;

public enum DiffEdgeType {
  /** hard * */
  DEPEND(true, "dependency", 0),
  /** hard * */

  /** soft * */
  SIMILAR(false, "similar", 1),
  CLOSE(false, "close", 1),
  /** soft * */

  /** pattern * */
  REFACTOR(true, "refactor", 2),
  MOVING(true, "moving", 2),
  /** pattern * */

  /** logical * */
  REFORMAT(true, "reformat", 3),
  TEST(false, "test", 3),
  /** logical * */

  DOC(false, "doc", 4),
  CONFIG(false, "config", 4),
  RESOURCE(false, "resource", 4),
  NONJAVA(false, "non-java", 4),
  OTHERS(false, "others", 4);

  Boolean fixed;
  String label;
  Integer kind;

  DiffEdgeType(Boolean fixed, String label) {
    this.fixed = fixed;
    this.label = label;
  }

  DiffEdgeType(Boolean fixed, String label, Integer kind) {
    this.fixed = fixed;
    this.label = label;
    this.kind = kind;
  }

  public String asString() {
    return this.label;
  }

  public Boolean isConstraint() {
    return this.fixed;
  }

  public Integer getKind() {
    return this.kind;
  }
}
