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
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.jgrapht.Graph;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WorkingTree {

  public static void main(String[] args) {

    String REPO_ID = Config.REPO_ID;
    String REPO_NAME = Config.REPO_NAME;
    String REPO_PATH = Config.REPO_PATH;
    String TEMP_DIR = Config.TEMP_DIR;

    Utils.clearDir(TEMP_DIR);
    // use basic configuration when packaging
    BasicConfigurator.configure();
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);

    try {
      // 1. analyze the repo
      RepoAnalyzer repoAnalyzer = new RepoAnalyzer(REPO_ID, REPO_NAME, REPO_PATH);
      List<DiffFile> diffFiles = repoAnalyzer.analyzeWorkingTree();

      // 2. collect the data into temp dir
      // (1) diff files
      DataCollector dataCollector = new DataCollector(REPO_NAME, TEMP_DIR);
      Pair<String, String> dataPaths = dataCollector.collectDiffFilesWorking(diffFiles);
      // (2) file id mapping
      // (3) diff hunks
      Map<String, String> fileIDToPathMap = dataCollector.collectDiffHunksWorking(diffFiles);

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

      // 4. analyze the diff hunks
      GroupGenerator groupGenerator =
          new GroupGenerator(
              REPO_ID,
              REPO_NAME,
              Config.THRESHOLD,
              diffFiles,
              repoAnalyzer.getDiffHunks(),
              baseGraph,
              currentGraph);
      groupGenerator.analyzeNonJavaFiles();
      groupGenerator.analyzeHardLinks();
      groupGenerator.analyzeSoftLinks();
      groupGenerator.analyzeRemainingDiffHunks();
      groupGenerator.exportGroupingResults(TEMP_DIR);

      String diffGraphString =
          DiffGraphExporter.exportAsDotWithType(groupGenerator.getDiffHunkGraph());
      // 6. commit
      Map<String, DiffFile> idToDiffFileMap = repoAnalyzer.getIdToDiffFileMap();
      Map<String, DiffHunk> idToDiffHunkMap = repoAnalyzer.getIdToDiffHunkMap();

      executorService.shutdown();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
