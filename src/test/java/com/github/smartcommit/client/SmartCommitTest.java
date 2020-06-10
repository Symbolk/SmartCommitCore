package com.github.smartcommit.client;

import com.github.smartcommit.compilation.MavenError;
import com.github.smartcommit.model.Group;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

class SmartCommitTest {
  public static void main(String[] args) throws Exception {

    String repoName = "SmartCommitMaven";
    String repoPath = "/Users/Chuncen/IdeaProjects/SmartCommitMaven";
    String repoID = String.valueOf(repoName.hashCode());
    SmartCommit smartCommit = new SmartCommit(repoID, repoName, repoPath, "~/Downloads");

    Map<String, Group> id2GroupMap = smartCommit.analyzeWorkingTree();
    System.out.println(id2GroupMap.size());
    smartCommit.generateHunkIndexes();

    for (Map.Entry<String, Group> entry : id2GroupMap.entrySet()) {
      String groupId = entry.getKey();
      Group group = entry.getValue();
      System.out.println("GroupID: " + groupId);
      System.out.println("DiffHunkIDs: " + group.getDiffHunkIDs());
      System.out.println("IntentLabel: " + group.getIntentLabel());

      // find errors
      List<MavenError> errors = smartCommit.compileWith("mvn", groupId);
      // minimize errors
      errors = smartCommit.fix(groupId, errors);
      // print errors
      if (errors.isEmpty()) System.out.println("No Error");
      else System.out.println(errors.toString());

    }
  }
}
