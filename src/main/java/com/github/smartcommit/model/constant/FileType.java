package com.github.smartcommit.model.constant;

/** The type of the diff File */
public enum FileType {
  JAVA(".java", "Java"),
  MD(".md", "Markdown"),
  TXT(".txt", "Text"),
  HTML(".html", "HTML"),
  JAR(".jar", "Jar"),
  XML(".xml", "XML"),
  YML(".yml", "YAML"),
  GRADLE(".gradle", "Gradle"),
  PROP(".properties", "Properties"),
  OTHER(".*", "Other");

  public String extension;
  public String label;

  FileType(String extension, String label) {
    this.extension = extension;
    this.label = label;
  }
}
