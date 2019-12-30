package com.github.smartcommit.core;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;

public class RepoAnalyzer {

  private static final Logger logger = LoggerFactory.getLogger(RepoAnalyzer.class);

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
    List<DiffHunk> allDiffHunks = gitService.getDiffHunksInWorkingTree(this.repoPath, diffFiles);
    this.diffFiles = diffFiles;
    this.diffHunks = allDiffHunks;
    this.idToDiffFileMap = generateIDToDiffFileMap();
    this.idToDiffHunkMap = generateIDToDiffHunkMap();
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
    List<DiffHunk> allDiffHunks =
        gitService.getDiffHunksAtCommit(this.repoPath, commitID, diffFiles);
    this.diffFiles = diffFiles;
    this.diffHunks = allDiffHunks;
    this.idToDiffFileMap = generateIDToDiffFileMap();
    this.idToDiffHunkMap = generateIDToDiffHunkMap();
    return diffFiles;
  }

  /**
   * Generate fileID:diffFile map (for commit stage)
   *
   * @return
   */
  private Map<String, DiffFile> generateIDToDiffFileMap() {
    Map<String, DiffFile> idToDiffFileMap = new HashMap<>();

    for (DiffFile diffFile : diffFiles) {
      String fileID = Utils.generateUUID();
      idToDiffFileMap.put(fileID, diffFile);
      diffFile.setRepoID(repoID);
      diffFile.setRepoName(repoName);
      diffFile.setFileID(fileID);
    }
    return idToDiffFileMap;
  }

  /**
   * Generate diffHunkID:diffHunk map (for commit stage)
   *
   * @return
   */
  private Map<String, DiffHunk> generateIDToDiffHunkMap() {
    Map<String, DiffHunk> idToDiffHunkMap = new HashMap<>();

    for (DiffHunk diffHunk : diffHunks) {
      String diffHunkID = Utils.generateUUID();
      idToDiffHunkMap.put(diffHunkID, diffHunk);
      diffHunk.setRepoID(repoID);
      diffHunk.setRepoName(repoName);
      diffHunk.setDiffHunkID(diffHunkID);
    }
    return idToDiffHunkMap;
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
