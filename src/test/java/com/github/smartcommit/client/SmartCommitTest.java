package com.github.smartcommit.client;

import com.github.smartcommit.compilation.MavenError;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

class SmartCommitTest {

  public static void main(String[] args) throws Exception {

    String repoName = "SmartCommitMaven";
    String repoPath = "/Users/Chuncen/IdeaProjects/SmartCommitMaven";
    String repoID = String.valueOf(repoName.hashCode());
    SmartCommit smartCommit = new SmartCommit(repoID, repoName, repoPath, "~/Downloads");


    smartCommit.analyzeWorkingTree();

    // smartCommit.buildTrie();

    smartCommit.generateIndexesFromDiffHunk();

    smartCommit.compileWithMaven();
    String groupID = "Compiling Working Tree";
    List<MavenError> mavenErrors = smartCommit.parseMavenErrors(groupID);
    for(MavenError mavenError : mavenErrors) {
      System.out.println(mavenError);
      Pair<Integer, Integer> pair = smartCommit.findIndexPairFromMavenError(mavenError);
      if(pair == null) System.out.println("Not Found");
      else {
        System.out.println("fileIndex " + pair.getLeft());
        System.out.println("index " + pair.getRight());
      }

    }

    /*
    String commands = "mvn compile";
    try {
      StringBuilder builder = new StringBuilder();
      Runtime rt = Runtime.getRuntime();
      Process proc = rt.exec(commands, null, new File(repoPath));
      BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
      BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
      String s = null;
      while ((s = stdInput.readLine()) != null) {
        builder.append(s);
        builder.append("\n");
        //                if (verbose) log(s);
      }
      while ((s = stdError.readLine()) != null) {
        builder.append(s);
        builder.append("\n");
        //                if (verbose) log(s);
      }
      System.out.println(builder.toString());
    } catch (IOException e) {
      e.printStackTrace();
    }

     */

/*

// compile with Maven
    Map<String, String> compileResult = smartCommit.compileWithMaven();
    System.out.println(compileResult.toString());
    // parse Maven Errors
    for (Map.Entry<String, String> entry : compileResult.entrySet()) {
      String groupId = entry.getKey();
      String groupResult = entry.getValue();
      System.out.println(groupResult);
      System.out.println("GroupID: " + groupId);
      System.out.println(smartCommit.parseMavenErrors(groupId).toString());
      break;
    }
     */



    /*
    String compileOut ="[ERROR] /Users/Chuncen/IdeaProjects/SmartCommitMaven/src/main/java/com/github/smartcommit/util/JDTParser.java:[3,36] package com.github.smartcommit.model does not exist\n" +
      "[ERROR] /Users/Chuncen/IdeaProjects/SmartCommitMaven/src/main/java/com/github/smartcommit/util/JDTParser.java:[5,24] package org.apache.log4j does not exist\n" +
      "[ERROR] /Users/Chuncen/IdeaProjects/SmartCommitMaven/src/main/java/com/github/smartcommit/util/JDTParser.java:[13,24] cannot find symbol\n" +
      "  symbol:   class Logger\n" +
      "  location: class com.github.smartcommit.util.JDTParser\n" +
      "[ERROR] /Users/Chuncen/IdeaProjects/SmartCommitMaven/src/main/java/com/github/smartcommit/util/JDTParser.java:[28,64] cannot find symbol\n" +
      "  symbol:   class DiffFile\n" +
      "  location: class com.github.smartcommit.util.JDTParser";
    System.out.println(smartCommit.parseMavenErrors(compileOut).toString());

     */
  }
}
