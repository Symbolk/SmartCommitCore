package com.github.smartcommit.util;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** Implementation of helper functions based on jGit (the java implementation of Git). */
public class GitServiceJGit implements GitService {
  @Override
  public ArrayList<DiffFile> getChangedFilesInWorkingTree(String repoPath) {
    return null;
  }

  @Override
  public ArrayList<DiffFile> getChangedFilesAtCommit(String repoPath, String commitID) {
    return null;
  }

  @Override
  public List<DiffHunk> getDiffHunksInWorkingTree(String repoPath, List<DiffFile> diffFiles) {
    return null;
  }

  @Override
  public List<DiffHunk> getDiffHunksAtCommit(
      String repoPath, String commitID, List<DiffFile> diffFiles) {
    return null;
  }

  @Override
  public String getContentAtHEAD(Charset charset, String repoDir, String relativePath) {
    return null;
  }

  @Override
  public String getContentAtCommit(
      Charset charset, String repoDir, String relativePath, String commitID) {
    return null;
  }

  @Override
  public String getCommitterName(String repoDir, String commitID) {
    return null;
  }

  @Override
  public String getCommitterEmail(String repoDir, String commitID) {
    return null;
  }
}
