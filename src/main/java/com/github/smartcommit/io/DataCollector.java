package com.github.smartcommit.io;

import com.github.smartcommit.model.Description;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.ChangeType;
import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.eclipse.jdt.core.dom.ASTNode.FIELD_DECLARATION;
import static org.eclipse.jdt.core.dom.ASTNode.METHOD_DECLARATION;

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
  private Description generateDescForDiffHunk(DiffHunk diffHunk) {
    ChangeType changeType = diffHunk.getChangeType();
    ContentType baseContentType = diffHunk.getBaseHunk().getContentType();
    ContentType currentContentType = diffHunk.getCurrentHunk().getContentType();
    if (changeType.equals(ChangeType.ADDED)) {
      switch (currentContentType) {
        case IMPORT:
        case COMMENT:
        case BLANKLINE:
          return new Description(changeType.label, currentContentType.label, "");
        case CODE:
          return analyzeCoveredNodes(ChangeType.ADDED, diffHunk.getCurrentHunk().getCoveredNodes());
        default:
          return new Description(changeType.label, "Code", "");
      }

    } else if (changeType.equals(ChangeType.DELETED)) {
      switch (baseContentType) {
        case IMPORT:
        case COMMENT:
        case BLANKLINE:
          return new Description(changeType.label, baseContentType.label, "");
        case CODE:
          return analyzeCoveredNodes(ChangeType.DELETED, diffHunk.getBaseHunk().getCoveredNodes());
        default:
          return new Description(changeType.label, "Code", "");
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
        return new Description(changeType.label, ContentType.COMMENT.label, "");
      }

      if (baseContentType.equals(ContentType.IMPORT)
          && currentContentType.equals(ContentType.IMPORT)) {
        return new Description(changeType.label, ContentType.IMPORT.label, "");
      }

      // assert: both code in base&current
      if (baseContentType.equals(ContentType.CODE) && currentContentType.equals(ContentType.CODE)) {
        return analyzeCoveredNodes(
            changeType,
            diffHunk.getBaseHunk().getCoveredNodes(),
            diffHunk.getCurrentHunk().getCoveredNodes());
      }
    }
    return new Description(changeType.label, "Code", "");
  }

  /**
   * Generate description from covered AST nodes (for add/delete)
   *
   * @param changeType
   * @param coveredNodes
   * @return
   */
  private Description analyzeCoveredNodes(ChangeType changeType, List<ASTNode> coveredNodes) {
    if (coveredNodes.isEmpty()) {
      return new Description(changeType.label, "Code", "");
    }
    StringBuilder types = new StringBuilder();
    StringBuilder labels = new StringBuilder();
    List<Pair<String, String>> infos = getASTNodesInfo(coveredNodes);
    for (int i = 0; i < infos.size(); i++) {
      Pair<String, String> info = infos.get(i);
      types.append(info.getLeft());
      if (i != infos.size() - 1) {
        types.append(", ");
      }
      labels.append(info.getRight());
      if (i != infos.size() - 1) {
        labels.append(", ");
      }
    }
    return new Description(changeType.label, types.toString(), labels.toString());
  }

  /**
   * Generate description from covered AST nodes (for modify)
   *
   * @param changeType
   * @param baseNodes
   * @return
   */
  private Description analyzeCoveredNodes(
      ChangeType changeType, List<ASTNode> baseNodes, List<ASTNode> currentNodes) {
    StringBuilder typesFrom = new StringBuilder();
    StringBuilder typesTo = new StringBuilder();
    StringBuilder labelsFrom = new StringBuilder();
    StringBuilder labelsTo = new StringBuilder();
    List<Pair<String, String>> infosFrom = getASTNodesInfo(baseNodes);
    List<Pair<String, String>> infosTo = getASTNodesInfo(currentNodes);
    for (int i = 0; i < infosFrom.size(); i++) {
      Pair<String, String> info = infosFrom.get(i);
      typesFrom.append(info.getLeft());
      if (i != infosFrom.size() - 1) {
        typesFrom.append(", ");
      }
      labelsFrom.append(info.getRight());
      if (i != infosFrom.size() - 1) {
        labelsFrom.append(", ");
      }
    }
    for (int i = 0; i < infosTo.size(); i++) {
      Pair<String, String> info = infosTo.get(i);
      typesTo.append(info.getLeft());
      if (i != infosFrom.size() - 1) {
        typesTo.append(", ");
      }
      labelsTo.append(info.getRight());
      if (i != infosFrom.size() - 1) {
        labelsTo.append(", ");
      }
    }
    return new Description(
        changeType.label,
        typesFrom.toString(),
        labelsFrom.toString(),
        typesTo.toString(),
        labelsTo.toString());
  }

  /**
   * Get the node types and labels (if exists) changed in diff hunks
   *
   * @param coveredNodes
   * @return
   */
  private List<Pair<String, String>> getASTNodesInfo(List<ASTNode> coveredNodes) {
    Set<Pair<String, String>> infos = new LinkedHashSet<>();
    for (ASTNode node : coveredNodes) {
      if (node != null) {
        String type = Annotation.nodeClassForType(node.getNodeType()).getSimpleName();
        String label = "";
        switch (node.getNodeType()) {
          case FIELD_DECLARATION:
            label =
                ((VariableDeclarationFragment) ((FieldDeclaration) node).fragments().get(0))
                    .getName()
                    .getIdentifier();
            break;
          case METHOD_DECLARATION:
            label = ((MethodDeclaration) node).getName().getIdentifier();
            break;
          default:
            label = "";
        }
        infos.add(Pair.of(type, label));
      }
    }
    return new ArrayList<>(infos);
  }
}
