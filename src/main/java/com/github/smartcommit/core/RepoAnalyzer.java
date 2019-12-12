package com.github.smartcommit.core;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

// import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;

public class RepoAnalyzer {

  private static final Logger logger = LoggerFactory.getLogger(RepoAnalyzer.class);

  private String repoPath;
  private String commitID;
  private List<DiffFile> diffFiles;
  private List<DiffHunk> diffHunks;

  public RepoAnalyzer(String repoPath) {
    this.repoPath = repoPath;
  }

  public RepoAnalyzer(String repoPath, String commitID) {
    this.repoPath = repoPath;
    this.commitID = commitID;
  }

  public String getRepoPath() {
    return repoPath;
  }

  public String getCommitID() {
    return commitID;
  }

  public List<DiffFile> getDiffFiles() {
    return diffFiles;
  }

  public void setDiffFiles(List<DiffFile> diffFiles) {
    this.diffFiles = diffFiles;
  }

  public List<DiffHunk> getDiffHunks() {
    return diffHunks;
  }

  public void setDiffHunks(List<DiffHunk> diffHunks) {
    this.diffHunks = diffHunks;
  }

  /**
   * Filter the diff hunks within the given diff file
   *
   * @param diffFile
   * @param diffHunks
   * @return
   */
  public List<DiffHunk> getDiffHunksInFile(DiffFile diffFile, List<DiffHunk> diffHunks) {
    String baseRelativePath = diffFile.getBaseRelativePath();
    String currentRelativePath = diffFile.getCurrentRelativePath();

    List<DiffHunk> results =
        diffHunks.stream()
            .filter(
                diffHunk ->
                    diffHunk.getBaseHunk().getRelativeFilePath().contains(baseRelativePath)
                        && diffHunk
                            .getCurrentHunk()
                            .getRelativeFilePath()
                            .contains(currentRelativePath))
            .collect(Collectors.toList());
    return results;
  }
}
