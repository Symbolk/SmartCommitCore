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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
    Utils.runSystemCommand(repoPath, StandardCharsets.UTF_8, "git", "reset", "HEAD", ".");

    ArrayList<DiffFile> diffFileList = new ArrayList<>();
    // run git status --porcelain to get changeset
    String output =
        Utils.runSystemCommand(
            repoPath, StandardCharsets.UTF_8, "git", "status", "--porcelain", "-uall");
    // early return
    if (output.isEmpty()) {
      // working tree clean
      return new ArrayList<>();
    }
    // ! use an independent incremental index to avoid index jump in case of invalid status output
    // only increment index when creating new diff file
    int fileIndex = 0;

    String[] lines = output.split("\\r?\\n");
    for (int i = 0; i < lines.length; i++) {
      String[] temp = lines[i].trim().split("\\s+");
      String symbol = temp[0];
      String relativePath = temp[1];
      FileType fileType = Utils.checkFileType(relativePath);
      String absolutePath = repoPath + File.separator + relativePath;
      FileStatus status = Utils.convertSymbolToStatus(symbol);
      DiffFile DiffFile = null;
      Charset charset = StandardCharsets.UTF_8;
      switch (status) {
        case MODIFIED:
          charset = Utils.detectCharset(absolutePath);
          DiffFile =
              new DiffFile(
                  fileIndex++,
                  status,
                  fileType,
                  charset,
                  relativePath,
                  relativePath,
                  getContentAtHEAD(charset, repoPath, relativePath),
                  Utils.readFileToString(absolutePath));
          break;
        case ADDED:
        case UNTRACKED:
          charset = Utils.detectCharset(absolutePath);
          DiffFile =
              new DiffFile(
                  fileIndex++,
                  status,
                  fileType,
                  charset,
                  "",
                  relativePath,
                  "",
                  Utils.readFileToString(absolutePath));
          break;
        case DELETED:
          DiffFile =
              new DiffFile(
                  fileIndex++,
                  status,
                  fileType,
                  StandardCharsets.UTF_8,
                  relativePath,
                  "",
                  getContentAtHEAD(charset, repoPath, relativePath),
                  "");
          break;
        case RENAMED:
        case COPIED:
          if (temp.length == 4) {
            // C/R aaa -> bbb
            String oldPath = temp[1];
            String newPath = temp[3];
            String newAbsPath = repoPath + File.separator + newPath;
            charset = Utils.detectCharset(absolutePath);
            DiffFile =
                new DiffFile(
                    fileIndex++,
                    status,
                    fileType,
                    charset,
                    oldPath,
                    newPath,
                    getContentAtHEAD(charset, repoPath, oldPath),
                    Utils.readFileToString(newAbsPath));
          } else if (temp.length == 3) {
            // CXX/RXX aaa bbb
            String oldPath = temp[1];
            String newPath = temp[2];
            String newAbsPath = repoPath + File.separator + newPath;
            charset = Utils.detectCharset(absolutePath);
            DiffFile =
                new DiffFile(
                    fileIndex++,
                    status,
                    fileType,
                    charset,
                    oldPath,
                    newPath,
                    getContentAtHEAD(charset, repoPath, oldPath),
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
    // assert: diffFileList.size() == fileIndex + 1
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
        Utils.runSystemCommand(
            repoPath,
            StandardCharsets.UTF_8,
            "git",
            "diff",
            "--name-status",
            commitID + "~",
            commitID);
    // early return
    if (output.trim().isEmpty()) {
      return new ArrayList<>();
    }
    ArrayList<DiffFile> diffFileList = new ArrayList<>();
    String[] lines = output.split("\\r?\\n");
    // ! use an independent incremental index to avoid index jump in case of invalid status output
    // only increment index when creating new diff file
    int fileIndex = 0;
    for (int i = 0; i < lines.length; i++) {
      String[] temp = lines[i].trim().split("\\s+");
      String symbol = temp[0];
      String relativePath = temp[1];
      FileType fileType = Utils.checkFileType(relativePath);
      //                        String absolutePath = repoDir + File.separator + relativePath;
      FileStatus status = Utils.convertSymbolToStatus(symbol);
      DiffFile diffFile = null;
      Charset charset = StandardCharsets.UTF_8;
      switch (status) {
        case MODIFIED:
          diffFile =
              new DiffFile(
                  fileIndex++,
                  status,
                  fileType,
                  charset,
                  relativePath,
                  relativePath,
                  getContentAtCommit(charset, repoPath, relativePath, commitID + "~"),
                  getContentAtCommit(charset, repoPath, relativePath, commitID));
          break;
        case ADDED:
        case UNTRACKED:
          diffFile =
              new DiffFile(
                  fileIndex++,
                  status,
                  fileType,
                  charset,
                  "",
                  relativePath,
                  "",
                  getContentAtCommit(charset, repoPath, relativePath, commitID));
          break;
        case DELETED:
          diffFile =
              new DiffFile(
                  fileIndex++,
                  status,
                  fileType,
                  charset,
                  relativePath,
                  "",
                  getContentAtCommit(charset, repoPath, relativePath, commitID + "~"),
                  "");
          break;
        case RENAMED:
        case COPIED:
          if (temp.length == 4) {
            // C/R aaa -> bbb
            String oldPath = temp[1];
            String newPath = temp[3];
            diffFile =
                new DiffFile(
                    fileIndex++,
                    status,
                    fileType,
                    charset,
                    oldPath,
                    newPath,
                    getContentAtCommit(charset, repoPath, oldPath, commitID + "~"),
                    getContentAtCommit(charset, repoPath, newPath, commitID));
          } else if (temp.length == 3) {
            // CXX/RXX aaa bbb
            String oldPath = temp[1];
            String newPath = temp[2];
            diffFile =
                new DiffFile(
                    fileIndex++,
                    status,
                    fileType,
                    charset,
                    oldPath,
                    newPath,
                    getContentAtCommit(charset, repoPath, oldPath, commitID + "~"),
                    getContentAtCommit(charset, repoPath, newPath, commitID));
          }
          break;
        default:
          break;
      }
      if (diffFile != null) {
        diffFileList.add(diffFile);
      }
    }
    // assert: diffFileList.size() == fileIndex + 1
    return diffFileList;
  }

  @Override
  public List<DiffHunk> getDiffHunksInWorkingTree(String repoPath, List<DiffFile> diffFiles) {
    // unstage the staged files first
    //    Utils.runSystemCommand(repoPath, "git", "reset", "--mixed");
    Utils.runSystemCommand(repoPath, StandardCharsets.UTF_8, "git", "reset", "HEAD", ".");
    // git diff + git diff --cached/staged == git diff HEAD (show all the changes since last commit
    // String diffOutput = Utils.runSystemCommand(repoPath, "git", "diff", "HEAD", "-U0");
    StringBuilder diffOutput = new StringBuilder();
    for (DiffFile diffFile : diffFiles) {
      if (null != diffFile.getBaseRelativePath() && !"".equals(diffFile.getBaseRelativePath())) {
        diffOutput.append(
            Utils.runSystemCommand(
                repoPath,
                diffFile.getCharset(),
                "git",
                "diff",
                "-U0",
                "--",
                diffFile.getBaseRelativePath()));
      }
    }
    List<Diff> diffs = new ArrayList<>();
    if (!diffOutput.toString().trim().isEmpty()) {
      // with -U0 (no context lines), the generated patch cannot be applied successfully
      DiffParser parser = new UnifiedDiffParser();
      diffs = parser.parse(new ByteArrayInputStream(diffOutput.toString().getBytes()));
    }

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
        if (removeVersionLabel(baseFilePath).equals(diffFile.getBaseRelativePath())
            && removeVersionLabel(currentFilePath).equals(diffFile.getCurrentRelativePath())) {
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
        Utils.runSystemCommand(
            repoPath, StandardCharsets.UTF_8, "git", "diff", "-U0", "-w", commitID + "~", commitID);
    List<Diff> diffs = new ArrayList<>();
    if (!diffOutput.trim().isEmpty()) {
      // with -U0 (no context lines), the generated patch cannot be applied successfully
      DiffParser parser = new UnifiedDiffParser();
      diffs = parser.parse(new ByteArrayInputStream(diffOutput.getBytes()));
    }

    return generateDiffHunks(diffs, diffFiles);
  }

  /**
   * Get the file content at HEAD
   *
   * @param relativePath
   * @return
   */
  @Override
  public String getContentAtHEAD(Charset charset, String repoDir, String relativePath) {
    return Utils.runSystemCommand(repoDir, charset, "git", "show", "HEAD:" + relativePath);
  }

  /**
   * Get the file content at one specific commit
   *
   * @param relativePath
   * @returnØØ
   */
  @Override
  public String getContentAtCommit(
      Charset charset, String repoDir, String relativePath, String commitID) {
    return Utils.runSystemCommand(repoDir, charset, "git", "show", commitID + ":" + relativePath);
  }

  /**
   * Remove the "a/" or "b/" at the beginning of the path printed in Git
   *
   * @return
   */
  private String removeVersionLabel(String gitFilePath) {
    String trimmedPath = gitFilePath.trim();
    if (trimmedPath.startsWith("a/")) {
      return gitFilePath.replaceFirst("a/", "");
    }
    if (trimmedPath.startsWith("b/")) {
      return gitFilePath.replaceFirst("b/", "");
    }
    if (trimmedPath.equals("/dev/null")) {
      return "";
    }
    return gitFilePath;
  }

  /**
   * Make the working dir clean by dropping all the changes (which are backed up in tempDir/current)
   *
   * @param repoPath
   */
  public boolean clearWorkingTree(String repoPath) {
    Utils.runSystemCommand(repoPath, StandardCharsets.UTF_8, "git", "reset", "--hard");
    String status =
        Utils.runSystemCommand(
            repoPath, StandardCharsets.UTF_8, "git", "status", "--porcelain", "-uall");
    // working tree clean if empty
    return status.isEmpty();
  }

  @Override
  public String getCommitterName(String repoDir, String commitID) {
    // git show HEAD | grep Author
    // git log -1 --format='%an' HASH
    // git show -s --format='%an' HASH
    return Utils.runSystemCommand(
            repoDir, StandardCharsets.UTF_8, "git", "show", "-s", "--format='%an'", commitID)
        .trim()
        .replaceAll("'", "");
  }

  @Override
  public String getCommitterEmail(String repoDir, String commitID) {
    // git log -1 --format='%ae' HASH
    // git show -s --format='%ae' HASH
    return Utils.runSystemCommand(
            repoDir, StandardCharsets.UTF_8, "git", "show", "-s", "--format='%ae'", commitID)
        .trim()
        .replaceAll("'", "");
  }
}
