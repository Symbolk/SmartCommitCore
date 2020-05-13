package com.github.smartcommit.model.entity;

/** More like a method with name "static" */
public class InitializerInfo extends DeclarationInfo {
  public boolean isStatic = false;
  public String comment = "";
  public String body = "";
  public String belongTo = "";

  public String uniqueName() {
    return belongTo + ":INIT";
  }
}
