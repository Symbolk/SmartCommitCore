package com.github.smartcommit.client;

import com.github.smartcommit.core.GraphBuilder;
import com.github.smartcommit.core.GroupGenerator1;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.jgrapht.Graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/** API entry */
public class SmartCommit {
  private static final Logger logger = Logger.getLogger(SmartCommit.class);
  private String repoID;
  private String repoName;
  private String repoPath;
  private String tempDir;
  private Map<String, DiffHunk> id2DiffHunkMap;
  // options
  private Boolean detectRefactorings = false;
  private Double similarityThreshold = 0.618D;
  // {hunk: 0 (default), member: 1, class: 2, package: 3}
  private Integer distanceThreshold = 0;

  public SmartCommit(String repoID, String repoName, String repoPath, String tempDir) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.repoPath = repoPath;
    this.tempDir = tempDir;
    this.id2DiffHunkMap = new HashMap<>();
  }

  public void setDetectRefactorings(Boolean detectRefactorings) {
    this.detectRefactorings = detectRefactorings;
  }

  public void setSimilarityThreshold(Double similarityThreshold) {
    this.similarityThreshold = similarityThreshold;
  }

  public void setDistanceThreshold(Integer distanceThreshold) {
    this.distanceThreshold = distanceThreshold;
  }

  /**
   * Clear the temp dir and create the logs dir
   *
   * @param dir
   */
  private void prepareTempDir(String dir) {
    Utils.clearDir(dir);
    System.setProperty("logs.dir", dir);
    PropertyConfigurator.configure("log4j.properties");
  }

  /**
   * Analyze the current working directory
   *
   * @return
   * @throws Exception
   */
  public Map<String, Group> analyzeWorkingTree() throws Exception {
    prepareTempDir(tempDir);
    // 1. analyze the repo
    RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);
    List<DiffFile> diffFiles = repoAnalyzer.analyzeWorkingTree();
    List<DiffHunk> allDiffHunks = repoAnalyzer.getDiffHunks();
    if (diffFiles.isEmpty()) {
      logger.info("Nothing to commit, working tree clean.");
      return new HashMap<>();
    }

    if (allDiffHunks.isEmpty()) {
      logger.info("Changes exist, but not in file contents.");
      return new HashMap<>();
    }

    this.id2DiffHunkMap = repoAnalyzer.getIdToDiffHunkMap();

    // 2. collect the data into temp dir
    // (1) diff files (2) file id mapping (3) diff hunks
    DataCollector dataCollector = new DataCollector(repoName, tempDir);
    // dirs that keeps the source code of diff files
    Pair<String, String> srcDirs = dataCollector.collectDiffFilesWorking(diffFiles);

    Map<String, Group> results = analyze(diffFiles, allDiffHunks, srcDirs);

    Map<String, String> fileIDToPathMap = dataCollector.collectDiffHunks(diffFiles, tempDir);

    // generate commit message
    if (results != null) {
      for (Map.Entry<String, Group> entry : results.entrySet()) {
        Group group = entry.getValue();
        // generate recommended commit messages
        group.setRecommendedCommitMsgs(generateCommitMsg(group));
      }
    }

    // save the results on disk
    exportGroupResults(results, tempDir);

    return results;
  }

  /**
   * Analyze a specific commit
   *
   * @param commitID
   * @return
   * @throws Exception
   */
  public Map<String, Group> analyzeCommit(String commitID) throws Exception {
    String resultsDir = tempDir + File.separator + commitID;
    prepareTempDir(resultsDir);

    // 1. analyze the repo
    RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);
    List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(commitID);
    List<DiffHunk> allDiffHunks = repoAnalyzer.getDiffHunks();

    if (diffFiles.isEmpty() || allDiffHunks.isEmpty()) {
      logger.info("No changes at commit: " + commitID);
      return new HashMap<>();
    }

    this.id2DiffHunkMap = repoAnalyzer.getIdToDiffHunkMap();

    // 2. collect the data into temp dir
    DataCollector dataCollector = new DataCollector(repoName, tempDir);
    // dirs that keeps the source code of diff files
    Pair<String, String> srcDirs = dataCollector.collectDiffFilesAtCommit(commitID, diffFiles);

    Map<String, Group> results = analyze(diffFiles, allDiffHunks, srcDirs);

    Map<String, String> fileIDToPathMap = dataCollector.collectDiffHunks(diffFiles, resultsDir);

    exportGroupResults(results, resultsDir);

    return results;
  }

  /**
   * Analyze the changes collected
   *
   * @param diffFiles
   * @param allDiffHunks
   * @param srcDirs
   * @return
   */
  private Map<String, Group> analyze(
      List<DiffFile> diffFiles, List<DiffHunk> allDiffHunks, Pair<String, String> srcDirs)
      throws ExecutionException, InterruptedException, TimeoutException {

    // build the change semantic graph
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    Future<Graph<Node, Edge>> baseBuilder =
        executorService.submit(new GraphBuilder(srcDirs.getLeft(), diffFiles));
    Future<Graph<Node, Edge>> currentBuilder =
        executorService.submit(new GraphBuilder(srcDirs.getRight(), diffFiles));
    Graph<Node, Edge> baseGraph = baseBuilder.get(60 * 10, TimeUnit.SECONDS);
    Graph<Node, Edge> currentGraph = currentBuilder.get(60 * 10, TimeUnit.SECONDS);
    //    String baseDot = GraphExporter.exportAsDotWithType(baseGraph);
    //    String currentDot = GraphExporter.exportAsDotWithType(currentGraph);
    executorService.shutdown();

    // analyze the diff hunks
    GroupGenerator groupGenerator =
        new GroupGenerator(
            repoID,
            repoName,
            similarityThreshold,
            distanceThreshold,
            diffFiles,
            allDiffHunks,
            baseGraph,
            currentGraph);
    groupGenerator.analyzeNonJavaFiles();
    groupGenerator.analyzeSoftLinks();
    groupGenerator.analyzeHardLinks();
    if (detectRefactorings) {
      groupGenerator.analyzeRefactorings(tempDir);
    }

    // visualize the diff hunk graph
    //    String diffGraphString =
    //        DiffGraphExporter.exportAsDotWithType(groupGenerator.getDiffHunkGraph());

    return groupGenerator.generateGroups();
  }

  /**
   * Save generated group results into the target dir
   *
   * @param generatedGroups
   */
  public void exportGroupResults(Map<String, Group> generatedGroups, String targetDir) {
    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    for (Map.Entry<String, Group> entry : generatedGroups.entrySet()) {
      Utils.writeStringToFile(
          gson.toJson(entry.getValue()),
          targetDir
              + File.separator
              + "generated_groups"
              + File.separator
              + entry.getKey()
              + ".json");
      // the copy to accept the user feedback
      Utils.writeStringToFile(
          gson.toJson(entry.getValue()),
          targetDir + File.separator + "manual_groups" + File.separator + entry.getKey() + ".json");
    }
  }

  /**
   * Generate and save the details of the grouping results
   *
   * @param results
   */
  public void exportGroupDetails(Map<String, Group> results) {
    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    List<String> groupedDiffHunks = new ArrayList<>();
    for (Map.Entry<String, Group> entry : results.entrySet()) {
      String path =
          tempDir + File.separator + "details" + File.separator + entry.getKey() + ".json";
      StringBuilder builder = new StringBuilder();
      builder.append(entry.getValue().getIntentLabel()).append("\n");
      for (String id : entry.getValue().getDiffHunkIDs()) {
        if (groupedDiffHunks.contains(id)) {
          DiffHunk diffHunk = id2DiffHunkMap.get(id.split(":")[1]);
          logger.error("Duplicate DiffHunk: " + diffHunk.getUniqueIndex());
        }
        groupedDiffHunks.add(id);
        String[] pair = id.split(":");
        if (pair.length == 2) {
          builder.append("------------").append("\n");
          DiffHunk diffHunk = id2DiffHunkMap.get(pair[1]);
          builder.append(diffHunk.getUniqueIndex()).append("\n");
          builder.append(diffHunk.getDescription()).append("\n");
          builder.append(gson.toJson(diffHunk.getBaseHunk())).append("\n");
          builder.append(gson.toJson(diffHunk.getCurrentHunk())).append("\n");
        } else {
          logger.error("Invalid id: " + id);
        }
      }
      Utils.writeStringToFile(builder.toString(), path);
    }

    if (groupedDiffHunks.size() != id2DiffHunkMap.keySet().size()) {
      logger.error(
          "Incorrect #diffhunks: Actual/Expected= "
              + groupedDiffHunks.size()
              + "/"
              + id2DiffHunkMap.keySet().size());
    }
  }

  /**
   * Read selected group json file, generate patches that can be applied incrementally for
   * inter-versions
   *
   * @param selectedGroupIDs
   * @throws FileNotFoundException
   */
  public void exportPatches(List<String> selectedGroupIDs) throws FileNotFoundException {
    String manualGroupsDir = tempDir + File.separator + "manual_groups";
    String fileDiffsDir = tempDir + File.separator + "diffs";
    String patchesDir = tempDir + File.separator + "patches";
    Utils.clearDir(patchesDir);

    List<String> groupFilePaths = Utils.listAllJsonFilePaths(manualGroupsDir);
    Gson gson = new Gson();
    for (String path : groupFilePaths) {
      StringBuilder builder = new StringBuilder();
      // read and parse group json file
      JsonReader reader = new JsonReader(new FileReader(path));
      Group group = gson.fromJson(reader, Group.class);
      // put diff hunks within the same file together
      Map<String, List<String>> fileID2hunkIDsMap = new HashMap<>();
      for (String id : group.getDiffHunkIDs()) {
        Pair<String, String> idPair = Utils.parseUUIDs(id);
        if (idPair != null) {
          String fileID = idPair.getLeft();
          String diffHunkID = idPair.getRight();
          if (fileID2hunkIDsMap.containsKey(fileID)) {
            fileID2hunkIDsMap.get(fileID).add(diffHunkID);
          } else {
            List<String> temp = new ArrayList<>();
            temp.add(diffHunkID);
            fileID2hunkIDsMap.put(fileID, temp);
          }
        } else {
          logger.error("Null idPair with " + id);
        }
      }

      // read and parse the diff json by file id
      for (Map.Entry<String, List<String>> entry : fileID2hunkIDsMap.entrySet()) {
        String fileDiffPath = fileDiffsDir + File.separator + entry.getKey() + ".json";
        reader = new JsonReader(new FileReader(fileDiffPath));
        DiffFile diffFile = gson.fromJson(reader, DiffFile.class);
        // get headers and raw diffs
        builder
            .append(String.join(System.lineSeparator(), diffFile.getRawHeaders()))
            .append(System.lineSeparator());
        for (String diffHunkID : entry.getValue()) {
          DiffHunk diffHunk = diffFile.getDiffHunksMap().getOrDefault(diffHunkID, null);
          if (diffHunk != null) {
            builder
                .append(String.join(System.lineSeparator(), diffHunk.getRawDiffs()))
                .append(System.lineSeparator());
          } else {
            logger.error("Null diffHunk with id: " + diffHunkID);
          }
        }
      }
      // save patches in temp dir
      String resultPath = patchesDir + File.separator + group.getGroupID() + ".patch";
      Utils.writeStringToFile(builder.toString(), resultPath);
    }
  }

  /**
   * Generate commit message for a given group
   *
   * @param group
   * @return
   */
  public List<String> generateCommitMsg(Group group) {
    // get the ast actions and refactoring actions
    List<String> diffHunkIDs = group.getDiffHunkIDs();
    List<Action> astActions = new ArrayList<>();
    List<Action> refActions = new ArrayList<>();
    for (String id : diffHunkIDs) {
      DiffHunk diffHunk = id2DiffHunkMap.getOrDefault(id.split(":")[1], null);
      if (diffHunk != null) {
        astActions.addAll(diffHunk.getAstActions());
        refActions.addAll(diffHunk.getRefActions());
      }
    }

    CommitMsgGenerator generator = new CommitMsgGenerator(astActions, refActions);
    List<Integer> vectors = generator.generateGroupVector();
    MsgClass msgClass = generator.invokeAIModel(vectors);
    return generator.generateDetailedMsgs(msgClass, group.getIntentLabel());
  }

  /**
   * Commit all the selected groups with the given commit messages
   *
   * @param selectedGroupIDs
   * @param commitMsgs "group1":"Feature ...."
   * @return
   */
  public boolean commit(List<String> selectedGroupIDs, Map<String, String> commitMsgs) {
    GitServiceCGit gitService = new GitServiceCGit();
    // clear the working dir firstly to prepare for applying patches
    if (gitService.clearWorkingTree(repoPath)) {
      for (String id : selectedGroupIDs) {
        String msg = commitMsgs.getOrDefault(id, "<Empty Commit Message>");
        // git apply patchX.patch
        // git add .
        // git commit -m "XXX"
      }

      // after all selected groups committed, stash the remaining changes
      // combine all uncommitted patches

      // apply the patches (TODO: base has changed)

      // stash the working tree

      return true;
    } else {
      logger.error("Failed to clear the working tree.");
      return false;
    }
  }

  public Map<String, DiffHunk> getId2DiffHunkMap() {
    return id2DiffHunkMap;
  }
}
