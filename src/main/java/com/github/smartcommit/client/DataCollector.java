package com.github.smartcommit.client;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;

import java.io.File;
import java.util.ArrayList;

/** Collect and write the diff file content into temp folders */
public class DataCollector {
  public static void main(String[] args) {
    String REPO_NAME = "nomulus";
    String REPO_PATH = "/Users/symbolk/coding/data/" + REPO_NAME;
    String DATA_DIR = "/Users/symbolk/coding/data/temp";
    String COMMIT_ID = "906b054f4b7a2e38681fd03282996955406afd65";
    GitService gitService = new GitServiceCGit();

    ArrayList<DiffFile> filePairs = gitService.getChangedFilesAtCommit(REPO_PATH, COMMIT_ID);
    // write old/new content to disk
    for (DiffFile filePair : filePairs) {
      // currently only collect MODIFIED Java files
      if (filePair.getBaseRelativePath().endsWith(".java")
          && filePair.getStatus().equals(FileStatus.MODIFIED)) {
        String dir =
            DATA_DIR + File.separator + REPO_NAME + File.separator + COMMIT_ID + File.separator;
        String aPath = dir + "a" + File.separator + filePair.getBaseRelativePath();
        String bPath = dir + "b" + File.separator + filePair.getCurrentRelativePath();
        boolean aOk = Utils.writeContentToPath(aPath, filePair.getBaseContent());
        boolean bOk = Utils.writeContentToPath(bPath, filePair.getCurrentContent());
        if (!(aOk && bOk)) {
          System.out.println("Error with: " + filePair.getBaseRelativePath());
        } else {
          System.out.println(aPath);
          System.out.println(bPath);
          }
      }
    }
  }
}
