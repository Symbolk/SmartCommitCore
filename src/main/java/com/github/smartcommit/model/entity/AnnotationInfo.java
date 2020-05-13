package com.github.smartcommit.model.entity;

public class AnnotationInfo extends DeclarationInfo{
  public String name;
  public String fullName;
  public String comment = "";
  public String content = "";

  public String uniqueName() {
    return fullName;
  }
}
