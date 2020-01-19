package com.github.smartcommit.client;

import com.github.smartcommit.core.GraphBuilder;
import com.github.smartcommit.core.GroupGenerator;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.io.DiffGraphExporter;
import com.github.smartcommit.io.GraphExporter;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** API entry */
public class SmartCommit {
  private static final Logger logger = LoggerFactory.getLogger(SmartCommit.class);
  private String repoID;
  private String repoName;
  private String repoPath;
  private String tempDir;
  // options
  private Boolean detectRefactorings;

  public SmartCommit(String repoID, String repoName, String repoPath, String tempDir) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.repoPath = repoPath;
    this.tempDir = tempDir;
    this.detectRefactorings = false;
    Utils.clearDir(tempDir);
  }

  public void setDetectRefactorings(Boolean detectRefactorings) {
    this.detectRefactorings = detectRefactorings;
  }

  /**
   * Analyze the current working directory
   *
   * @return
   * @throws Exception
   */
  public Map<String, Group> analyzeWorkingTree() throws Exception {
    // 1. analyze the repo
    RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);
    List<DiffFile> diffFiles = repoAnalyzer.analyzeWorkingTree();
    List<DiffHunk> allDiffHunks = repoAnalyzer.getDiffHunks();
    if (diffFiles.isEmpty()) {
      logger.info("Nothing to commit, working tree clean.");
      return null;
    }

    if (allDiffHunks.isEmpty()) {
      logger.info("File contents are unchanged.");
      return null;
    }

    // 2. collect the data into temp dir
    // (1) diff files (2) file id mapping (3) diff hunks
    DataCollector dataCollector = new DataCollector(repoName, tempDir);
    Pair<String, String> dataPaths = dataCollector.collectDiffFilesWorking(diffFiles);

    Map<String, Group> results = analyze(diffFiles, allDiffHunks, dataPaths);

    Map<String, String> fileIDToPathMap = dataCollector.collectDiffHunksWorking(diffFiles);

    // comment when packaging
//    generateIntermediateVersions(results, repoAnalyzer.getIdToDiffHunkMap());

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
    {
      // 1. analyze the repo
      RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);
      List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(commitID);
      List<DiffHunk> allDiffHunks = repoAnalyzer.getDiffHunks();

      if (diffFiles.isEmpty() || allDiffHunks.isEmpty()) {
        logger.info("Files are unchanged at commit: " + commitID);
        return null;
      }

      // 2. collect the data into temp dir
      DataCollector dataCollector = new DataCollector(repoName, tempDir);
      Pair<String, String> dataPaths = dataCollector.collectDiffFilesAtCommit(commitID, diffFiles);

      Map<String, Group> results = analyze(diffFiles, allDiffHunks, dataPaths);
      // comment when packaging
      generateIntermediateVersions(results, repoAnalyzer.getIdToDiffHunkMap());
      return results;
    }
  }

  /**
   * Generate and save the intermediate versions to manually validate
   *
   * @param results
   * @param id2DiffHunkMap
   */
  private void generateIntermediateVersions(
      Map<String, Group> results, Map<String, DiffHunk> id2DiffHunkMap) {
    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    List<String> groupedDiffHunks = new ArrayList<>();
    for (Map.Entry<String, Group> entry : results.entrySet()) {
      String path =
          tempDir + File.separator + "versions" + File.separator + entry.getKey() + ".json";
      StringBuilder builder = new StringBuilder();
      builder.append(entry.getValue().getIntentLabel()).append("\n");
      for (String id : entry.getValue().getDiffHunks()) {
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
   * Analyze the changes collected
   *
   * @param diffFiles
   * @param allDiffHunks
   * @param dataPaths
   * @return
   */
  private Map<String, Group> analyze(
      List<DiffFile> diffFiles, List<DiffHunk> allDiffHunks, Pair<String, String> dataPaths)
      throws ExecutionException, InterruptedException {
    // build the change semantic graph
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    Future<Graph<Node, Edge>> baseBuilder =
        executorService.submit(new GraphBuilder(dataPaths.getLeft(), diffFiles));
    Future<Graph<Node, Edge>> currentBuilder =
        executorService.submit(new GraphBuilder(dataPaths.getRight(), diffFiles));
    Graph<Node, Edge> baseGraph = baseBuilder.get();
    Graph<Node, Edge> currentGraph = currentBuilder.get();
    String baseDot = GraphExporter.exportAsDotWithType(baseGraph);
    String currentDot = GraphExporter.exportAsDotWithType(currentGraph);
    executorService.shutdown();

    // analyze the diff hunks
    GroupGenerator groupGenerator =
        new GroupGenerator(
            repoID, repoName, Config.THRESHOLD, diffFiles, allDiffHunks, baseGraph, currentGraph);
    groupGenerator.analyzeNonJavaFiles();
    groupGenerator.analyzeSoftLinks();
    groupGenerator.analyzeHardLinks();
    if(detectRefactorings){
      groupGenerator.analyzeRefactorings(tempDir);
    }
    groupGenerator.exportGroupingResults(tempDir);

    // visualize the diff hunk graph
    String diffGraphString =
        DiffGraphExporter.exportAsDotWithType(groupGenerator.getDiffHunkGraph());

    return groupGenerator.getGeneratedGroups();
  }
}
