package com.github.smartcommit.client;

import com.github.smartcommit.model.Group;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;

import java.util.Map;

public class Main {
  public static void main(String[] args) {
    // use basic configuration when packaging
    BasicConfigurator.configure();
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);

    String COMMIT_ID = "fced40b";
    try {
      SmartCommit smartCommit =
          new SmartCommit(Config.REPO_ID, Config.REPO_NAME, Config.REPO_PATH, Config.TEMP_DIR);
      smartCommit.setDetectRefactorings(true);
      smartCommit.setProcessNonJavaChanges(false);
      smartCommit.setWeightThreshold(Config.WEIGHT_THRESHOLD);
      smartCommit.setMinSimilarity(Config.MIN_SIMILARITY);
      smartCommit.setMaxDistance(Config.MAX_DISTANCE);
      //      Map<String, Group> groups = smartCommit.analyzeWorkingTree();
      Map<String, Group> groups = smartCommit.analyzeCommit(COMMIT_ID);
      if (groups != null && !groups.isEmpty()) {
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
          Group group = entry.getValue();
          System.out.println(entry.getKey());
          System.out.println(group.toString());
        }

      } else {
        System.out.println("There is no changes.");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
