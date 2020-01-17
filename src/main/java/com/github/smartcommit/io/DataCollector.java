package com.github.smartcommit.io;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.ChangeType;
import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class DataCollector {
  private static final Logger logger = LoggerFactory.getLogger(DataCollector.class);

  private String repoName;
  private String tempDir;

  public DataCollector(String repoName, String tempDir) {
    this.repoName = repoName;
    this.tempDir = Utils.createDir(tempDir);
  }

  /**
   * Collect the base and current version of diff files
   *
   * @param commitID
   * @return
   */
  public Pair<String, String> collectDiffFilesAtCommit(String commitID, List<DiffFile> diffFiles) {
    String baseDir = tempDir + File.separator + Version.BASE.asString() + File.separator;
    String currentDir = tempDir + File.separator + Version.CURRENT.asString() + File.separator;

    collect(baseDir, currentDir, diffFiles);
    return Pair.of(baseDir, currentDir);
  }

  /**
   * Collect the base and current version of diff files
   *
   * @return
   */
  public Pair<String, String> collectDiffFilesWorking(List<DiffFile> diffFiles) {
    String baseDir = tempDir + File.separator + Version.BASE.asString() + File.separator;
    String currentDir = tempDir + File.separator + Version.CURRENT.asString() + File.separator;

    collect(baseDir, currentDir, diffFiles);
    return Pair.of(baseDir, currentDir);
  }

  /**
   * Collect the diff files into the data dir
   *
   * @param baseDir
   * @param currentDir
   * @param diffFiles
   * @return
   */
  private int collect(String baseDir, String currentDir, List<DiffFile> diffFiles) {
    int count = 0;
    Utils.createDir(baseDir);
    Utils.createDir(currentDir);
    for (DiffFile diffFile : diffFiles) {
      String basePath, currentPath;
      switch (diffFile.getStatus()) {
        case ADDED:
        case UNTRACKED:
          currentPath = currentDir + diffFile.getCurrentRelativePath();
          if (Utils.writeStringToFile(diffFile.getCurrentContent(), currentPath)) {
            count++;
          } else {
            logger.error("Error when collecting: " + diffFile.getStatus() + ":" + currentPath);
          }
          break;
        case DELETED:
          basePath = baseDir + diffFile.getBaseRelativePath();
          if (Utils.writeStringToFile(diffFile.getBaseContent(), basePath)) {
            count++;
          } else {
            logger.error("Error when collecting: " + diffFile.getStatus() + ":" + basePath);
          }
          break;
        case MODIFIED:
        case RENAMED:
        case COPIED:
          basePath = baseDir + diffFile.getBaseRelativePath();
          currentPath = currentDir + diffFile.getCurrentRelativePath();
          boolean baseOk = Utils.writeStringToFile(diffFile.getBaseContent(), basePath);
          boolean currentOk = Utils.writeStringToFile(diffFile.getCurrentContent(), currentPath);
          if (baseOk && currentOk) {
            count++;
          } else {
            logger.error("Error when collecting: " + diffFile.getStatus() + ":" + basePath);
          }
          break;
      }
    }
    return count;
  }

  /**
   * Save diffs for each diff file on the disk
   *
   * @param diffFiles
   * @return fileID : filePath (base if status!=ADDED else current)
   */
  public Map<String, String> collectDiffHunksWorking(List<DiffFile> diffFiles) {
    String diffDir = tempDir + File.separator + "diffs";
    Map<String, String> fileIDToPathMap = new HashMap<>();
    for (DiffFile diffFile : diffFiles) {
      // generate description for each diff hunk
      for (DiffHunk diffHunk : diffFile.getDiffHunks()) {
        diffHunk.setDescription(generateDescForDiffHunk(diffHunk));
      }

      String filePath =
          diffFile.getBaseRelativePath().isEmpty()
              ? diffFile.getCurrentRelativePath()
              : diffFile.getBaseRelativePath();
      fileIDToPathMap.put(diffFile.getFileID(), filePath);
      Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
      Utils.writeStringToFile(
          gson.toJson(diffFile.shallowClone()),
          diffDir + File.separator + diffFile.getFileID() + ".json");
    }

    // save the fileID to path map
    Gson gson = new Gson();
    String str = gson.toJson(fileIDToPathMap);
    Utils.writeStringToFile(str, tempDir + File.separator + "file_ids.json");
    return fileIDToPathMap;
  }

  /**
   * Generate description about the changes in a diff hunk
   *
   * @param diffHunk
   */
  private String generateDescForDiffHunk(DiffHunk diffHunk) {
    ChangeType changeType = diffHunk.getChangeType();
    ContentType baseContentType = diffHunk.getBaseHunk().getContentType();
    ContentType currentContentType = diffHunk.getCurrentHunk().getContentType();
    if (changeType.equals(ChangeType.ADDED)) {
      switch (currentContentType) {
        case IMPORT:
        case COMMENT:
        case BLANKLINE:
          return changeType.label + " " + currentContentType.label;
        case CODE:
          return analyzeCoveredNodes(ChangeType.ADDED, diffHunk.getCurrentHunk().getCoveredNodes());
        default:
          return changeType.label + " some code";
      }

    } else if (changeType.equals(ChangeType.DELETED)) {
      switch (baseContentType) {
        case IMPORT:
        case COMMENT:
        case BLANKLINE:
          return changeType.label + " " + baseContentType.label;
        case CODE:
          return analyzeCoveredNodes(ChangeType.DELETED, diffHunk.getBaseHunk().getCoveredNodes());
        default:
          return changeType.label + " some code";
      }
    } else if (changeType.equals(ChangeType.MODIFIED)) {
      // consider blank lines as empty, thus the change type should be add or delete
      if (baseContentType.equals(ContentType.BLANKLINE)) {
        return analyzeCoveredNodes(ChangeType.ADDED, diffHunk.getCurrentHunk().getCoveredNodes());
      }
      if (currentContentType.equals(ContentType.BLANKLINE)) {
        return analyzeCoveredNodes(ChangeType.DELETED, diffHunk.getBaseHunk().getCoveredNodes());
      }

      if (baseContentType.equals(ContentType.COMMENT)
          && currentContentType.equals(ContentType.COMMENT)) {
        return changeType.label + " " + ContentType.COMMENT.label;
      }

      if (baseContentType.equals(ContentType.IMPORT)
          && currentContentType.equals(ContentType.IMPORT)) {
        return changeType.label + " " + ContentType.IMPORT.label;
      }

      // assert: both code in base&current
      if (baseContentType.equals(ContentType.CODE) && currentContentType.equals(ContentType.CODE)) {
        // TODO: consider current too?
        return analyzeCoveredNodes(changeType, diffHunk.getBaseHunk().getCoveredNodes());
      }
    }
    return changeType.label + " some code";
  }

  private String analyzeCoveredNodes(ChangeType changeType, List<ASTNode> coveredNodes) {
    StringBuilder builder = new StringBuilder(changeType.label);
    if (coveredNodes.isEmpty()) {
      builder.append(" ").append("some code");
      return builder.toString();
    }
    for (String type : getNodeTypes(coveredNodes)) {
      builder.append(" ").append(type);
    }
    return builder.toString();
  }

  /**
   * Get the node types changed in diff hunks
   *
   * @param coveredNodes
   * @return
   */
  private List<String> getNodeTypes(List<ASTNode> coveredNodes) {
    Set<String> types = new LinkedHashSet<>();
    for (ASTNode node : coveredNodes) {
      types.add(Annotation.nodeClassForType(node.getNodeType()).getSimpleName());
    }
    return new ArrayList<>(types);
  }
}
