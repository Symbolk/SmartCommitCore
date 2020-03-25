package com.github.smartcommit.util;

import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.*;
import com.github.smartcommit.util.diffparser.api.DiffParser;
import com.github.smartcommit.util.diffparser.api.UnifiedDiffParser;
import com.github.smartcommit.util.diffparser.api.model.Diff;
import com.github.smartcommit.util.diffparser.api.model.Hunk;
import com.github.smartcommit.util.diffparser.api.model.Line;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Implementation of helper functions based on the output of git commands. */
public class GitServiceCGit implements GitService {
  /**
   * Get the diff files in the current working tree
   *
   * @return
   */
  @Override
  public ArrayList<DiffFile> getChangedFilesInWorkingTree(String repoPath) {
    // unstage the staged files first
    //    Utils.runSystemCommand(repoPath, "git", "restore", "--staged", ".");
    Utils.runSystemCommand(repoPath, "git", "reset", "HEAD", ".");

    ArrayList<DiffFile> diffFileList = new ArrayList<>();
    // run git status --porcelain to get changeset
    String output = Utils.runSystemCommand(repoPath, "git", "status", "--porcelain", "-uall");
    if (output.isEmpty()) {
      // working tree clean
      return diffFileList;
    }

    String lines[] = output.split("\\r?\\n");
    for (int i = 0; i < lines.length; i++) {
      String temp[] = lines[i].trim().split("\\s+");
      String symbol = temp[0];
      String relativePath = temp[1];
      FileType fileType = Utils.checkFileType(relativePath);
      String absolutePath = repoPath + File.separator + relativePath;
      FileStatus status = Utils.convertSymbolToStatus(symbol);
      DiffFile DiffFile = null;
      switch (status) {
        case MODIFIED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  relativePath,
                  relativePath,
                  getContentAtHEAD(repoPath, relativePath),
                  Utils.readFileToString(absolutePath));
          break;
        case ADDED:
        case UNTRACKED:
          DiffFile =
              new DiffFile(
                  i, status, fileType, "", relativePath, "", Utils.readFileToString(absolutePath));
          break;
        case DELETED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  relativePath,
                  "",
                  getContentAtHEAD(repoPath, relativePath),
                  "");
          break;
        case RENAMED:
        case COPIED:
          if (temp.length == 4) {
            String oldPath = temp[1];
            String newPath = temp[3];
            String newAbsPath = repoPath + File.separator + temp[3];
            DiffFile =
                new DiffFile(
                    i,
                    status,
                    fileType,
                    oldPath,
                    newPath,
                    getContentAtHEAD(repoPath, oldPath),
                    Utils.readFileToString(newAbsPath));
          }
          break;
        default:
          break;
      }
      if (DiffFile != null) {
        diffFileList.add(DiffFile);
      }
    }
    return diffFileList;
  }

  /**
   * Get the diff files between one commit and its previous commit
   *
   * @return
   */
  @Override
  public ArrayList<DiffFile> getChangedFilesAtCommit(String repoPath, String commitID) {
    // git diff <start_commit> <end_commit>
    // on Windows the ~ character must be used instead of ^
    String output =
        Utils.runSystemCommand(repoPath, "git", "diff", "--name-status", commitID + "~", commitID);
    ArrayList<DiffFile> DiffFileList = new ArrayList<>();
    String lines[] = output.split("\\r?\\n");
    for (int i = 0; i < lines.length; i++) {
      String temp[] = lines[i].trim().split("\\s+");
      String symbol = temp[0];
      String relativePath = temp[1];
      FileType fileType = Utils.checkFileType(relativePath);
      //            String absolutePath = repoDir + File.separator + relativePath;
      FileStatus status = Utils.convertSymbolToStatus(symbol);
      DiffFile DiffFile = null;
      switch (status) {
        case MODIFIED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  relativePath,
                  relativePath,
                  getContentAtCommit(repoPath, relativePath, commitID + "~"),
                  getContentAtCommit(repoPath, relativePath, commitID));
          break;
        case ADDED:
        case UNTRACKED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  "",
                  relativePath,
                  "",
                  getContentAtCommit(repoPath, relativePath, commitID));
          break;
        case DELETED:
          DiffFile =
              new DiffFile(
                  i,
                  status,
                  fileType,
                  relativePath,
                  "",
                  getContentAtCommit(repoPath, relativePath, commitID + "~"),
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
                    fileType,
                    oldPath,
                    newPath,
                    getContentAtCommit(repoPath, oldPath, commitID + "~"),
                    getContentAtCommit(repoPath, newPath, commitID));
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
  public List<DiffHunk> getDiffHunksInWorkingTree(String repoPath, List<DiffFile> diffFiles) {
    // unstage the staged files first
    //    Utils.runSystemCommand(repoPath, "git", "reset", "--mixed");
    Utils.runSystemCommand(repoPath, "git", "reset", "HEAD", ".");
    // git diff + git diff --cached/staged == git diff HEAD (show all the changes since last commit
    String diffOutput = Utils.runSystemCommand(repoPath, "git", "diff", "HEAD", "-U0");
    // with -U0 (no context lines), the generated patch cannot be applied successfully
    DiffParser parser = new UnifiedDiffParser();
    List<Diff> diffs = parser.parse(new ByteArrayInputStream(diffOutput.getBytes()));
    return generateDiffHunks(diffs, diffFiles);
  }

  /**
   * Generate diff hunks from diffs parsed from git-diff output
   *
   * @param diffs
   * @return
   */
  private List<DiffHunk> generateDiffHunks(List<Diff> diffs, List<DiffFile> diffFiles) {
    List<DiffHunk> allDiffHunks = new ArrayList<>();
    // one file, one diff
    // UNTRACKED/ADDED files won't be shown in the diff
    for (DiffFile diffFile : diffFiles) {
      if (diffFile.getStatus().equals(FileStatus.ADDED)
          || diffFile.getStatus().equals(FileStatus.UNTRACKED)) {
        List<String> lines = Utils.convertStringToList(diffFile.getCurrentContent());
        DiffHunk diffHunk =
            new DiffHunk(
                0,
                Utils.checkFileType(diffFile.getCurrentRelativePath()),
                ChangeType.ADDED,
                new com.github.smartcommit.model.Hunk(
                    Version.BASE, "", 0, -1, ContentType.EMPTY, new ArrayList<>()),
                new com.github.smartcommit.model.Hunk(
                    Version.CURRENT,
                    diffFile.getCurrentRelativePath(),
                    1,
                    lines.size(),
                    Utils.checkContentType(lines),
                    lines),
                "Add File:" + diffFile.getCurrentRelativePath());
        diffHunk.addASTAction(
            new Action(Operation.ADD, "", "", "File", diffFile.getCurrentRelativePath()));

        // bidirectional binding
        diffHunk.setFileIndex(diffFile.getIndex());
        List<DiffHunk> diffHunksInFile = new ArrayList<>();
        diffHunksInFile.add(diffHunk);
        allDiffHunks.add(diffHunk);
        diffFile.setDiffHunks(diffHunksInFile);
      }
    }

    for (Diff diff : diffs) {
      // the hunkIndex of the diff hunk in the current file diff, start from 0
      Integer hunkIndex = 0;

      String baseFilePath = diff.getFromFileName();
      String currentFilePath = diff.getToFileName();

      List<String> headers = diff.getHeaderLines();
      headers.add("--- " + baseFilePath);
      headers.add("+++ " + currentFilePath);

      // currently we only process Java files
      FileType fileType =
          baseFilePath.contains("/dev/null")
              ? Utils.checkFileType(currentFilePath) // ADDED/UNTRACKED
              : Utils.checkFileType(baseFilePath);

      // collect and save diff hunks into diff files
      List<DiffHunk> diffHunksInFile = new ArrayList<>();
      for (Hunk hunk : diff.getHunks()) {
        List<List<String>> hunkLines = splitHunkLines(hunk.getLines());
        List<String> baseCodeLines = hunkLines.get(1);
        List<String> currentCodeLines = hunkLines.get(2);
        int leadingNeutral = hunkLines.get(0).size();
        int trailingNeutral = hunkLines.get(3).size();
        com.github.smartcommit.model.Hunk baseHunk =
            new com.github.smartcommit.model.Hunk(
                Version.BASE,
                removeVersionLabel(baseFilePath),
                // with -U0, leadingNeutral = 0 = trailingNeutral
                hunk.getFromFileRange().getLineStart() + leadingNeutral,
                hunk.getFromFileRange().getLineStart()
                    + leadingNeutral
                    + hunk.getFromFileRange().getLineCount()
                    - leadingNeutral
                    - trailingNeutral
                    - 1,
                Utils.checkContentType(baseCodeLines),
                baseCodeLines);
        com.github.smartcommit.model.Hunk currentHunk =
            new com.github.smartcommit.model.Hunk(
                Version.CURRENT,
                removeVersionLabel(currentFilePath),
                hunk.getToFileRange().getLineStart() + leadingNeutral,
                hunk.getToFileRange().getLineStart()
                    + leadingNeutral
                    + hunk.getToFileRange().getLineCount()
                    - leadingNeutral
                    - trailingNeutral
                    - 1,
                Utils.checkContentType(currentCodeLines),
                currentCodeLines);
        ChangeType changeType = ChangeType.MODIFIED;
        if (baseCodeLines.isEmpty()) {
          changeType = ChangeType.ADDED;
        }
        if (currentCodeLines.isEmpty()) {
          changeType = ChangeType.DELETED;
        }
        DiffHunk diffHunk = new DiffHunk(hunkIndex, fileType, changeType, baseHunk, currentHunk);
        diffHunk.setRawDiffs(hunk.getRawLines());
        diffHunksInFile.add(diffHunk);
        allDiffHunks.add(diffHunk);
        hunkIndex++;
      }

      // bidirectional binding
      for (DiffFile diffFile : diffFiles) {
        if (baseFilePath.contains(diffFile.getBaseRelativePath())
            && currentFilePath.contains(diffFile.getCurrentRelativePath())) {
          diffHunksInFile.forEach(diffHunk -> diffHunk.setFileIndex(diffFile.getIndex()));
          diffFile.setDiffHunks(diffHunksInFile);
          diffFile.setRawHeaders(headers);
        }
      }
    }
    return allDiffHunks;
  }

  /**
   * Split the raw hunk lines into 0 (leading neutral), 1 (from), 2 (to), 3 (trailing neural)
   *
   * @param lines
   * @return
   */
  private List<List<String>> splitHunkLines(List<Line> lines) {
    List<List<String>> result = new ArrayList<>();
    for (int i = 0; i < 4; ++i) {
      result.add(new ArrayList<>());
    }

    boolean trailing = false;
    // to handle case where two neighboring diff hunks are merged if the lines between them are less
    // than the -Ux
    boolean isLastLineNeutral = true;
    for (int i = 0; i < lines.size(); ++i) {
      Line line = lines.get(i);
      switch (line.getLineType()) {
        case NEUTRAL:
          boolean isNextLineNeutral = true;
          if (!isLastLineNeutral) {
            // check if the neutral lies between two non-netural lines
            if (i + 1 < lines.size()) {
              Line nextLine = lines.get(i + 1);
              isNextLineNeutral = nextLine.getLineType().equals(Line.LineType.NEUTRAL);
            }
          }
          if (!isLastLineNeutral && !isNextLineNeutral) {
            isLastLineNeutral = true;
            continue;
          } else {
            if (!line.getContent().trim().equals("\\ No newline at end of file")) {
              if (trailing) {
                result.get(3).add(line.getContent());
              } else {
                result.get(0).add(line.getContent());
              }
              isLastLineNeutral = true;
            }
          }
          break;
        case FROM:
          result.get(1).add(line.getContent());
          trailing = true;
          isLastLineNeutral = false;
          break;
        case TO:
          result.get(2).add(line.getContent());
          trailing = true;
          isLastLineNeutral = false;
          break;
      }
    }
    return result;
  }

  /**
   * Get the diff hunks between one commit and its previous commit
   *
   * @param repoPath
   * @param commitID
   * @return
   */
  @Override
  public List<DiffHunk> getDiffHunksAtCommit(
      String repoPath, String commitID, List<DiffFile> diffFiles) {
    // git diff <start_commit> <end_commit>
    // on Windows the ~ character must be used instead of ^
    String diffOutput =
        Utils.runSystemCommand(repoPath, "git", "diff", "-U0", "-w", commitID + "~", commitID);
    DiffParser parser = new UnifiedDiffParser();
    List<Diff> diffs = parser.parse(new ByteArrayInputStream(diffOutput.getBytes()));
    return generateDiffHunks(diffs, diffFiles);
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

  /**
   * Remove the "a/" or "b/" at the beginning of the path printed in Git
   *
   * @return
   */
  private String removeVersionLabel(String gitFilePath) {
    if (gitFilePath.trim().startsWith("a/")) {
      return gitFilePath.replaceFirst("a/", "");
    }
    if (gitFilePath.trim().startsWith("b/")) {
      return gitFilePath.replaceFirst("b/", "");
    }
    return gitFilePath;
  }

  /**
   * Make the working dir clean by dropping all the changes (which are backed up in tempDir/current)
   *
   * @param repoPath
   */
  public boolean clearWorkingTree(String repoPath) {
    Utils.runSystemCommand(repoPath, "git", "reset", "--hard");
    String status = Utils.runSystemCommand(repoPath, "git", "status", "--porcelain", "-uall");
    if (status.isEmpty()) {
      // working tree clean
      return true;
    } else {
      return false;
    }
  }

  @Override
  public String getCommitterName(String repoDir, String commitID) {
    // git show HEAD | grep Author
    // git log -1 --format='%an' HASH
    // git show -s --format='%an' HASH
    return Utils.runSystemCommand(repoDir, "git", "show", "-s", "--format='%an'", commitID)
        .trim()
        .replaceAll("'", "");
  }

  @Override
  public String getCommitterEmail(String repoDir, String commitID) {
    // git log -1 --format='%ae' HASH
    // git show -s --format='%ae' HASH
    return Utils.runSystemCommand(repoDir, "git", "show", "-s", "--format='%ae'", commitID)
        .trim()
        .replaceAll("'", "");
  }
}
