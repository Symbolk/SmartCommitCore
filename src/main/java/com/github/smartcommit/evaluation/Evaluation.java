package com.github.smartcommit.evaluation;

import com.github.smartcommit.client.SmartCommit;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.model.*;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.constant.GroupLabel;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import com.google.common.base.Stopwatch;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jgrapht.alg.matching.MaximumWeightBipartiteMatching;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class Evaluation {
  private static final Logger logger = Logger.getLogger(Evaluation.class);

  public static void main(String[] args) {
    BasicConfigurator.configure();
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);

    String repoDir = "/Users/symbolk/coding/data/repos/";
    String tempDir = "/Users/symbolk/coding/data/temp/";

    // per project and avg
    String repoName = "jruby";
    String repoPath = repoDir + repoName;
    runRQ1(repoName, repoPath, tempDir + "/test/" + repoName, 2);
    //        debugBatch(repoName, repoPath, tempDir + "/test/debug", "65e4dc4_790994f_2be74da");
    //            runRQ2(repoName, repoPath, tempDir + "/RQ2/" + repoName);
  }

  /**
   * RQ1: grouping precision and recall
   *
   * @param repoPath
   */
  private static void runRQ1(String repoName, String repoPath, String outputDir, int step) {
    System.out.println("[RQ1] Repo: " + repoName + " Step: " + step);
    String resultsCSV =
        "/Users/symbolk/coding/dev/visualization/raw2/" + repoName + "_" + step + "_sc.csv";
    String baseline1CSV =
        "/Users/symbolk/coding/dev/visualization/raw2/" + repoName + "_" + step + "_all.csv";
    String baseline2CSV =
        "/Users/symbolk/coding/dev/visualization/raw2/" + repoName + "_" + step + "_file.csv";
    Utils.writeStringToFile(
        "batch,#diff_hunks,#LOC,#reassign,#reorder,accuracy,#operations,runtime"
            + System.lineSeparator(),
        resultsCSV);
    Utils.writeStringToFile(
        "batch,#diff_hunks,#LOC,#reassign,#reorder,accuracy,#operations,runtime"
            + System.lineSeparator(),
        baseline1CSV);
    Utils.writeStringToFile(
        "batch,#diff_hunks,#LOC,#reassign,#reorder,accuracy,#operations,runtime"
            + System.lineSeparator(),
        baseline2CSV);
    String tempDir = outputDir + File.separator + step;
    Utils.clearDir(tempDir);

    // read samples from db
    // mongod --dbpath /Users/symbolk/database/mongo
    MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
    MongoClient mongoClient = new MongoClient(connectionString);
    MongoDatabase db = mongoClient.getDatabase("atomic");
    MongoCollection<Document> col = db.getCollection(repoName);

    // get atomic commitsByEmail list from mongodb, group by authors
    // use the hash function to simulate random
    Map<String, List<String>> commitsByEmail = new HashMap<>();
    try (MongoCursor<Document> cursor = col.find().iterator()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        String email = (String) doc.get("committer_email");
        String commitID = (String) doc.get("commit_id");
        if (commitsByEmail.containsKey(email)) {
          commitsByEmail.get(email).add(commitID);
        } else {
          List<String> ids = new ArrayList<>();
          ids.add(commitID);
          commitsByEmail.put(email, ids);
        }
      }
    }
    mongoClient.close();

    // filter authors whose commits are less than step
    Map<String, List<String>> commitsByEmailAboveStep =
        commitsByEmail.entrySet().stream()
            .filter(a -> a.getValue().size() >= step)
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

    SmartCommit smartCommit =
        new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);
    smartCommit.setDetectRefactorings(true);
    smartCommit.setProcessNonJavaChanges(false);
    //        smartCommit.setDistanceThreshold(2);
    //    smartCommit.setSimilarityThreshold(0.8);
    Stopwatch stopwatch = Stopwatch.createStarted();

    // randomly sample 100 composite commits for each size
    // combine consecutive 2/3/5 commitsByEmail into a change-set
    List<Double> resultAccs = new ArrayList<>();
    List<Double> baselineAccs1 = new ArrayList<>();
    List<Double> baselineAccs2 = new ArrayList<>();
    int sampleNum = 0;
    for (Map.Entry<String, List<String>> entry : commitsByEmailAboveStep.entrySet()) {
      List<String> commits = entry.getValue();

      outerloop:
      for (int i = 0; i < commits.size(); i += step) {
        if (sampleNum >= 50) {
          break outerloop;
        }
        // ordered map
        Map<String, Set<String>> groundTruth = new LinkedHashMap<>();
        // union diff hunks in one change set, reorder the file index
        List<DiffFile> unionDiffFiles = new ArrayList<>();
        List<DiffHunk> unionDiffHunks = new ArrayList<>();
        Map<String, DiffHunk> unionDiffHunkMap = new HashMap<>();

        // collect data for each commit to be combined
        String resultsDir = tempDir + File.separator + commits.get(i);

        DataCollector dataCollector = new DataCollector(repoName, resultsDir);

        int LOC = 0;
        int j; // num of merged commits
        String dirNameHash = "";
        for (j = 0; j < step; j++) {
          if (i + j < commits.size()) {
            String commitID = commits.get(i + j);
            if (!commitID.isEmpty()) {
              // short hash, git default 7
              if (dirNameHash.isEmpty()) {
                dirNameHash = commitID.substring(0, 7);
              } else {
                dirNameHash = dirNameHash + "_" + commitID.substring(0, 7);
              }

              // get diff hunks and save in groundTruth
              RepoAnalyzer repoAnalyzer =
                  new RepoAnalyzer(String.valueOf(repoName.hashCode()), repoName, repoPath);

              List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(commitID);

              // collect all base and current files in the same folder
              dataCollector.collectDiffFilesWorking(diffFiles);

              // assign new index for diffFile and diffHunk
              int beginIndex = unionDiffFiles.size();

              for (int k = 0; k < diffFiles.size(); ++k) {
                int newIndex = beginIndex + k;
                diffFiles.get(k).setIndex(newIndex);
                for (DiffHunk diffHunk : diffFiles.get(k).getDiffHunks()) {
                  diffHunk.setFileIndex(newIndex);
                  LOC +=
                      (diffHunk.getBaseEndLine() - diffHunk.getBaseStartLine() + 1)
                          + (diffHunk.getCurrentEndLine() - diffHunk.getCurrentStartLine() + 1);
                }
              }
              List<DiffHunk> diffHunks = repoAnalyzer.getDiffHunks();
              //              if (diffHunks.isEmpty() || checkPureNoJavaChanges(diffHunks)) {
              if (diffHunks.isEmpty()) {
                continue;
              }

              unionDiffFiles.addAll(diffFiles);
              unionDiffHunks.addAll(diffHunks);

              unionDiffHunkMap.putAll(repoAnalyzer.getIdToDiffHunkMap());

              groundTruth.put(commitID, repoAnalyzer.getIdToDiffHunkMap().keySet());
            }
          } else {
            break;
          }
        }
        if (j < step
            || unionDiffHunks.size() > step * 100
            || checkPureNoJavaChanges(unionDiffHunks)) {
          // the remaining commits for current committer is not enough
          FileUtils.deleteQuietly(new File(resultsDir));
          continue;
        }

        resultsDir = Utils.renameDir(resultsDir, dirNameHash);
        // dirs that keeps the source code of diff files
        String baseDir = resultsDir + File.separator + Version.BASE.asString() + File.separator;
        String currentDir =
            resultsDir + File.separator + Version.CURRENT.asString() + File.separator;

        System.out.print("[Batch " + sampleNum + ":" + dirNameHash + "]");
        try {
          stopwatch.reset().start();
          Map<String, Group> results =
              smartCommit.analyze(unionDiffFiles, unionDiffHunks, Pair.of(baseDir, currentDir));
          stopwatch.stop();

          long timeCost = stopwatch.elapsed(TimeUnit.MILLISECONDS);
          System.out.println(
              " Time="
                  + timeCost
                  + "ms"
                  + " #diff_hunks="
                  + unionDiffHunks.size()
                  + " #LOC="
                  + LOC);

          // generate "diffs" and "file_ids.json", which keeps diff info
          dataCollector.collectDiffHunks(unionDiffFiles, resultsDir);

          // export for testing
          smartCommit.exportGroupResults(results, resultsDir);
          smartCommit.setId2DiffHunkMap(unionDiffHunkMap);
          Map<String, Group> groundTruthGroups = new HashMap<>();
          int gid = 0;
          for (Map.Entry en1 : groundTruth.entrySet()) {
            Group group =
                new Group(
                    "",
                    repoName,
                    "group" + (gid++),
                    new ArrayList<>((Set<String>) en1.getValue()),
                    GroupLabel.OTHER);
            group.setCommitID(en1.getKey().toString());
            groundTruthGroups.put(group.getGroupID(), group);
          }
          smartCommit.exportGroupDetails(
              groundTruthGroups, resultsDir + File.separator + "manual_details");
          smartCommit.exportGroupDetails(
              results, resultsDir + File.separator + "generated_details");

          // convert the group into map from groupid to diffhunkids
          Map<String, Set<String>> generatedResults = new LinkedHashMap<>();
          for (Map.Entry res : results.entrySet()) {
            Set<String> ids = new HashSet<>();
            for (String s : ((Group) res.getValue()).getDiffHunkIDs()) {
              ids.add(Utils.parseUUIDs(s).getRight());
            }
            generatedResults.put((String) res.getKey(), ids);
          }
          // smartcommit results
          Pair<List<Integer>, Double> resultMetrics = computeMetrics(groundTruth, generatedResults);
          exportMetrics(
              "SmartCommit", resultsCSV, resultMetrics, results.size(), LOC, sampleNum, timeCost);
          resultAccs.add(resultMetrics.getRight());

          // baseline1: all diff hunks in one group
          Map<String, Set<String>> baselineResult1 = new LinkedHashMap<>();
          Set<String> allDiffHunkIDs = unionDiffHunkMap.keySet();
          baselineResult1.put("group0", allDiffHunkIDs);
          Pair<List<Integer>, Double> baselineMetrics1 =
              computeMetrics(groundTruth, baselineResult1);
          exportMetrics(
              "All in one Group",
              baseline1CSV,
              baselineMetrics1,
              baselineResult1.size(),
              LOC,
              sampleNum,
              0L);
          baselineAccs1.add(baselineMetrics1.getRight());

          // baseline2: group according to file
          Map<String, Set<String>> baselineResult2 = new LinkedHashMap<>();
          for (DiffFile diffFile : unionDiffFiles) {
            Set<String> diffHunkIDs =
                diffFile.getDiffHunks().stream()
                    .map(DiffHunk::getDiffHunkID)
                    .collect(Collectors.toSet());
            // use path as the group id
            String groupID =
                diffFile.getBaseRelativePath().isEmpty()
                    ? diffFile.getCurrentRelativePath()
                    : diffFile.getBaseRelativePath();
            if (baselineResult2.containsKey(groupID)) {
              baselineResult2.get(groupID).addAll(diffHunkIDs);
            } else {
              baselineResult2.put(groupID, diffHunkIDs);
            }
          }

          Pair<List<Integer>, Double> baselineMetrics2 =
              computeMetrics(groundTruth, baselineResult2);

          exportMetrics(
              "One file one Group",
              baseline2CSV,
              baselineMetrics2,
              baselineResult2.size(),
              LOC,
              sampleNum,
              0L);
          baselineAccs2.add(baselineMetrics2.getRight());

          sampleNum++;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
          e.printStackTrace();
        }
      }
    }

    System.out.println(
        "SmartCommit: Median Accuracy: " + Utils.formatDouble(getMedian(resultAccs)) + "%");
    System.out.println(
        "All in one Group: Median Accuracy: " + Utils.formatDouble(getMedian(baselineAccs1)) + "%");
    System.out.println(
        "One File One Group: Median Accuracy: "
            + Utils.formatDouble(getMedian(baselineAccs2))
            + "%");
  }

  private static void exportMetrics(
      String title,
      String csvPath,
      Pair<List<Integer>, Double> metrics,
      int groupsNum,
      int LOC,
      int sampleNum,
      long timeCost) {
    int total = metrics.getLeft().get(0);
    int correct = metrics.getLeft().get(1);
    int reassignSteps = total - correct;
    int orderingSteps = metrics.getLeft().get(2);
    double accuracy = metrics.getRight();
    System.out.println(
        title
            + ": \t"
            + "#groups="
            + groupsNum
            + " (#Incorrect/#Total)="
            + reassignSteps
            + "/"
            + total
            + " Reordering="
            + orderingSteps
            + " Accuracy="
            + accuracy
            + "%");

    // save metrics (for visualization)
    Utils.appendStringToFile(
        csvPath,
        sampleNum
            + ","
            + total
            + ","
            + LOC
            + ","
            + reassignSteps
            + ","
            + orderingSteps
            + ","
            + accuracy
            + ","
            + (reassignSteps + orderingSteps)
            + ","
            + timeCost
            + System.lineSeparator());
  }

  /**
   * Check if the diff hunk list only contains NonJava changes
   *
   * @param diffHunks
   * @return true: only contains non java changes; false: else
   */
  private static boolean checkPureNoJavaChanges(List<DiffHunk> diffHunks) {
    Optional<DiffHunk> javaDiffHunk =
        diffHunks.stream()
            .filter(diffHunk -> diffHunk.getFileType().equals(FileType.JAVA))
            .findAny();
    // if not present, return true
    return !javaDiffHunk.isPresent();
  }

  /**
   * Compare with the original commits by uuid to evaluate the grouping generatedResult
   *
   * @param groundTruth
   * @param generatedResult
   * @return
   */
  private static Pair<List<Integer>, Double> computeMetrics(
      Map<String, Set<String>> groundTruth, Map<String, Set<String>> generatedResult) {

    class BipartiteNode {
      public int id;
      public Set<String> data;

      public BipartiteNode(int id, Set<String> data) {
        this.id = id;
        this.data = data;
      }
    }

    // ordered sets
    Set<BipartiteNode> partition1 = new LinkedHashSet<>();
    Set<BipartiteNode> partition2 = new LinkedHashSet<>();
    DefaultUndirectedWeightedGraph<BipartiteNode, DefaultWeightedEdge> bipartite =
        new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);

    int totalChangesNum = 0;

    // pairs of diff hunk ids within the same group in the ground truth, generated result
    Set<Pair<String, String>> groundTruthPairs = new LinkedHashSet();
    Set<Pair<String, String>> resultPairs = new LinkedHashSet();
    // pairs across different groups in ground truth
    Set<Pair<String, String>> crossGroupPairs = new LinkedHashSet();

    // bipartite node id
    int id = 0;

    List<List<String>> groupsInGroundTruth = new ArrayList<>();
    for (Map.Entry entry : groundTruth.entrySet()) {
      BipartiteNode n = new BipartiteNode(id++, (Set<String>) entry.getValue());
      partition1.add(n);
      List<String> temp = new ArrayList<>((Set<String>) entry.getValue());
      groupsInGroundTruth.add(temp);
      totalChangesNum += temp.size();
      for (int i = 0; i < temp.size(); ++i) {
        for (int j = i + 1; j < temp.size(); ++j) {
          groundTruthPairs.add(Pair.of(temp.get(i), temp.get(j)));
        }
      }
    }
    for (Map.Entry entry : generatedResult.entrySet()) {
      Set<String> ids = (Set<String>) entry.getValue();
      BipartiteNode n = new BipartiteNode(id++, ids);
      partition2.add(n);

      List<String> temp = new ArrayList<>(ids);
      for (int i = 0; i < temp.size(); ++i) {
        for (int j = i + 1; j < temp.size(); ++j) {
          resultPairs.add(Pair.of(temp.get(i), temp.get(j)));
        }
      }
    }

    // compute the intersection set num
    for (BipartiteNode n1 : partition1) {
      for (BipartiteNode n2 : partition2) {
        bipartite.addVertex(n1);
        bipartite.addVertex(n2);
        bipartite.addEdge(n1, n2);
        Set<String> intersection = new HashSet<>(n1.data);
        intersection.retainAll(n2.data);
        bipartite.setEdgeWeight(n1, n2, intersection.size());
      }
    }

    MaximumWeightBipartiteMatching matcher =
        new MaximumWeightBipartiteMatching(bipartite, partition1, partition2);
    Set<DefaultWeightedEdge> edges = matcher.getMatching().getEdges();

    int correctChangesNum = 0;
    // Metric1: operation alpha: assignment
    Map<Integer, Integer> idMap = new HashMap<>();
    for (DefaultWeightedEdge edge : edges) {
      correctChangesNum += bipartite.getEdgeWeight(edge);
      idMap.put(bipartite.getEdgeTarget(edge).id, bipartite.getEdgeSource(edge).id);
    }

    // Metric2: operation beta: ordering
    List<Integer> list1 = new ArrayList<>(); // ground truth (natural order)
    List<Integer> list2 = new ArrayList<>(); // generatedResult (mapped)
    for (BipartiteNode node : partition1) {
      list1.add(node.id);
    }
    for (BipartiteNode node : partition2) {
      list2.add(idMap.get(node.id));
    }

    // since the edit allowed here is moving groups, so edit distance need to be divided by 2
    // +A and -A is equivalent to move A
    int reorderingSteps = (int) Math.floor(editDistance(list1, list2) / 2.0);

    // Metric3: Accuracy
    int correctlyGroupedPairs = 0;
    for (Pair<String, String> p1 : groundTruthPairs) {
      for (Pair<String, String> p2 : resultPairs) {
        if ((p1.getLeft().equals(p2.getLeft()) && p1.getRight().equals(p2.getRight()))
            || (p1.getLeft().equals(p2.getRight()) && p1.getRight().equals(p2.getLeft()))) {
          correctlyGroupedPairs += 1;
          continue; // unique in set so find and continue
        }
      }
    }

    for (int i = 0; i < groupsInGroundTruth.size() - 1; ++i) {
      // select two groups
      for (int j = i + 1; j < groupsInGroundTruth.size(); ++j) {
        // select two ids
        List<String> group1 = groupsInGroundTruth.get(i);
        List<String> group2 = groupsInGroundTruth.get(j);
        for (String s1 : group1) {
          for (String s2 : group2) {
            crossGroupPairs.add(Pair.of(s1, s2));
          }
        }
      }
    }
    int correctlySeparatedPairs = crossGroupPairs.size();

    for (Pair<String, String> p1 : resultPairs) {
      for (Pair<String, String> p2 : crossGroupPairs) {
        if ((p1.getLeft().equals(p2.getLeft()) && p1.getRight().equals(p2.getRight()))
            || (p1.getLeft().equals(p2.getRight()) && p1.getRight().equals(p2.getLeft()))) {
          correctlySeparatedPairs -= 1;
        }
      }
    }

    double accuracy =
        Utils.formatDouble(
            (correctlyGroupedPairs + correctlySeparatedPairs)
                * 100
                / ((double) (totalChangesNum * (totalChangesNum - 1) / 2)));

    List<Integer> res = new ArrayList<>();
    res.add(totalChangesNum);
    res.add(correctChangesNum);
    res.add(reorderingSteps);

    return Pair.of(res, accuracy);
  }

  /**
   * RQ2: acceptance rate/comment from the original author
   *
   * @param repoPath
   */
  private static void runRQ2(String repoName, String repoPath, String tempDir) {
    System.out.println("RQ2: " + repoName);

    // read samples from db
    MongoClientURI local = new MongoClientURI("mongodb://localhost:27017");
    // !product env
    MongoClientURI server =
        new MongoClientURI("mongodb://symbol:98eukk5age@47.113.179.146:29107/smartcommit");
    String tempFilesDir = "/root/data/temp/RQ2/" + repoName;
    //         !local test env
    //    MongoClientURI server = new MongoClientURI("mongodb://localhost:27017/smartcommit");
    //    String tempFilesDir = tempDir + File.separator;
    //    Utils.clearDir(tempDir);

    MongoClient localClient = new MongoClient(local);
    MongoClient serverClient = new MongoClient(server);
    GitService gitService = new GitServiceCGit();
    SmartCommit smartCommit =
        new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);
    smartCommit.setDistanceThreshold(0); // default
    smartCommit.setSimilarityThreshold(0.618D); // default
    smartCommit.setDetectRefactorings(true);
    smartCommit.setProcessNonJavaChanges(false);

    int cnt = 1;
    try {
      MongoDatabase samplesDB = localClient.getDatabase("composite");
      MongoCollection<Document> samplesCol = samplesDB.getCollection(repoName);
      MongoDatabase resultsDB = serverClient.getDatabase("smartcommit");

      MongoCollection<Document> contactsCol = resultsDB.getCollection("contacts");
      MongoCollection<Document> resultsCol = resultsDB.getCollection(repoName);

      try (MongoCursor<Document> cursor = samplesCol.find().iterator()) {
        while (cursor.hasNext()) {
          Document sampleDoc = cursor.next();

          String commitID = sampleDoc.get("commit_id").toString();
          String commitMsg = sampleDoc.get("commit_msg").toString();
          String commitTime = sampleDoc.get("commit_time").toString();
          // for too many: time range in 2015~2020
          if (!commitID.isEmpty()) {
            // get committer name and email
            String committerName = gitService.getCommitterName(repoPath, commitID);
            String committerEmail = gitService.getCommitterEmail(repoPath, commitID);
            System.out.println(
                "[" + (cnt++) + "] " + committerEmail + " @ " + commitTime + " : " + commitID);
            // call analyze commit and generate groups

            //            long beginTime = System.currentTimeMillis();
            Map<String, Group> results = smartCommit.analyzeCommit(commitID);
            Map<String, DiffHunk> id2DiffHunkMap = smartCommit.getId2DiffHunkMap();
            // filter only one group & only non-java in prod
            if (results != null && results.entrySet().size() > 1) {
              // update the contact
              Bson condition = Filters.eq("email", committerEmail);
              if (contactsCol.countDocuments(condition) == 0) {
                HashSet<Document> rs = new HashSet<>();
                rs.add(new Document("repo", repoName));
                HashSet<Document> cs = new HashSet<>();
                cs.add(new Document("repo_name", repoName).append("commit_id", commitID));
                contactsCol.insertOne(
                    new Document("email", committerEmail)
                        .append("name", committerName)
                        .append("repos", rs)
                        .append("commits", cs));
              } else {
                contactsCol.updateOne(
                    condition, Updates.addToSet("repos", new Document("repo", repoName)));
                contactsCol.updateOne(
                    condition,
                    Updates.addToSet(
                        "commits",
                        new Document("repo_name", repoName).append("commit_id", commitID)));
              }

              // save results in mongodb (for committers to review online)
              Document resultDoc = new Document("repo_name", repoName);
              resultDoc
                  .append("commit_id", commitID)
                  .append("commit_msg", commitMsg)
                  .append("commit_time", commitTime);
              resultDoc
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
                    diffHunkDoc.append("actions", convertActionsToDocs(diffHunk));
                    diffHunkDoc.append(
                        "a_hunk",
                        convertHunkToDoc(
                            diffHunk.getBaseHunk(),
                            tempFilesDir
                                + File.separator
                                + commitID
                                + File.separator
                                + Version.BASE.asString()
                                + File.separator));
                    diffHunkDoc.append(
                        "b_hunk",
                        convertHunkToDoc(
                            diffHunk.getCurrentHunk(),
                            tempFilesDir
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
              resultDoc.append("groups", groupDocs);
              resultsCol.insertOne(resultDoc);
            }
          }
        }
      }
      serverClient.close();
      localClient.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static List<String> convertActionsToDocs(DiffHunk diffHunk) {
    List<String> actions = new ArrayList<>();
    for (Action action : diffHunk.getRefActions()) {
      actions.add(action.toString());
    }
    for (Action action : diffHunk.getAstActions()) {
      actions.add(action.toString());
    }
    return actions;
  }

  /**
   * Read and compare results collected stored in mongodb
   *
   * @param repoName
   * @param repoPath
   */
  private static void runRQ3(String repoName, String repoPath) {
    // read data from db
    MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
    MongoClient mongoClient = new MongoClient(connectionString);
    MongoDatabase db = mongoClient.getDatabase("smartcommit");
    MongoCollection<Document> col = db.getCollection(repoName);

    // convert manual and generated groups into maps

    BasicDBObject condition =
        new BasicDBObject(
            "repos", new BasicDBObject("$elemMatch", new BasicDBObject("repo", repoName)));
    try (MongoCursor<Document> cursor = col.find(condition).iterator()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        String commitID = (String) doc.get("commit_id");
        List<Document> manualGroups = (List<Document>) doc.get("manual_groups");
        List<Document> generatedGroups = (List<Document>) doc.get("generated_groups");
        Map<String, Set<String>> groundTruth = convertToMap(manualGroups);
        Map<String, Set<String>> results = convertToMap(generatedGroups);

        Pair<List<Integer>, Double> res = computeMetrics(groundTruth, results);
        int total = res.getLeft().get(0);
        int correct = res.getLeft().get(1);
        int orderingSteps = res.getLeft().get(2);
        System.out.println(
            "[Commit "
                + commitID
                + "] (#Incorrect/#Total)= "
                + (total - correct)
                + "/"
                + total
                + " Accuracy="
                + res.getRight()
                + "%"
                + " Reordering="
                + orderingSteps);
      }
    }
    mongoClient.close();
  }

  /**
   * Convert the groups docs into a map
   *
   * @param groups
   * @return
   */
  private static Map<String, Set<String>> convertToMap(List<Document> groups) {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    for (Document group : groups) {
      String groupID = group.get("group_id").toString();
      Set<String> diffHunks = new HashSet<>();
      for (Document dhID : (List<Document>) group.get("diff_hunks")) {
        diffHunks.add(dhID.get("diff_hunk_id").toString());
      }
      result.put(groupID, diffHunks);
    }
    return result;
  }

  private static void getAllEmails(String repoName) {
    System.out.println("Repo: " + repoName);
    MongoClientURI server =
        new MongoClientURI("mongodb://symbol:98eukk5age@47.113.179.146:29107/smartcommit");
    MongoClient serverClient = new MongoClient(server);

    try {
      MongoDatabase db = serverClient.getDatabase("smartcommit");
      MongoCollection<Document> col = db.getCollection("contacts");
      BasicDBObject condition =
          new BasicDBObject(
              "repos", new BasicDBObject("$elemMatch", new BasicDBObject("repo", repoName)));
      //      Bson condition = Filters.elemMatch("repos", Fil);

      try (MongoCursor<Document> cursor = col.find(condition).iterator()) {
        while (cursor.hasNext()) {
          Document doc = cursor.next();
          System.out.println((doc.get("email").toString()));
        }
      }

      serverClient.close();
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
    if (hunk.getRelativeFilePath().equals("/dev/null")
        || hunk.getRelativeFilePath().trim().isEmpty()) {
      hunkDoc.append("file_path", hunk.getRelativeFilePath().trim());
    } else {
      hunkDoc.append("file_path", dir + hunk.getRelativeFilePath());
    }
    hunkDoc.append("start_line", hunk.getStartLine());
    hunkDoc.append("end_line", hunk.getEndLine());
    hunkDoc.append("content", Utils.convertListLinesToString(hunk.getCodeSnippet()));
    hunkDoc.append("content_type", hunk.getContentType().label);
    return hunkDoc;
  }

  /**
   * Compute the edit distance of two lists with same elements but different order
   *
   * @param list1
   * @param list2
   * @return
   */
  public static int editDistance(List<Integer> list1, List<Integer> list2) {
    int M = list1.size();
    int N = list2.size();
    if (M * N == 0) {
      return M + N;
    }

    int[][] dp = new int[M + 1][N + 1];

    for (int i = 0; i < M + 1; i++) {
      dp[i][0] = i;
    }
    for (int j = 0; j < N + 1; j++) {
      dp[0][j] = j;
    }

    for (int i = 1; i < M + 1; i++) {
      for (int j = 1; j < N + 1; j++) {
        if (list1.get(i - 1).equals(list2.get(j - 1))) {
          dp[i][j] = dp[i - 1][j - 1];
        } else {
          dp[i][j] = Math.min(Math.min(dp[i - 1][j - 1] + 1, dp[i - 1][j] + 1), dp[i][j - 1] + 1);
        }
      }
    }
    return dp[M][N];
  }

  private static double jaccard(Set s1, Set s2) {
    Set<String> union = new HashSet<>();
    union.addAll(s1);
    union.addAll(s2);
    Set<String> intersection = new HashSet<>();
    intersection.addAll(s1);
    intersection.retainAll(s2);
    if (union.size() <= 0) {
      return 0D;
    } else {
      return (double) intersection.size() / union.size();
    }
  }

  private static double getMean(List<Double> numList) {
    Double average = numList.stream().mapToDouble(val -> val).average().orElse(0.0);
    return average;
  }

  private static double getMedian(List<Double> numList) {
    Double[] numArray = numList.toArray(new Double[0]);
    Arrays.sort(numArray);
    int middle = ((numArray.length) / 2);
    if (numArray.length % 2 == 0) {
      double medianA = numArray[middle];
      double medianB = numArray[middle - 1];
      return (medianA + medianB) / 2;
    } else {
      return numArray[middle + 1];
    }
  }

  private static void debugBatch(
      String repoName, String repoPath, String tempDir, String commitString) {

    String[] commitIDs = commitString.split("_");

    SmartCommit smartCommit =
        new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);
    smartCommit.setDetectRefactorings(true);
    smartCommit.setProcessNonJavaChanges(false);
    //    smartCommit.setDistanceThreshold(1);
    Stopwatch stopwatch = Stopwatch.createStarted();

    // randomly sample 100 composite commits for each size
    // combine consecutive 2/3/5 commitsByEmail into a change-set
    List<Double> resultAccs = new ArrayList<>();
    // All in one Group
    List<Double> baselineAccs1 = new ArrayList<>();
    // One file one Group
    List<Double> baselineAccs2 = new ArrayList<>();

    // ordered map
    Map<String, Set<String>> groundTruth = new LinkedHashMap<>();
    // union diff hunks in one change set, reorder the file index
    List<DiffFile> unionDiffFiles = new ArrayList<>();
    List<DiffHunk> unionDiffHunks = new ArrayList<>();
    Map<String, DiffHunk> unionDiffHunkMap = new HashMap<>();

    // collect data for each commit to be combined
    String resultsDir = tempDir + File.separator + commitString;
    Utils.clearDir(resultsDir);

    DataCollector dataCollector = new DataCollector(repoName, resultsDir);

    int LOC = 0;
    int j; // num of merged commits
    for (j = 0; j < commitIDs.length; j++) {
      String commitID = commitIDs[j];
      if (!commitID.isEmpty()) {

        // get diff hunks and save in groundTruth
        RepoAnalyzer repoAnalyzer =
            new RepoAnalyzer(String.valueOf(repoName.hashCode()), repoName, repoPath);

        List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(commitID);

        // collect all base and current files in the same folder
        dataCollector.collectDiffFilesWorking(diffFiles);

        // assign new index for diffFile and diffHunk
        int beginIndex = unionDiffFiles.size();
        diffFiles =
            diffFiles.stream()
                .filter(diffFile -> diffFile.getFileType().equals(FileType.JAVA))
                .collect(Collectors.toList());
        for (int k = 0; k < diffFiles.size(); ++k) {
          int newIndex = beginIndex + k;
          diffFiles.get(k).setIndex(newIndex);
          for (DiffHunk diffHunk : diffFiles.get(k).getDiffHunks()) {
            diffHunk.setFileIndex(newIndex);
            LOC +=
                (diffHunk.getBaseEndLine() - diffHunk.getBaseStartLine() + 1)
                    + (diffHunk.getCurrentEndLine() - diffHunk.getCurrentStartLine() + 1);
          }
        }
        List<DiffHunk> diffHunks = repoAnalyzer.getDiffHunks();
        if (diffHunks.isEmpty()) {
          continue;
        }

        unionDiffFiles.addAll(diffFiles);
        unionDiffHunks.addAll(diffHunks);

        unionDiffHunkMap.putAll(repoAnalyzer.getIdToDiffHunkMap());

        groundTruth.put(commitID, repoAnalyzer.getIdToDiffHunkMap().keySet());
      } else {
        break;
      }
    }

    // dirs that keeps the source code of diff files
    String baseDir = resultsDir + File.separator + Version.BASE.asString() + File.separator;
    String currentDir = resultsDir + File.separator + Version.CURRENT.asString() + File.separator;

    System.out.println("[Batch X" + ":" + commitString + "]");
    try {
      stopwatch.reset().start();
      Map<String, Group> results =
          smartCommit.analyze(unionDiffFiles, unionDiffHunks, Pair.of(baseDir, currentDir));
      stopwatch.stop();

      // generate "diffs" and "file_ids.json", which keeps diff info
      dataCollector.collectDiffHunks(unionDiffFiles, resultsDir);

      long timeCost = stopwatch.elapsed(TimeUnit.MILLISECONDS);

      // export for testing
      smartCommit.exportGroupResults(results, resultsDir);
      smartCommit.setId2DiffHunkMap(unionDiffHunkMap);
      Map<String, Group> groundTruthGroups = new HashMap<>();
      int gid = 0;
      for (Map.Entry en1 : groundTruth.entrySet()) {
        Group group =
            new Group(
                "",
                repoName,
                "group" + (gid++),
                new ArrayList<>((Set<String>) en1.getValue()),
                GroupLabel.OTHER);
        group.setCommitID(en1.getKey().toString());
        //        group.setCommitMsg(); // original commit msg
        groundTruthGroups.put(group.getGroupID(), group);
      }
      smartCommit.exportGroupDetails(
          groundTruthGroups, resultsDir + File.separator + "manual_details");
      smartCommit.exportGroupDetails(results, resultsDir + File.separator + "generated_details");

      // convert the group into map from groupid to diffhunkids
      Map<String, Set<String>> generatedResults = new LinkedHashMap<>();
      for (Map.Entry res : results.entrySet()) {
        Set<String> ids = new HashSet<>();
        for (String s : ((Group) res.getValue()).getDiffHunkIDs()) {
          ids.add(Utils.parseUUIDs(s).getRight());
        }
        generatedResults.put((String) res.getKey(), ids);
      }
      // smartcommit results
      Pair<List<Integer>, Double> resultMetrics = computeMetrics(groundTruth, generatedResults);

      int total = resultMetrics.getLeft().get(0);
      int correct = resultMetrics.getLeft().get(1);
      int reassignSteps = total - correct;
      int orderingSteps = resultMetrics.getLeft().get(2);
      double accuracy = resultMetrics.getRight();
      resultAccs.add(accuracy);
      System.out.println(
          "SmartCommit: \t"
              + "#groups="
              + results.size()
              + " #diff_hunks="
              + unionDiffHunks.size()
              + " #LOC="
              + LOC
              + " (#Incorrect/#Total)="
              + reassignSteps
              + "/"
              + total
              + " Reordering="
              + orderingSteps
              + " Accuracy="
              + accuracy
              + "%"
              + " Time="
              + timeCost
              + "ms");

      // baseline1: all diff hunks in one group
      Map<String, Set<String>> baselineResult1 = new LinkedHashMap<>();
      Set<String> allDiffHunkIDs = unionDiffHunkMap.keySet();
      baselineResult1.put("group0", allDiffHunkIDs);
      Pair<List<Integer>, Double> baselineMetrics1 = computeMetrics(groundTruth, baselineResult1);

      total = baselineMetrics1.getLeft().get(0);
      correct = baselineMetrics1.getLeft().get(1);
      reassignSteps = total - correct;
      orderingSteps = baselineMetrics1.getLeft().get(2);
      accuracy = baselineMetrics1.getRight();
      baselineAccs1.add(accuracy);
      System.out.println(
          "All in one Group: \t"
              + "#groups="
              + baselineResult1.size()
              + " #diff_hunks="
              + unionDiffHunks.size()
              + " #LOC="
              + LOC
              + " (#Incorrect/#Total)="
              + reassignSteps
              + "/"
              + total
              + " Reordering="
              + orderingSteps
              + " Accuracy="
              + accuracy
              + "%");

      // baseline2: group according to file
      Map<String, Set<String>> baselineResult2 = new LinkedHashMap<>();
      for (DiffFile diffFile : unionDiffFiles) {
        Set<String> diffHunkIDs =
            diffFile.getDiffHunks().stream()
                .map(DiffHunk::getDiffHunkID)
                .collect(Collectors.toSet());
        // use path as the group id
        String groupID =
            diffFile.getBaseRelativePath().isEmpty()
                ? diffFile.getCurrentRelativePath()
                : diffFile.getBaseRelativePath();
        if (baselineResult2.containsKey(groupID)) {
          baselineResult2.get(groupID).addAll(diffHunkIDs);
        } else {
          baselineResult2.put(groupID, diffHunkIDs);
        }
      }

      Pair<List<Integer>, Double> baselineMetrics2 = computeMetrics(groundTruth, baselineResult2);

      total = baselineMetrics2.getLeft().get(0);
      correct = baselineMetrics2.getLeft().get(1);
      reassignSteps = total - correct;
      orderingSteps = baselineMetrics2.getLeft().get(2);
      accuracy = baselineMetrics2.getRight();
      baselineAccs2.add(accuracy);
      System.out.println(
          "One File One Group: \t"
              + "#groups="
              + baselineResult2.size()
              + " #diff_hunks="
              + unionDiffHunks.size()
              + " #LOC="
              + LOC
              + " (#Incorrect/#Total)="
              + reassignSteps
              + "/"
              + total
              + " Reordering="
              + orderingSteps
              + " Accuracy="
              + accuracy
              + "%");

    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      e.printStackTrace();
    }
  }
}
