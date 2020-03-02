package com.github.smartcommit.intent.model;

public class AstAction {
  private ASTOperation operation;
  private String astNodeType;

  public AstAction(ASTOperation operation, String astNodeType) {
    this.operation = operation;
    this.astNodeType = astNodeType;
  }

  public String getASTNodeType() {
    return astNodeType;
  }

  public ASTOperation getASTOperation(){
    return operation;
  }

}
