package com.github.smartcommit.client;

import com.github.smartcommit.core.GraphBuilder;
import com.github.smartcommit.core.GroupGenerator;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.constant.ChangeType;
import com.github.smartcommit.model.constant.GroupLabel;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jgrapht.Graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/** API entry */
public class SmartCommit {
  private static final Logger logger = Logger.getLogger(SmartCommit.class);
  private final String repoID;
  private final String repoName;
  private final String repoPath;
  private final String tempDir;
  private Map<String, DiffHunk> id2DiffHunkMap;

  // saved for analysis
  Graph<Node, Edge> baseGraph;
  Graph<Node, Edge> currentGraph;

  // options and default
  private boolean detectRefactorings = false;
  private boolean processNonJavaChanges = false;
  private double weightThreshold = 0D;
  private double minSimilarity = 0.8D;
  private int maxDistance = 0;

  /**
   * Initial setup for analysis
   *
   * @param repoID repo unique id (used for database)
   * @param repoName repo name
   * @param repoPath absolute local repo path
   * @param tempDir temporary directory path for intermediate and final result
   */
  public SmartCommit(String repoID, String repoName, String repoPath, String tempDir) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.repoPath = repoPath;
    this.tempDir = tempDir;
    this.baseGraph = null;
    this.currentGraph = null;
    this.id2DiffHunkMap = new HashMap<>();
  }

  public void setDetectRefactorings(boolean detectRefactorings) {
    this.detectRefactorings = detectRefactorings;
  }

  public void setProcessNonJavaChanges(boolean processNonJavaChanges) {
    this.processNonJavaChanges = processNonJavaChanges;
  }

  public void setWeightThreshold(Double weightThreshold) {
    this.weightThreshold = weightThreshold;
  }

  public void setMinSimilarity(double minSimilarity) {
    this.minSimilarity = minSimilarity;
  }

  public void setMaxDistance(int maxDistance) {
    this.maxDistance = maxDistance;
  }

  public void setId2DiffHunkMap(Map<String, DiffHunk> id2DiffHunkMap) {
    this.id2DiffHunkMap = id2DiffHunkMap;
  }

  public Map<String, DiffHunk> getId2DiffHunkMap() {
    return id2DiffHunkMap;
  }

  /**
   * Clear the temp dir and create the logs dir
   *
   */
  private void prepareTempDir(String dir) {
    Utils.clearDir(dir);
    System.setProperty("logs.dir", dir);
    //    PropertyConfigurator.configure("log4j.properties");
  }

  /**
   * Analyze the current working directory of the repository
   *
   * @return suggested groups <id:group>
   */
  public Map<String, Group> analyzeWorkingTree() {
    prepareTempDir(tempDir);
    // 1. analyze the repo
    RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);
    List<DiffFile> diffFiles = repoAnalyzer.analyzeWorkingTree();
    List<DiffHunk> allDiffHunks = repoAnalyzer.getDiffHunks();
    if (diffFiles.isEmpty()) {
      logger.info("Nothing to commit, working tree clean.");
      return new HashMap<>();
    }

    boolean onlyEncodingChange = false;
    if (allDiffHunks.isEmpty()) {
      logger.info("Changes exist, but not in file contents.");
      onlyEncodingChange = true;

      // mock one dummy diff hunk for each diff file to continue commit
      for (DiffFile diffFile : diffFiles) {
        List<DiffHunk> diffHunksInFile = new ArrayList<>();

        DiffHunk diffHunk =
            new DiffHunk(
                0,
                diffFile.getFileType(),
                ChangeType.MODIFIED,
                new com.github.smartcommit.model.Hunk(
                    Version.BASE,
                    diffFile.getBaseRelativePath(),
                    0,
                    0,
                    Utils.checkContentType(Utils.convertStringToList(diffFile.getBaseContent())),
                    new ArrayList<>()),
                new com.github.smartcommit.model.Hunk(
                    Version.CURRENT,
                    diffFile.getCurrentRelativePath(),
                    0,
                    0,
                    Utils.checkContentType(Utils.convertStringToList(diffFile.getCurrentContent())),
                    new ArrayList<>()),
                "file/line encodings change");
        diffHunk.setRawDiffs(new ArrayList<>());
        // bind diff file with diff hunks
        diffHunk.setFileIndex(diffFile.getIndex());
        diffHunksInFile.add(diffHunk);
        diffFile.setDiffHunks(diffHunksInFile);

        allDiffHunks.add(diffHunk);
      }
    }

    this.id2DiffHunkMap = repoAnalyzer.getIdToDiffHunkMap();

    // 2. collect the data into temp dir
    // (1) diff files (2) file id mapping (3) diff hunks
    DataCollector dataCollector = new DataCollector(repoName, tempDir);
    // dirs that keeps the source code of diff files
    Pair<String, String> srcDirs = dataCollector.collectDiffFilesWorking(diffFiles);

    Map<String, Group> results = new HashMap<>();
    if (onlyEncodingChange) {
      List<String> allDiffHunkIDs =
          allDiffHunks.stream().map(DiffHunk::getDiffHunkID).collect(Collectors.toList());
      Group group = new Group(repoID, repoName, "group0", allDiffHunkIDs, GroupLabel.OTHER);
      group.setRecommendedCommitMsgs(Collections.singletonList("Change file/line encodings."));
      results.put("group0", group);
    } else {
      results = analyze(diffFiles, allDiffHunks, srcDirs);
    }

    dataCollector.collectDiffHunks(diffFiles, tempDir);

    // generate commit message
    if (results != null) {
      for (Map.Entry<String, Group> entry : results.entrySet()) {
        Group group = entry.getValue();
        // generate recommended commit messages
        group.setRecommendedCommitMsgs(generateCommitMsg(group));
      }
      // save the results on disk
      exportGroupResults(results, tempDir);
      exportGroupDetails(results, tempDir + File.separator + "details");
    }

    return results;
  }

  /**
   * Analyze a specific commit of the repository for decomposition
   *
   * @param commitID the target commit hash id
   * @return suggested groups <id:group>
   */
  public Map<String, Group> analyzeCommit(String commitID) {
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

    dataCollector.collectDiffHunks(diffFiles, resultsDir);

    exportGroupResults(results, resultsDir);
    exportGroupDetails(results, resultsDir + File.separator + "details");

    return results;
  }

  /**
   * Analyze the changes collected
   *
   */
  public Map<String, Group> analyze(
      List<DiffFile> diffFiles, List<DiffHunk> allDiffHunks, Pair<String, String> srcDirs) {

    try {
      buildRefGraphs(diffFiles, srcDirs);
    } catch (Exception e) {
      System.err.println("Exception during graph building:");
      e.printStackTrace();
    }

    // analyze the diff hunks
    GroupGenerator generator =
        new GroupGenerator(
            repoID, repoName, srcDirs, diffFiles, allDiffHunks, baseGraph, currentGraph);
    generator.setMinSimilarity(minSimilarity);
    generator.setMaxDistance(maxDistance);
    generator.enableRefDetection(detectRefactorings);
    generator.enableNonJavaChanges(processNonJavaChanges);
    generator.buildDiffGraph();
    return generator.generateGroups(weightThreshold);
  }

  /**
   * Build the Entity Reference Graphs for base and current versions
   *
   */
  private void buildRefGraphs(List<DiffFile> diffFiles, Pair<String, String> srcDirs)
      throws ExecutionException, InterruptedException, TimeoutException {
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    Future<Graph<Node, Edge>> baseBuilder =
        executorService.submit(new GraphBuilder(srcDirs.getLeft(), diffFiles));
    Future<Graph<Node, Edge>> currentBuilder =
        executorService.submit(new GraphBuilder(srcDirs.getRight(), diffFiles));
    baseGraph = baseBuilder.get(60 * 10, TimeUnit.SECONDS);
    currentGraph = currentBuilder.get(60 * 10, TimeUnit.SECONDS);
    //            String baseDot = GraphExporter.exportAsDotWithType(baseGraph);
    //            String currentDot = GraphExporter.exportAsDotWithType(currentGraph);
    executorService.shutdown();
  }

  /**
   * Solely for evaluation with ClusterChanges
   *
   */
  public Map<String, Group> analyzeWithCC(
      List<DiffFile> diffFiles, List<DiffHunk> allDiffHunks, Pair<String, String> srcDirs) {
    if (baseGraph == null && currentGraph == null) {
      try {
        buildRefGraphs(diffFiles, srcDirs);
      } catch (Exception e) {
        System.err.println("Exception during graph building:");
        e.printStackTrace();
      }
    }

    // analyze the diff hunks
    GroupGenerator generator =
        new GroupGenerator(
            repoID, repoName, srcDirs, diffFiles, allDiffHunks, baseGraph, currentGraph);
    generator.buildDiffGraph();
    return generator.clusterChanges();
  }

  /**
   * Analyze with one specific type of links for ablation
   *
   * @param filters: filter one or several types of links: 0 hard, 1 soft, 2 pattern, 3 logical
   */
  public Map<String, Group> analyzeWithAblation(
      List<DiffFile> diffFiles,
      List<DiffHunk> allDiffHunks,
      Pair<String, String> srcDirs,
      int... filters) {

    // only build ref graphs if they are null
    if (baseGraph == null && currentGraph == null) {
      try {
        buildRefGraphs(diffFiles, srcDirs);
      } catch (Exception e) {
        System.err.println("Exception during graph building:");
        e.printStackTrace();
      }
    }

    // analyze the diff hunks
    GroupGenerator generator =
        new GroupGenerator(
            repoID, repoName, srcDirs, diffFiles, allDiffHunks, baseGraph, currentGraph);
    generator.setMinSimilarity(minSimilarity);
    generator.setMaxDistance(maxDistance);
    generator.enableRefDetection(detectRefactorings);
    generator.enableNonJavaChanges(processNonJavaChanges);
    generator.buildDiffGraph();
    return generator.generateGroups(weightThreshold, filters);
  }

  /**
   * Save meta information of each group, including diff hunk ids, commit msgs, etc.
   *
   * @param generatedGroups generated groups <id:group>
   * @param outputDir output directory path
   */
  public void exportGroupResults(Map<String, Group> generatedGroups, String outputDir) {
    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    for (Map.Entry<String, Group> entry : generatedGroups.entrySet()) {
      Utils.writeStringToFile(
          gson.toJson(entry.getValue()),
          outputDir
              + File.separator
              + "generated_groups"
              + File.separator
              + entry.getKey()
              + ".json");
      // any manual adjustments will be made on this copy
      Utils.writeStringToFile(
          gson.toJson(entry.getValue()),
          outputDir + File.separator + "manual_groups" + File.separator + entry.getKey() + ".json");
    }
  }

  /**
   * Generate and save the detailed content of diff hunks for each group
   *
   * @param results generated groups <id:group>
   * @param outputDir output directory path
   */
  public void exportGroupDetails(Map<String, Group> results, String outputDir) {
    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    List<String> groupedDiffHunks = new ArrayList<>();
    for (Map.Entry<String, Group> entry : results.entrySet()) {
      String path = outputDir + File.separator + entry.getKey() + ".json";
      StringBuilder builder = new StringBuilder();
      builder.append(entry.getValue().getIntentLabel()).append("\n");
      builder.append(entry.getValue().getCommitMsg()).append("\n");
      // check for duplication
      for (String id : entry.getValue().getDiffHunkIDs()) {
        if (groupedDiffHunks.contains(id)) {
          DiffHunk diffHunk = id2DiffHunkMap.get(id.split(":")[1]);
          logger.error("Duplicate DiffHunk: " + diffHunk.getUniqueIndex());
        }
        groupedDiffHunks.add(id);
        String[] pair = id.split(":");
        String diffHunkID;
        if (pair.length == 2) {
          diffHunkID = pair[1];
        } else if (pair.length == 1) {
          diffHunkID = pair[0];
        } else {
          logger.error("Invalid id: " + id);
          continue;
        }
        builder.append("------------").append("\n");
        DiffHunk diffHunk = id2DiffHunkMap.get(diffHunkID);
        builder.append(diffHunk.getUniqueIndex()).append("\n");
        builder.append(diffHunk.getDescription()).append("\n");
        builder.append(gson.toJson(diffHunk.getBaseHunk())).append("\n");
        builder.append(gson.toJson(diffHunk.getCurrentHunk())).append("\n");
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
        String fileID = idPair.getLeft();
        String diffHunkID = idPair.getRight();
        if (fileID2hunkIDsMap.containsKey(fileID)) {
          fileID2hunkIDsMap.get(fileID).add(diffHunkID);
        } else {
          List<String> temp = new ArrayList<>();
          temp.add(diffHunkID);
          fileID2hunkIDsMap.put(fileID, temp);
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

    //    CommitMsgGenerator generator = new CommitMsgGenerator(astActions, refActions);
    //    List<Integer> vectors = generator.generateGroupVector();
    //    MsgClass msgClass = generator.invokeAIModel(vectors);
    //    return generator.generateDetailedMsgs(msgClass, group.getIntentLabel());
    return new ArrayList<>();
  }

  /**
   * Commit all the selected groups with the given commit messages
   *
   * @param commitMsgs "group1":"Feature ...."
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
}
