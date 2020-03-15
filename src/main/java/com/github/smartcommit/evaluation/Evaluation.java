package com.github.smartcommit.evaluation;

import com.github.smartcommit.client.SmartCommit;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.Hunk;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bson.Document;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Evaluation {
  private static final Logger logger = Logger.getLogger(Evaluation.class);

  public static void main(String[] args) {
    BasicConfigurator.configure();
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);

    String repoDir = "/Users/symbolk/coding/data/repos/";
    String resultsDir = "/Users/symbolk/coding/data/results/";
    String tempDir = "/Users/symbolk/coding/data/temp/";

    String repoName = "jruby";
    String repoPath = repoDir + repoName;
    String atomicCsvPath = resultsDir + repoName + "_atomic.csv";
    String compositeCsvPath = resultsDir + repoName + "_composite.csv";
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
    List<String[]> lines = Utils.readCSV(csvPath, "~~");
    // combine consecutive 5 commits changesets
    GitService gitService = new GitServiceCGit();
    SmartCommit smartCommit =
        new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);

    // analyze each commit
    try {
      MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
      MongoClient mongoClient = new MongoClient(connectionString);
      MongoDatabase db = mongoClient.getDatabase("smartcommit");
      MongoCollection<Document> col = db.getCollection("commits");
      // !!! drop the last testing results
      col.drop();

      for (int i = 0; i < lines.size(); i++) {
        if (lines.get(i).length == 2) {
          String commitID = lines.get(i)[0];
          String commitMsg = lines.get(i)[1];
          if (!commitID.isEmpty()) {
            // get committer name and email
            String committerName = gitService.getCommitterName(repoPath, commitID);
            String committerEmail = gitService.getCommitterEmail(repoPath, commitID);
            System.out.println(commitID);
            // call analyze commit and generate groups
            Map<String, Group> results = smartCommit.analyzeCommit(commitID);
            Map<String, DiffHunk> id2DiffHunkMap = smartCommit.getId2DiffHunkMap();
            // save results in mongodb (for committers to review online)
            Document commitDoc = new Document("repo_name", repoName);
            commitDoc.append("commit_id", commitID).append("commit_msg", commitMsg);
            commitDoc
                .append("committer_name", committerName)
                .append("committer_email", committerEmail);
            List<Document> groupDocs = new ArrayList<>();
            for (Map.Entry<String, Group> entry : results.entrySet()) {
              Group group = entry.getValue();
              Document groupDoc = new Document("group_id", group.getGroupID());
              groupDoc.append("group_label", group.getIntentLabel().label);
              List<Document> diffHunkDocs = new ArrayList<>();
              for (String id : group.getDiffHunkIDs()) {
                DiffHunk diffHunk = id2DiffHunkMap.getOrDefault(id.split(":")[1], null);
                if (diffHunk != null) {
                  Document diffHunkDoc = new Document();
                  diffHunkDoc.append("file_index", diffHunk.getFileIndex());
                  diffHunkDoc.append("file_type", diffHunk.getFileType().label);
                  diffHunkDoc.append("diff_hunk_index", diffHunk.getIndex());
                  diffHunkDoc.append("change_type", diffHunk.getChangeType().label);
                  diffHunkDoc.append("description", diffHunk.getDescription());
                  diffHunkDoc.append(
                      "a_hunk",
                      convertHunkToDoc(
                          diffHunk.getBaseHunk(),
                          tempDir
                              + File.separator
                              + commitID
                              + File.separator
                              + Version.BASE.asString()
                              + File.separator));
                  diffHunkDoc.append(
                      "b_hunk",
                      convertHunkToDoc(
                          diffHunk.getCurrentHunk(),
                          tempDir
                              + File.separator
                              + commitID
                              + File.separator
                              + Version.CURRENT.asString()
                              + File.separator));
                  diffHunkDocs.add(diffHunkDoc);
                }
                groupDoc.append("diff_hunks", diffHunkDocs);
              }
              groupDocs.add(groupDoc);
            }
            commitDoc.append("groups", groupDocs);
            col.insertOne(commitDoc);
          }
        } else {
          logger.error("Invalid line: " + lines.get(i));
        }
      }
      mongoClient.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Convert a given diff hunk to a mongo doc
   *
   * @param hunk
   * @param dir
   * @return
   */
  private static Document convertHunkToDoc(Hunk hunk, String dir) {
    Document hunkDoc = new Document();
    hunkDoc.append("git_path", hunk.getRelativeFilePath());
    if (hunk.getRelativeFilePath().equals("/dev/null")) {
      hunkDoc.append("file_path", hunk.getRelativeFilePath());
    } else {
      hunkDoc.append("file_path", dir + hunk.getRelativeFilePath());
    }
    hunkDoc.append("start_line", hunk.getStartLine());
    hunkDoc.append("end_line", hunk.getEndLine());
    hunkDoc.append("content", Utils.convertListLinesToString(hunk.getCodeSnippet()));
    hunkDoc.append("content_type", hunk.getContentType().label);
    return hunkDoc;
  }

  private void runRQ3() {
    // RQ3: user study in an actual project:
    // acceptance rate/adjust steps

  }
}
