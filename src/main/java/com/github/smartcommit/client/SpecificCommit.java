package com.github.smartcommit.client;

import com.github.smartcommit.core.GraphBuilder;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.io.GraphExporter;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
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

    try {
      // 1. analyze the repo
      RepoAnalyzer repoAnalyzer = new RepoAnalyzer(REPO_ID, REPO_NAME, REPO_PATH);
      List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(COMMIT_ID);

      // 2. collect the data into temp dir
      // (1) diff files
      DataCollector dataCollector = new DataCollector(REPO_NAME, TEMP_DIR);
      Pair<String, String> dataPaths = dataCollector.collectDiffFilesAtCommit(COMMIT_ID, diffFiles);
      //          Pair<String, String> dataPaths = dataCollector.collectDiffFilesWorking(diffFiles);
      // (2) file id mapping
      // (3) diff hunks
      Map<String, String> fileIDToPathMap = dataCollector.collectDiffHunksWorking(diffFiles);

      // 3. build the diff hunk graph
      ExecutorService executorService = Executors.newFixedThreadPool(1);
      //    Future<Graph<Node, Edge>> baseBuilder =
      //        executorService.submit(new GraphBuilder(dataPaths.getLeft(), diffFiles));
      Future<Graph<Node, Edge>> currentBuilder =
          executorService.submit(new GraphBuilder(dataPaths.getRight(), diffFiles));
      //      Graph<Node, Edge> baseGraph = baseBuilder.get();
      Graph<Node, Edge> currentGraph = currentBuilder.get();
      //      String graphDotString = GraphExporter.exportAsDotWithType(baseGraph);
      String graphDotString = GraphExporter.exportAsDotWithType(currentGraph);

      // 4. analyze the diff hunks

      // 5. generate diff hunk groups

      // 6. commit
      Map<String, DiffFile> idToDiffFileMap = repoAnalyzer.getIdToDiffFileMap();
      Map<String, DiffHunk> idToDiffHunkMap = repoAnalyzer.getIdToDiffHunkMap();

      executorService.shutdown();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
