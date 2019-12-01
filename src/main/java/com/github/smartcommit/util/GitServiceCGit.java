package com.github.smartcommit.util;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffFileStatus;
import com.github.smartcommit.model.DiffHunk;
import io.reflectoring.diffparser.api.DiffParser;
import io.reflectoring.diffparser.api.UnifiedDiffParser;
import io.reflectoring.diffparser.api.model.Diff;
import io.reflectoring.diffparser.api.model.Hunk;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.github.smartcommit.util.Utils.readFileToString;

/** Implementation of helper functions based on the output of git commands. */
public class GitServiceCGit implements GitService {
  /**
   * Get the diff files in the current working tree
   *
   * @return
   */
  @Override
  public ArrayList<DiffFile> getChangedFilesInWorkingTree(String repoDir) {
    // run git status --porcelain to get changeset
    String output = Utils.runSystemCommand(repoDir, "git", "status", "--porcelain");
    ArrayList<DiffFile> DiffFileList = new ArrayList<>();
    String lines[] = output.split("\\r?\\n");
    for (int i = 0; i < lines.length; i++) {
      String temp[] = lines[i].trim().split("\\s+");
      String symbol = temp[0];
      String relativePath = temp[1];
      String absolutePath = repoDir + File.separator + relativePath;
      DiffFileStatus status = Utils.convertSymbolToStatus(symbol);
      DiffFile DiffFile = null;
      switch (status) {
        case MODIFIED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  relativePath,
                  relativePath,
                  getContentAtHEAD(repoDir, relativePath),
                  readFileToString(absolutePath));
          break;
        case ADDED:
        case UNTRACKED:
          DiffFile = new DiffFile(i, status, "", relativePath, "", readFileToString(absolutePath));
          break;
        case DELETED:
          DiffFile =
              new DiffFile(
                  i, status, relativePath, "", getContentAtHEAD(repoDir, relativePath), "");
          break;
        case RENAMED:
        case COPIED:
          if (temp.length == 4) {
            String oldPath = temp[1];
            String newPath = temp[3];
            String newAbsPath = repoDir + File.separator + temp[3];
            DiffFile =
                new DiffFile(
                    i,
                    status,
                    oldPath,
                    newPath,
                    getContentAtHEAD(repoDir, oldPath),
                    readFileToString(newAbsPath));
          }
          break;
        default:
          break;
      }
      if (DiffFile != null) {
        DiffFileList.add(DiffFile);
      }
    }
    return DiffFileList;
  }

  /**
   * Get the diff files between one commit and its previous commit
   *
   * @return
   */
  @Override
  public ArrayList<DiffFile> getChangedFilesAtCommit(String repoDir, String commitID) {
    // git diff <start_commit> <end_commit>
    // on Windows the ~ character must be used instead of ^
    String output =
        Utils.runSystemCommand(repoDir, "git", "diff", "--name-status", commitID + "~", commitID);
    ArrayList<DiffFile> DiffFileList = new ArrayList<>();
    String lines[] = output.split("\\r?\\n");
    for (int i = 0; i < lines.length; i++) {
      String temp[] = lines[i].trim().split("\\s+");
      String symbol = temp[0];
      String relativePath = temp[1];
      //            String absolutePath = repoDir + File.separator + relativePath;
      DiffFileStatus status = Utils.convertSymbolToStatus(symbol);
      DiffFile DiffFile = null;
      switch (status) {
        case MODIFIED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  relativePath,
                  relativePath,
                  getContentAtCommit(repoDir, relativePath, commitID + "~"),
                  getContentAtCommit(repoDir, relativePath, commitID));
          break;
        case ADDED:
        case UNTRACKED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  "",
                  relativePath,
                  "",
                  getContentAtCommit(repoDir, relativePath, commitID));
          break;
        case DELETED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  relativePath,
                  "",
                  getContentAtCommit(repoDir, relativePath, commitID + "~"),
                  "");
          break;
        case RENAMED:
        case COPIED:
          if (temp.length == 4) {
            String oldPath = temp[1];
            String newPath = temp[3];
            DiffFile =
                new DiffFile(
                    i,
                    status,
                    oldPath,
                    newPath,
                    getContentAtCommit(repoDir, oldPath, commitID + "~"),
                    getContentAtCommit(repoDir, newPath, commitID));
          }
          break;
        default:
          break;
      }
      if (DiffFile != null) {
        DiffFileList.add(DiffFile);
      }
    }
    return DiffFileList;
  }

  @Override
  public List<DiffHunk> getDiffHunksInWorkingTree(String repoPath) {
    String diffOutput = Utils.runSystemCommand(repoPath, "git", "diff", "-U1");
    DiffParser parser = new UnifiedDiffParser();
    List<Diff> diffs = parser.parse(new ByteArrayInputStream(diffOutput.getBytes()));
    return generateDiffHunks(diffs);
  }

  /**
   * Generate diff hunks from diffs parsed from git-diff output
   *
   * @param diffs
   * @return
   */
  private List<DiffHunk> generateDiffHunks(List<Diff> diffs) {
    List<DiffHunk> allDiffHunks = new ArrayList<>();
    for (Diff diff : diffs) {
      // the index of the diff hunk in the current file diff, start from 1
      Integer index = 0;
      for (Hunk hunk : diff.getHunks()) {
        DiffHunk diffHunk = new DiffHunk();
        diffHunk.setIndex(index);
        diffHunk.setOldRelativePath(diff.getFromFileName());
        diffHunk.setNewRelativePath(diff.getToFileName());
        // [startLine, endLine]
        diffHunk.setOldStartLine(hunk.getFromFileRange().getLineStart() + 1);
        diffHunk.setOldEndLine(
            hunk.getFromFileRange().getLineStart() + hunk.getFromFileRange().getLineCount() - 2);
        diffHunk.setNewStartLine(hunk.getToFileRange().getLineStart() + 1);
        diffHunk.setNewEndLine(
            hunk.getToFileRange().getLineStart() + hunk.getToFileRange().getLineCount() - 2);
        allDiffHunks.add(diffHunk);
        index++;
      }
    }
    return allDiffHunks;
  }

  /**
   * Get the diff hunks between one commit and its previous commit
   *
   * @param repoPath
   * @param commitID
   * @return
   */
  @Override
  public List<DiffHunk> getDiffHunksAtCommit(String repoPath, String commitID) {
    // git diff <start_commit> <end_commit>
    // on Windows the ~ character must be used instead of ^
    String diffOutput =
        Utils.runSystemCommand(repoPath, "git", "diff", "-U1", commitID + "~", commitID);
    DiffParser parser = new UnifiedDiffParser();
    // TODO fix the bug within the library when parsing diff with only added lines with -U0 or
    // default -U3
    List<Diff> diffs = parser.parse(new ByteArrayInputStream(diffOutput.getBytes()));
    return generateDiffHunks(diffs);
  }

  /**
   * Get the file content at HEAD
   *
   * @param relativePath
   * @return
   */
  @Override
  public String getContentAtHEAD(String repoDir, String relativePath) {
    String output = Utils.runSystemCommand(repoDir, "git", "show", "HEAD:" + relativePath);
    if (output != null) {
      return output;
    } else {
      return "";
    }
  }

  /**
   * Get the file content at one specific commit
   *
   * @param relativePath
   * @returnØØ
   */
  @Override
  public String getContentAtCommit(String repoDir, String relativePath, String commitID) {
    String output = Utils.runSystemCommand(repoDir, "git", "show", commitID + ":" + relativePath);
    if (output != null) {
      return output;
    } else {
      return "";
    }
  }
}
