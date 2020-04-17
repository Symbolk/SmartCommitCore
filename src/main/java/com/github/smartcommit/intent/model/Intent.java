package com.github.smartcommit.intent.model;

public enum Intent {
  TEST("test"),
  // TESTS("tests"),
  FIX("fix"),
  // BUGFIX("bugfix"),
  // FIXED("fixed"),
  REFACTOR("refactor"),
  // REFACTORING("refactoring"),
  CHORE("chore"),
  FEAT("feat"),
  // FEATURE("feature"),
  STYLE("style"),
  BUILD("build"),
  PERF("perf"),
  // PREFS("prefs"),
  // PERFORMANCE("performance"),
  OPTIMIZE("optimize"),
  IMPROVE("improve"),
  DOC("doc"),
  // DOCS("docs"),
  REVERT("revert"),
  VERSION("version");
  /*
  FEA("feat"), // feat/feature/Feature
  FIX("fix"), // fix/bugfix/fixed
  DOC("doc"), // docs/doc
  RFM("reformat"),
  RFT("refactor"), // refactor/refactoring
  OPT("optimiz"), // perf/perfs/performance/improvement
  TST("test"), // test/tests
  CHR("chore"), // chore
  FIL("file"),
  RVT("ver"), // revert/version
  STY("sytle"), // style
  BUD("build"); // build
   */

  public String label;

  Intent(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public Intent get0() {
    if (this.equals(Intent.OPTIMIZE)) return Intent.PERF;
    else if (this.equals(Intent.IMPROVE)) return Intent.PERF;
    else if (this.equals(Intent.VERSION)) return Intent.REVERT;
    else return this;
  }
}
