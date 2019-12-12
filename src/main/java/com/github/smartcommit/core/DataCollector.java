package com.github.smartcommit.core;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import org.jgrapht.Graph;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Collect and write the diff file content into temp folders */
public class DataCollector {
  public static void main(String[] args) {
    String REPO_NAME = "nomulus";
    String REPO_PATH = "/Users/symbolk/coding/data/" + REPO_NAME;
    String TEMP_DIR = "/Users/symbolk/coding/data/temp"; // temp folder to collect diff files
    String COMMIT_ID = "906b054f4b7a2e38681fd03282996955406afd65";
    GitService gitService = new GitServiceCGit();
    String baseDir = TEMP_DIR + File.separator + REPO_NAME + File.separator + COMMIT_ID + File.separator + "a" + File.separator;
    String currentDir = TEMP_DIR + File.separator + REPO_NAME + File.separator + COMMIT_ID + File.separator + "b" + File.separator;

    ArrayList<DiffFile> filePairs = gitService.getChangedFilesAtCommit(REPO_PATH, COMMIT_ID);
    // collect the diff files into the data dir
    for (DiffFile filePair : filePairs) {
      // currently only collect MODIFIED Java files
      if (filePair.getBaseRelativePath().endsWith(".java")
          && filePair.getStatus().equals(FileStatus.MODIFIED)) {

        String aPath = baseDir + filePair.getBaseRelativePath();
        String bPath = currentDir + filePair.getCurrentRelativePath();
        boolean aOk = Utils.writeContentToPath(aPath, filePair.getBaseContent());
        boolean bOk = Utils.writeContentToPath(bPath, filePair.getCurrentContent());
        if (!(aOk && bOk)) {
          System.out.println("Error with: " + filePair.getBaseRelativePath());
        } else {
          System.out.println(aPath);
        }
      }
    }

    // build the graph
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    Future<Graph<Node, Edge>> baseBuilder =executorService.submit(new GraphBuilder(baseDir));
//    Future<Graph<Node, Edge>> currentBuilder =executorService.submit(new GraphBuilder(currentDir));
    try{
      Graph<Node, Edge> baseGraph = baseBuilder.get();
//      Graph<Node, Edge> currentGraph = currentBuilder.get();

    }catch (Exception e){
      e.printStackTrace();
    }

    executorService.shutdown();
  }
}
