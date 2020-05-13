package com.github.smartcommit.model.entity;

public class EnumInfo extends DeclarationInfo{
  public String name;
  public String fullName;
  public String visibility = "package";
  public String comment = "";
  public String content = "";

  public String uniqueName() {
    return fullName;
  }
}
