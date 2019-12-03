package com.github.smartcommit.model.constant;

/**
 * The type of the diff File
 */
public enum FileType {
  JAVA(".java", "Java"),
  MD(".md", "Markdown"),
  TXT(".txt", "Text"),
  JAR(".jar", "Jar"),
  OTHER(".*", "Other");

  public String extension;
  public String label;

  FileType(String extension, String label) {
    this.extension = extension;
    this.label = label;
  }
}
