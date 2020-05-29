package com.github.smartcommit.model.entity;

import java.util.Set;

public class FieldInfo extends DeclarationInfo {

  public String name;
  public String belongTo;
  public String typeString;
  public Set<String> types;
  public String visibility;
  public boolean isStatic;
  public boolean isFinal;
  public String comment = "";

  public String uniqueName() {
    return belongTo + ":" + name;
  }
}
