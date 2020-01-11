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
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  public SmartCommit(String repoID, String repoName, String repoPath, String tempDir) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.repoPath = repoPath;
    this.tempDir = tempDir;
    Utils.clearDir(tempDir);
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
    Map<String, String> fileIDToPathMap = dataCollector.collectDiffHunksWorking(diffFiles);

    return analyze(diffFiles, allDiffHunks, dataPaths);
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
        logger.info("Files are unchanged.");
        return null;
      }

      // 2. collect the data into temp dir
      DataCollector dataCollector = new DataCollector(repoName, tempDir);
      Pair<String, String> dataPaths = dataCollector.collectDiffFilesAtCommit(commitID, diffFiles);

      return analyze(diffFiles, allDiffHunks, dataPaths);
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
    groupGenerator.analyzeHardLinks();
    groupGenerator.analyzeSoftLinks();
    groupGenerator.analyzeRemainingDiffHunks();
    groupGenerator.exportGroupingResults(tempDir);

    // get the diff hunk graph
    String diffGraphString =
        DiffGraphExporter.exportAsDotWithType(groupGenerator.getDiffHunkGraph());

    if (groupGenerator.getAlreadyGrouped().size() != allDiffHunks.size()) {
      logger.error("Incorrect diff hunk nums!");
    }
    return groupGenerator.getGeneratedGroups();
  }
}
