package com.github.smartcommit.core;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;

public class RepoAnalyzer {

  private static final Logger logger = LoggerFactory.getLogger(RepoAnalyzer.class);

  private String repoName;
  private String repoPath;
  private List<DiffFile> diffFiles;
  private List<DiffHunk> diffHunks;

  public RepoAnalyzer(String repoName, String repoPath) {
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
    return diffFiles;
  }
}
