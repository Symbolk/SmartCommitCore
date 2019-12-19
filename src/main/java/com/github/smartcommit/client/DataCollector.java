package com.github.smartcommit.client;

import com.github.smartcommit.core.GraphBuilder;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Collect and write the diff file content into temp folders */
public class DataCollector {
  private static final Logger logger = LoggerFactory.getLogger(DataCollector.class);

  public static void main(String[] args) {

    String REPO_NAME = Config.REPO_NAME;
    String REPO_PATH = Config.REPO_PATH;
    String TEMP_DIR = Config.TEMP_DIR; // temp folder to collect diff files
    String COMMIT_ID = Config.COMMIT_ID;
    GitService gitService = new GitServiceCGit();
    String baseDir = TEMP_DIR + File.separator + REPO_NAME + File.separator + COMMIT_ID + File.separator + "a" + File.separator;
    String currentDir = TEMP_DIR + File.separator + REPO_NAME + File.separator + COMMIT_ID + File.separator + "b" + File.separator;

    ArrayList<DiffFile> diffFiles = gitService.getChangedFilesAtCommit(REPO_PATH, COMMIT_ID);
    gitService.getDiffHunksAtCommit(REPO_PATH, COMMIT_ID, diffFiles);
    // collect the diff files into the data dir
    int count = 0;
    for (DiffFile diffFile : diffFiles) {
      // currently only collect MODIFIED Java files
      if (diffFile.getFileType().equals(FileType.JAVA)
          && diffFile.getStatus().equals(FileStatus.MODIFIED)) {

        String aPath = baseDir + diffFile.getBaseRelativePath();
        String bPath = currentDir + diffFile.getCurrentRelativePath();
        boolean aOk = Utils.writeContentToPath(aPath, diffFile.getBaseContent());
        boolean bOk = Utils.writeContentToPath(bPath, diffFile.getCurrentContent());
        if (aOk && bOk) {
          count++;
        } else {
          logger.error("Error with: " + diffFile.getBaseRelativePath());
        }
      }
    }

    System.out.println("Java Files: " + count);

    // build the graph
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    Future<Graph<Node, Edge>> baseBuilder = executorService.submit(new GraphBuilder(baseDir, diffFiles));
    Future<Graph<Node, Edge>> currentBuilder =executorService.submit(new GraphBuilder(currentDir, diffFiles));
    try{
      Graph<Node, Edge> baseGraph = baseBuilder.get();
//      Graph<Node, Edge> currentGraph = currentBuilder.get();
//      String graphDotString = GraphExporter.exportAsDotWithType(baseGraph);
    }catch (Exception e){
      e.printStackTrace();
    }

    executorService.shutdown();
  }
}
