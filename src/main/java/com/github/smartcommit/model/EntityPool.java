package com.github.smartcommit.model;

import com.github.smartcommit.model.entity.*;

import java.util.HashMap;
import java.util.Map;

public class EntityPool {
  private String srcDir;
  public Map<String, ClassInfo> classInfoMap;
  public Map<String, InterfaceInfo> interfaceInfoMap;
  public Map<String, EnumInfo> enumInfoMap;
  public Map<String, EnumConstantInfo> enumConstantInfoMap;
  public Map<String, AnnotationInfo> annotationInfoMap;
  public Map<String, MethodInfo> methodInfoMap;
  public Map<String, FieldInfo> fieldInfoMap;
  public Map<String, InitializerInfo> initBlockInfoMap; // initializer blocks
  public Map<String, HunkInfo> hunkInfoMap;
  // fileIndex : importedType : hunkInfo
  public Map<Integer, Map<String, HunkInfo>> importInfoMap;

  public EntityPool(String srcDir) {
    this.srcDir = srcDir;
    classInfoMap = new HashMap<>();
    interfaceInfoMap = new HashMap<>();
    enumInfoMap = new HashMap<>();
    enumConstantInfoMap = new HashMap<>();
    annotationInfoMap = new HashMap<>();
    methodInfoMap = new HashMap<>();
    fieldInfoMap = new HashMap<>();
    initBlockInfoMap = new HashMap<>();
    hunkInfoMap = new HashMap<>();
    importInfoMap = new HashMap<>();
  }
}
