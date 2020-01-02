package com.github.smartcommit.client;

import com.github.smartcommit.core.GraphBuilder;
import com.github.smartcommit.core.GroupGenerator;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.Utils;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Collect and write the diff file content into temp folders */
public class SpecificCommit {
  private static final Logger logger = LoggerFactory.getLogger(SpecificCommit.class);

  public static void main(String[] args) {

    String REPO_ID = Config.REPO_ID;
    String REPO_NAME = Config.REPO_NAME;
    String REPO_PATH = Config.REPO_PATH;
    String TEMP_DIR = Config.TEMP_DIR;
    String COMMIT_ID = Config.COMMIT_ID;

    Utils.clearDir(TEMP_DIR);

    try {
      // 1. analyze the repo

      RepoAnalyzer repoAnalyzer = new RepoAnalyzer(REPO_ID, REPO_NAME, REPO_PATH);
      List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(COMMIT_ID);

      // 2. collect the data into temp dir
      DataCollector dataCollector = new DataCollector(REPO_NAME, TEMP_DIR);
      Pair<String, String> dataPaths = dataCollector.collectDiffFilesAtCommit(COMMIT_ID, diffFiles);

      // 3. build the diff hunk graph
      ExecutorService executorService = Executors.newFixedThreadPool(2);
      Future<Graph<Node, Edge>> baseBuilder =
          executorService.submit(new GraphBuilder(dataPaths.getLeft(), diffFiles));
      Future<Graph<Node, Edge>> currentBuilder =
          executorService.submit(new GraphBuilder(dataPaths.getRight(), diffFiles));
      Graph<Node, Edge> baseGraph = baseBuilder.get();
      Graph<Node, Edge> currentGraph = currentBuilder.get();
      //      String baseDot = GraphExporter.exportAsDotWithType(baseGraph);
      //      String currentDot = GraphExporter.exportAsDotWithType(currentGraph);
      executorService.shutdown();

      // 4. analyze the diff hunks to generate groups
      GroupGenerator groupGenerator =
          new GroupGenerator(
              REPO_ID, REPO_NAME, diffFiles, repoAnalyzer.getDiffHunks(), baseGraph, currentGraph);
      groupGenerator.analyzeNonJavaFiles();
      groupGenerator.analyzeHardLinks();
      groupGenerator.analyzeSoftLinks();
      groupGenerator.exportGroupingResults(TEMP_DIR);

      // 5. commit
      Map<String, DiffFile> idToDiffFileMap = repoAnalyzer.getIdToDiffFileMap();
      Map<String, DiffHunk> idToDiffHunkMap = repoAnalyzer.getIdToDiffHunkMap();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
