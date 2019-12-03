package com.github.smartcommit.util;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;

import java.util.ArrayList;
import java.util.List;

/** A list of helper functions related with Git */
public interface GitService {
  /**
   * Get the diff files in the current working tree
   *
   * @return
   */
  ArrayList<DiffFile> getChangedFilesInWorkingTree(String repoDir);

  /**
   * Get the diff files between one commit and its previous commit
   *
   * @return
   */
  ArrayList<DiffFile> getChangedFilesAtCommit(String repoDir, String commitID);

  /**
   * Get the diff hunks in the current working tree
   *
   * @param repoPath
   * @return
   */
  List<DiffHunk> getDiffHunksInWorkingTree(String repoPath,  List<DiffFile> diffFiles);

  /**
   * Get the diff hunks between one commit and its previous commit
   *
   * @param repoPath
   * @param commitID
   * @return
   */
  List<DiffHunk> getDiffHunksAtCommit(String repoPath, String commitID,  List<DiffFile> diffFiles);

  /**
   * Get the file content at HEAD
   *
   * @param relativePath
   * @return
   */
  String getContentAtHEAD(String repoDir, String relativePath);

  /**
   * Get the file content at one specific commit
   *
   * @param relativePath
   * @returnØØ
   */
  String getContentAtCommit(String repoDir, String relativePath, String commitID);
}
