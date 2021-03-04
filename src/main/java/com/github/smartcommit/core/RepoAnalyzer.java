package com.github.smartcommit.core;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import org.apache.log4j.Logger;

import java.util.*;

// import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;

public class RepoAnalyzer {

  private static final Logger logger = Logger.getLogger(RepoAnalyzer.class);

  private String repoID;
  private String repoName;
  private String repoPath;
  private List<DiffFile> diffFiles;
  private List<DiffHunk> diffHunks;
  private Map<String, DiffFile> idToDiffFileMap;
  private Map<String, DiffHunk> idToDiffHunkMap;

  public RepoAnalyzer(String repoID, String repoName, String repoPath) {
    this.repoID = repoID;
    this.repoName = repoName;
    this.repoPath = repoPath;
    this.diffFiles = new ArrayList<>();
    this.diffHunks = new ArrayList<>();
    this.idToDiffFileMap = new HashMap<>();
    this.idToDiffHunkMap = new HashMap<>();
  }

  public String getRepoPath() {
    return repoPath;
  }

  public List<DiffFile> getDiffFiles() {
    return diffFiles;
  }

  public List<DiffHunk> getDiffHunks() {
    return diffHunks;
  }

  /** Analyze the current working tree to cache temp data */
  public List<DiffFile> analyzeWorkingTree() {
    // analyze the diff files and hunks
    GitService gitService = new GitServiceCGit();
    ArrayList<DiffFile> diffFiles = gitService.getChangedFilesInWorkingTree(this.repoPath);
    if (!diffFiles.isEmpty()) {
      gitService.getDiffHunksInWorkingTree(this.repoPath, diffFiles);
      this.diffFiles = diffFiles;
      this.idToDiffFileMap = generateIDToDiffFileMap();
    }
    return diffFiles;
  }

  /**
   * Analyze one specific commit to cache temp data
   *
   * @param commitID
   */
  public List<DiffFile> analyzeCommit(String commitID) {
    // analyze the diff files and hunks
    GitService gitService = new GitServiceCGit();
    ArrayList<DiffFile> diffFiles = gitService.getChangedFilesAtCommit(this.repoPath, commitID);
    if (!diffFiles.isEmpty()) {
      gitService.getDiffHunksAtCommit(this.repoPath, commitID, diffFiles);
      this.diffFiles = diffFiles;
      this.idToDiffFileMap = generateIDToDiffFileMap();
    }
    return diffFiles;
  }

  /**
   * Generate fileID:diffFile map (for commit stage)
   *
   * @return
   */
  private Map<String, DiffFile> generateIDToDiffFileMap() {
    Map<String, DiffFile> idToDiffFileMap = new HashMap<>();
    // map for all diff hunks inside the repo
    for (DiffFile diffFile : diffFiles) {
      String fileID = Utils.generateUUID();
      idToDiffFileMap.put(fileID, diffFile);
      diffFile.setRepoID(repoID);
      diffFile.setRepoName(repoName);
      diffFile.setFileID(fileID);
      // map for diff hunks inside a file
      Map<String, DiffHunk> diffHunksMap = new HashMap<>();
      for (DiffHunk diffHunk : diffFile.getDiffHunks()) {
        String diffHunkID = Utils.generateUUID();
        // for entire file change as a whole diff hunk, fileID == diffHunkID
        if (diffFile.getStatus().equals(FileStatus.UNTRACKED)
            || diffFile.getStatus().equals(FileStatus.ADDED)
            || diffFile.getStatus().equals(FileStatus.DELETED)) {
          diffHunkID = fileID;
        }
        diffHunk.setRepoID(repoID);
        diffHunk.setRepoName(repoName);
        diffHunk.setFileID(fileID);
        diffHunk.setDiffHunkID(diffHunkID);
        this.diffHunks.add(diffHunk);
        diffHunksMap.put(diffHunkID, diffHunk);
        this.idToDiffHunkMap.put(diffHunkID, diffHunk);
      }
      diffFile.setDiffHunksMap(diffHunksMap);
    }
    return idToDiffFileMap;
  }

  public String getRepoName() {
    return repoName;
  }

  public Map<String, DiffFile> getIdToDiffFileMap() {
    return idToDiffFileMap;
  }

  public Map<String, DiffHunk> getIdToDiffHunkMap() {
    return idToDiffHunkMap;
  }
}
