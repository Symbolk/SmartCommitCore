package com.github.smartcommit.client;

import com.github.smartcommit.core.GraphBuilder;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.io.GraphExporter;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
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

      // 5. generate diff hunk groups according to user-input threshold
      Map<String, Group> generatedGroups = new HashMap<>();

      // put the non-java files in the first group
      List<String> nonJavaDiffHunks = new ArrayList<>();
      for (DiffFile diffFile : repoAnalyzer.getDiffFiles()) {
        if (!diffFile.getFileType().equals(FileType.JAVA)) {
          String diffHunkID = diffFile.getFileID() + ":" + diffFile.getFileID();
          nonJavaDiffHunks.add(diffHunkID);
        }
      }
      Group nonJavaGroup = new Group(REPO_ID, REPO_NAME, Utils.generateUUID(), nonJavaDiffHunks);
      generatedGroups.put("group0", nonJavaGroup);

      // save to disk
      Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
      for (Map.Entry<String, Group> entry : generatedGroups.entrySet()) {
        Utils.writeStringToFile(
            gson.toJson(entry.getValue()),
            TEMP_DIR
                + File.separator
                + "generated_groups"
                + File.separator
                + entry.getKey()
                + ".json");
      }

      // 6. commit
      Map<String, DiffFile> idToDiffFileMap = repoAnalyzer.getIdToDiffFileMap();
      Map<String, DiffHunk> idToDiffHunkMap = repoAnalyzer.getIdToDiffHunkMap();

      executorService.shutdown();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
