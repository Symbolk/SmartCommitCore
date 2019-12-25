package com.github.smartcommit.client;

import com.github.smartcommit.core.GraphBuilder;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Collect and write the diff file content into temp folders */
public class MergeBot {
  private static final Logger logger = LoggerFactory.getLogger(MergeBot.class);

  public static void main(String[] args) {

    String REPO_NAME = Config.REPO_NAME;
    String REPO_PATH = Config.REPO_PATH;
    String TEMP_DIR = Config.TEMP_DIR; // temp folder to collect diff files
    String COMMIT_ID = Config.COMMIT_ID;

    DataCollector dataCollector = new DataCollector(REPO_NAME, REPO_PATH, TEMP_DIR);
    int count = dataCollector.collectDiffFilesAtCommit(COMMIT_ID);
    System.out.println("Java Files: " + count);

    // build the graph
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    Future<Graph<Node, Edge>> baseBuilder =
        executorService.submit(new GraphBuilder(baseDir, diffFiles));
    Future<Graph<Node, Edge>> currentBuilder =
        executorService.submit(new GraphBuilder(currentDir, diffFiles));
    try {
      Graph<Node, Edge> baseGraph = baseBuilder.get();
      //      Graph<Node, Edge> currentGraph = currentBuilder.get();
      //      String graphDotString = GraphExporter.exportAsDotWithType(baseGraph);
    } catch (Exception e) {
      e.printStackTrace();
    }

    executorService.shutdown();
  }
}
