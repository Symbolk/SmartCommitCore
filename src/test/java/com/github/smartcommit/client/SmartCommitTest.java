package com.github.smartcommit.client;

import com.github.smartcommit.compilation.MavenError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

class SmartCommitTest {

  String repoName = "SmartCommitMaven";
  String repoPath = "/Users/Chuncen/IdeaProjects/SmartCommitMaven";
  String repoID = String.valueOf(repoName.hashCode());
  SmartCommit smartCommit = new SmartCommit(repoID, repoName, repoPath, "~/Downloads");

  @Test
  void testCompileWithMaven() throws Exception {
    smartCommit.analyzeWorkingFiles();
    smartCommit.generateHunkIndexes();
    Map<String, String> id2MavenOut = new HashMap<>();
    id2MavenOut = smartCommit.compileWithMaven();
    assertNotNull(id2MavenOut);
  }

  @Test
  void testParseMavenErrors() {
    String compileOut =
            "[ERROR] /Users/Chuncen/IdeaProjects/SmartCommitMaven/src/main/java/com/github/smartcommit/util/JDTParser.java:[3,36] package com.github.smartcommit.model does not exist\n"
                    + "[ERROR] /Users/Chuncen/IdeaProjects/SmartCommitMaven/src/main/java/com/github/smartcommit/util/JDTParser.java:[5,24] package org.apache.log4j does not exist\n"
                    + "[ERROR] /Users/Chuncen/IdeaProjects/SmartCommitMaven/src/main/java/com/github/smartcommit/util/JDTParser.java:[13,24] cannot find symbol\n"
                    + "  symbol:   class Logger\n"
                    + "  location: class com.github.smartcommit.util.JDTParser\n"
                    + "[ERROR] /Users/Chuncen/IdeaProjects/SmartCommitMaven/src/main/java/com/github/smartcommit/util/JDTParser.java:[28,64] cannot find symbol\n"
                    + "  symbol:   class DiffFile\n"
                    + "  location: class com.github.smartcommit.util.JDTParser";
    List<MavenError> errors = new ArrayList<>();
    errors = smartCommit.parseMavenErrors(compileOut);
    assertEquals("Logger", errors.get(0).getSymbol());
  }

  public static void main(String[] args) throws Exception {

    String repoName = "SmartCommitMaven";
    String repoPath = "/Users/Chuncen/IdeaProjects/SmartCommitMaven";
    String repoID = String.valueOf(repoName.hashCode());
    SmartCommit smartCommit = new SmartCommit(repoID, repoName, repoPath, "~/Downloads");

    smartCommit.analyzeWorkingTree();
    smartCommit.generateHunkIndexes();

    Map<String, String> id2MavenOut = smartCommit.compileWithMaven();
    for (Map.Entry<String, String> entry : id2MavenOut.entrySet()) {
      String groupId = entry.getKey();
      String mavenOut = entry.getValue();
      // find errors
      List<MavenError> errors = smartCommit.parseMavenErrors(mavenOut);
      // minimize errors
      errors = smartCommit.fixMavenErrors(groupId, errors);
      // print errors
      System.out.println("GroupID: " + groupId);
      if (errors.isEmpty()) System.out.println("No error");
      else System.out.println(errors.toString());
    }
  }
}
