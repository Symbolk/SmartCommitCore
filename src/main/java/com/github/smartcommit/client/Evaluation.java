package com.github.smartcommit.client;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Evaluation {
  public static void main(String[] args) {
    BasicConfigurator.configure();
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);

    String repoDir = "/Users/symbolk/coding/data/";
    String tempDir = "/Users/symbolk/coding/smart_commit/";

    String repoName = "jruby";
    String repoPath = repoDir + repoName;
    String atomicCsvPath = repoDir + "csv/jruby_test.csv";
    String compositeCsvPath = repoDir + "csv/jruby_atomic_fixes.csv";
    //    runRQ1(repoPath, csvPath);
    runRQ2(repoName, repoPath, compositeCsvPath, tempDir + repoName);
  }

  /**
   * RQ1: grouping precision and recall
   *
   * @param repoPath
   * @param csvPath
   */
  private static void runRQ1(String repoPath, String csvPath) {
    // read csv file for atomic commits (that links to one ticket id)
    // (from the latest to the oldest, sample 100 for each project)
    List<String[]> lines = Utils.readCSV(csvPath, ",");
    // combine consecutive 5 commits changesets
    GitService gitService = new GitServiceCGit();

    for (int i = 0; i < lines.size(); i += 5) {
      Map<String, List<DiffHunk>> map = new HashMap<>();
      for (int j = 0; j < 5; j++) {
        if (i + j < lines.size()) {
          String commitID = lines.get(i + j)[0];
          if (!commitID.isEmpty()) {
            System.out.println(commitID);
            // get diff hunks and save in map
            List<DiffFile> diffFiles = gitService.getChangedFilesAtCommit(repoPath, commitID);
            List<DiffHunk> diffHunks =
                gitService.getDiffHunksAtCommit(repoPath, commitID, diffFiles);
            // filter only java diff hunks
            //                     .stream().filter(diffHunk ->
            // diffHunk.getFileType().equals(FileType.JAVA))
            //                    .collect(Collectors.toList());
            map.put(commitID, diffHunks);
          }
        }
      }
      // union diff hunks in one change set
      List<DiffHunk> changeSet = new ArrayList<>();
      // filter: no overlapping diff hunks
      System.out.println(map);
      // collect diff file src

      // call smartCommit.analyze(diffFiles, allDiffHunks, dataPaths) to get grouping results
      // compare with the original map
      // compute the p/r for this one
    }

    // compare with the original (ground truth)

    // compute the precision and recall
  }

  /**
   * RQ2: acceptance rate/comment from the original author
   *
   * @param repoPath
   * @param csvPath
   */
  private static void runRQ2(String repoName, String repoPath, String csvPath, String tempDir) {
    // read tangled commit list from csv file
    List<String[]> lines = Utils.readCSV(csvPath, ",");
    // combine consecutive 5 commits changesets
    GitService gitService = new GitServiceCGit();
    SmartCommit smartCommit =
        new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);

    // analyze each commit
    try {
      MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
      MongoClient mongoClient = new MongoClient(connectionString);
      MongoDatabase db = mongoClient.getDatabase("smartcommit");
      MongoCollection<Document> col = db.getCollection(repoName);
      // drop the last testing results
      col.drop();

      for (int i = 0; i < lines.size(); i++) {
        String commitID = lines.get(i)[0];
        if (!commitID.isEmpty()) {
          // get committer name and email
          String committerName = gitService.getCommitterName(repoPath, commitID);
          String committerEmail = gitService.getCommitterEmail(repoPath, commitID);
          System.out.println(commitID);
          // call analyze commit and generate groups
          Map<String, Group> results = smartCommit.analyzeCommit(commitID);
          // save results in mongodb (for committers to review online)
          System.out.println(results);
          Document doc = new Document("repo_name", repoName).append("commit_id", commitID);
          doc.append("committer_name", committerName);
          doc.append("committer_email", committerEmail);
          col.insertOne(doc);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void runRQ3() {
    // RQ3: user study in an actual project:
    // acceptance rate/adjust steps

  }
}
