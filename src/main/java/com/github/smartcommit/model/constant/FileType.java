package com.github.smartcommit.model.constant;

/** The type of the diff File */
public enum FileType {
  JAVA(".java", "Java"),
  JSON(".json", "Json"),
  JS(".javascript", "JavaScript"),
  PY(".py", "Python"),
  CPP(".cpp", "C++"),
  HPP(".hpp", "C++ Header"),
  C(".c", "C"),
  H(".h", "C Header"),
  MD(".md", "Markdown"),
  TXT(".txt", "Text"),
  HTML(".html", "HTML"),
  XML(".xml", "XML"),
  YML(".yml", "YAML"),
  GRADLE(".gradle", "Gradle"),
  PROP(".properties", "Properties"),

  BIN(".", "Binary"), // binary file
  OTHER(".*", "Other"); // other plain text file

  public String extension;
  public String label;

  FileType(String extension, String label) {
    this.extension = extension;
    this.label = label;
  }
}
