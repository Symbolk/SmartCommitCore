package com.github.smartcommit.compilation;

/** A structured format of maven compilation error */
public class MavenError {
  private String type;
  private String filePath;
  private Integer line;
  private Integer column;
  private String symbol;
  private String msg;

  public MavenError(
      String type, String filePath, Integer line, Integer column, String symbol, String msg) {
    this.type = type;
    this.filePath = filePath;
    this.line = line;
    this.column = column;
    this.symbol = symbol;
    this.msg = msg;
  }

  public String getType() {
    return type;
  }

  public String getFilePath() {
    return filePath;
  }

  public Integer getLine() {
    return line;
  }

  public Integer getColumn() {
    return column;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getMsg() {
    return msg;
  }

  @Override
  public String toString() {
    return "MavenError{" +
            "type='" + type + '\'' +
            ", filePath='" + filePath + '\'' +
            ", line=" + line +
            ", column=" + column +
            ", symbol='" + symbol + '\'' +
            ", msg='" + msg + '\'' +
            '}';
  }
}
