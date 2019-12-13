package com.github.smartcommit.model;

import com.github.smartcommit.model.entity.ClassInfo;
import com.github.smartcommit.model.entity.FieldInfo;
import com.github.smartcommit.model.entity.MethodInfo;

import java.util.HashMap;
import java.util.Map;

public class EntityPool {
  private String srcDir;
  public Map<String, ClassInfo> classInfoMap;
  //    public Map<String, InterfaceInfo> interfaceInfoMap;
  public Map<String, MethodInfo> methodInfoMap;
  public Map<String, FieldInfo> fieldInfoMap;

  public EntityPool(String srcDir) {
    this.srcDir = srcDir;
    classInfoMap = new HashMap<>();
    //        interfaceInfoMap = new HashMap<>();
    methodInfoMap = new HashMap<>();
    fieldInfoMap = new HashMap<>();
  }
}
