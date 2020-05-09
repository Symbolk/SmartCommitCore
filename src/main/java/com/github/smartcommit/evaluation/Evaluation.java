package com.github.smartcommit.evaluation;

import com.github.smartcommit.client.SmartCommit;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.model.*;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
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
import java.util.concurrent.TimeoutException;

public class Evaluation {
  private static final Logger logger = Logger.getLogger(Evaluation.class);

  public static void main(String[] args) {
    BasicConfigurator.configure();
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);

    String repoDir = "/Users/symbolk/coding/data/repos/";
    String resultsDir = "/Users/symbolk/coding/data/results/";
    String tempDir = "/Users/symbolk/coding/data/temp/";

    // per project and avg
    String repoName = "elasticsearch";
    String repoPath = repoDir + repoName;
    //    runRQ1(repoName, repoPath, tempDir + "/RQ1/" + repoName, 5);
    runRQ2(repoName, repoPath, tempDir + "/RQ2/" + repoName);
    //    getAllEmails(repoName);
  }

  /**
   * RQ1: grouping precision and recall
   *
   * @param repoPath
   */
  private static void runRQ1(String repoName, String repoPath, String dir, int step) {
    System.out.println("RQ1: " + repoName + " Step: " + step);
    String tempDir = dir + File.separator + step;
    Utils.clearDir(tempDir);

    // read samples from db
    MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
    MongoClient mongoClient = new MongoClient(connectionString);
    MongoDatabase db = mongoClient.getDatabase("atomic");
    MongoCollection<Document> col = db.getCollection(repoName);

    // get atomic commits list from mongodb
    List<String> atomicCommits = new ArrayList<>();
    try (MongoCursor<Document> cursor = col.find().iterator()) {
      int num = 0;
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        String commitID = (String) doc.get("commit_id");
        atomicCommits.add(commitID);
        num++;
        if (num >= 100) {
          break;
        }
      }
    }
    mongoClient.close();

    SmartCommit smartCommit =
        new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);
    smartCommit.setDetectRefactorings(true);
    smartCommit.setProcessNonJavaChanges(true);

    // combine consecutive 2~5 commits into a change-set
    double sumAcc = 0D;
    for (int i = 0; i < atomicCommits.size(); i += step) {
      // ordered map
      Map<String, Set<String>> groundTruth = new LinkedHashMap<>();
      // union diff hunks in one change set, reorder the file index
      List<DiffFile> unionDiffFiles = new ArrayList<>();
      List<DiffHunk> unionDiffHunks = new ArrayList<>();

      // collect data for each commit to be combined
      String resultsDir = tempDir + File.separator + atomicCommits.get(i);
      DataCollector dataCollector = new DataCollector(repoName, resultsDir);

      for (int j = 0; j < step; j++) {
        if (i + j < atomicCommits.size()) {
          String commitID = atomicCommits.get(i + j);
          if (!commitID.isEmpty()) {
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
              }
            }
            List<DiffHunk> allDiffHunks = repoAnalyzer.getDiffHunks();

            unionDiffFiles.addAll(diffFiles);
            unionDiffHunks.addAll(allDiffHunks);

            groundTruth.put(commitID, repoAnalyzer.getIdToDiffHunkMap().keySet());
          }
        }
      }

      // dirs that keeps the source code of diff files
      String baseDir = resultsDir + File.separator + Version.BASE.asString() + File.separator;
      String currentDir = resultsDir + File.separator + Version.CURRENT.asString() + File.separator;
      // call to get grouping results

      try {
        Map<String, Group> results =
            smartCommit.analyze(unionDiffFiles, unionDiffHunks, Pair.of(baseDir, currentDir));
        smartCommit.exportGroupResults(results, resultsDir);

        // convert the group into map from groupid to diffhunkids
        Map<String, Set<String>> generatedResults = new LinkedHashMap<>();
        for (Map.Entry entry : results.entrySet()) {
          Set<String> ids = new HashSet<>();
          for (String s : ((Group) entry.getValue()).getDiffHunkIDs()) {
            ids.add(Utils.parseUUIDs(s).getRight());
          }
          generatedResults.put((String) entry.getKey(), ids);
        }

        Pair<List<Integer>, Double> res = computeMetrics(groundTruth, generatedResults);
        int total = res.getLeft().get(0);
        int correct = res.getLeft().get(1);
        int orderingSteps = res.getLeft().get(2);
        System.out.println(
            "[Batch "
                + (i / step)
                + "] (#Incorrect/#Total)= "
                + (total - correct)
                + "/"
                + total
                + " Accuracy="
                + res.getRight()
                + " Reordering="
                + orderingSteps);
        sumAcc += res.getRight();
      } catch (ExecutionException | InterruptedException | TimeoutException e) {
        e.printStackTrace();
      }
    }
    System.out.println(
        "Average Accuracy: "
            + Utils.formatDouble(sumAcc / (atomicCommits.size() / step) * 100)
            + "%");
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
    List<Integer> clusterSizes = new ArrayList<>();
    Set<Pair<String, String>> groundTruthPairs = new HashSet();
    Set<Pair<String, String>> resultPairs = new HashSet();

    int id = 0;
    for (Map.Entry entry : groundTruth.entrySet()) {
      BipartiteNode n = new BipartiteNode(id++, (Set<String>) entry.getValue());
      partition1.add(n);
      List<String> temp = new ArrayList<>((Set<String>) entry.getValue());
      totalChangesNum += temp.size();
      clusterSizes.add(temp.size());
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
    // dimension 1: distribution
    Map<Integer, Integer> idMap = new HashMap<>();
    for (DefaultWeightedEdge edge : edges) {
      correctChangesNum += bipartite.getEdgeWeight(edge);
      idMap.put(bipartite.getEdgeTarget(edge).id, bipartite.getEdgeSource(edge).id);
    }

    // dimension 2: ordering
    List<Integer> list1 = new ArrayList<>(); // ground truth (natural order)
    List<Integer> list2 = new ArrayList<>(); // generatedResult (mapped)
    for (BipartiteNode node : partition1) {
      list1.add(node.id);
    }
    for (BipartiteNode node : partition2) {
      list2.add(idMap.get(node.id));
    }

    // since the edit allowed here is moving groups, so edit distance need to be divided by 2
    int reorderingSteps = editDistance(list1, list2) / 2;

    // accuracy
    int rightPairs = 0;
    for (Pair<String, String> p1 : groundTruthPairs) {
      for (Pair<String, String> p2 : resultPairs) {
        if ((p1.getLeft().equals(p2.getLeft()) && p1.getRight().equals(p2.getRight()))
            || (p1.getLeft().equals(p2.getRight()) && p1.getRight().equals(p2.getLeft()))) {
          rightPairs += 1;
        }
      }
    }
    int wrongPairs = 0;
    for (int i = 0; i < clusterSizes.size() - 1; ++i) {
      for (int j = i + 1; j < clusterSizes.size(); ++j) {
        wrongPairs += clusterSizes.get(i) * clusterSizes.get(j);
      }
    }
    double accuracy =
        Utils.formatDouble(
            (double) (rightPairs + wrongPairs) / ((totalChangesNum * (totalChangesNum - 1) / 2)));

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
    smartCommit.setProcessNonJavaChanges(true);

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
}
