package com.github.smartcommit.io;

import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.*;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.util.*;

import static org.eclipse.jdt.core.dom.ASTNode.*;

public class DataCollector {
  private static final Logger logger = Logger.getLogger(DataCollector.class);

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
    String baseDir =
        tempDir
            + File.separator
            + commitID
            + File.separator
            + Version.BASE.asString()
            + File.separator;
    String currentDir =
        tempDir
            + File.separator
            + commitID
            + File.separator
            + Version.CURRENT.asString()
            + File.separator;

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
      // skip binary files
      if (diffFile.getFileType().equals(FileType.BIN)) {
        continue;
      }
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
  public Map<String, String> collectDiffHunks(List<DiffFile> diffFiles, String resultsDir) {
    String diffDir = resultsDir + File.separator + "diffs";
    Map<String, String> fileIDToPathMap = new HashMap<>();
    for (DiffFile diffFile : diffFiles) {
      // generate description for each diff hunk
      for (DiffHunk diffHunk : diffFile.getDiffHunks()) {
        diffHunk.setAstActions(analyzeASTActions(diffHunk));
        // TODO: move refactoring detection earlier?
        diffHunk.generateDescription();
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
    Utils.writeStringToFile(str, resultsDir + File.separator + "file_ids.json");
    return fileIDToPathMap;
  }

  /**
   * Generate description about the changes in a diff hunk
   *
   * @param diffHunk
   */
  public static List<Action> analyzeASTActions(DiffHunk diffHunk) {
    List<Action> actions = new ArrayList<>();

    ChangeType changeType = diffHunk.getChangeType();
    ContentType baseContentType = diffHunk.getBaseHunk().getContentType();
    ContentType currentContentType = diffHunk.getCurrentHunk().getContentType();
    if (changeType.equals(ChangeType.ADDED)) {
      switch (currentContentType) {
        case IMPORT:
        case COMMENT:
        case BLANKLINE:
          actions.add(new Action(Operation.ADD, currentContentType.label, ""));
          return actions;
        case CODE:
          return analyzeCoveredNodes(ChangeType.ADDED, diffHunk.getCurrentHunk().getCoveredNodes());
        default:
          actions.add(new Action(Operation.ADD, "Code", ""));
          return actions;
      }

    } else if (changeType.equals(ChangeType.DELETED)) {
      switch (baseContentType) {
        case IMPORT:
        case COMMENT:
        case BLANKLINE:
          actions.add(new Action(Operation.DEL, baseContentType.label, ""));
          return actions;
        case CODE:
          return analyzeCoveredNodes(ChangeType.DELETED, diffHunk.getBaseHunk().getCoveredNodes());
        default:
          actions.add(new Action(Operation.DEL, "Code", ""));
          return actions;
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
        actions.add(new Action(Operation.UPD, ContentType.COMMENT.label, ""));
        return actions;
      }

      if (baseContentType.equals(ContentType.IMPORT)
          && currentContentType.equals(ContentType.IMPORT)) {
        actions.add(new Action(Operation.UPD, ContentType.IMPORT.label, ""));
        return actions;
      }

      // assert: both code in base&current
      if (baseContentType.equals(ContentType.CODE) && currentContentType.equals(ContentType.CODE)) {
        return analyzeCoveredNodes(
            changeType,
            diffHunk.getBaseHunk().getCoveredNodes(),
            diffHunk.getCurrentHunk().getCoveredNodes());
      }
    }
    actions.add(new Action(convertChangeTypeToOperation(changeType), "Code", ""));
    return actions;
  }

  private static Operation convertChangeTypeToOperation(ChangeType changeType) {
    switch (changeType) {
      case ADDED:
        return Operation.ADD;
      case DELETED:
        return Operation.DEL;
      case MODIFIED:
        return Operation.UPD;
      default:
        return Operation.UPD;
    }
  }
  /**
   * Generate description from covered AST nodes (for added/deleted)
   *
   * @param changeType
   * @param coveredNodes
   * @return
   */
  private static List<Action> analyzeCoveredNodes(ChangeType changeType, List<ASTNode> coveredNodes) {
    List<Action> actions = new ArrayList<>();
    Operation operation = convertChangeTypeToOperation(changeType);

    if (coveredNodes.isEmpty()) {
      actions.add(new Action(operation, "Code", ""));
      return actions;
    }

    List<Pair<String, String>> infos = getASTNodesInfo(coveredNodes);
    if (changeType.equals(ChangeType.ADDED)) {
      for (Pair<String, String> info : infos) {
        //        Action action = new Action(operation, "", "", info.getLeft(), info.getRight());
        Action action = new Action(operation, info.getLeft(), info.getRight());
        actions.add(action);
      }
    } else if (changeType.equals(ChangeType.DELETED)) {
      for (Pair<String, String> info : infos) {
        Action action = new Action(operation, info.getLeft(), info.getRight());
        actions.add(action);
      }
    }
    return actions;
  }

  /**
   * Generate description from covered AST nodes (for modified)
   *
   * @param changeType
   * @param baseNodes
   * @return
   */
  private static List<Action> analyzeCoveredNodes(
      ChangeType changeType, List<ASTNode> baseNodes, List<ASTNode> currentNodes) {
    List<Action> actions = new ArrayList<>();
    Operation operation = convertChangeTypeToOperation(changeType);

    List<Pair<String, String>> infosFrom = getASTNodesInfo(baseNodes);
    List<Pair<String, String>> infosTo = getASTNodesInfo(currentNodes);
    for (int i = 0; i < Math.min(infosFrom.size(), infosTo.size()); i++) {
      Action action =
          new Action(
              operation,
              infosFrom.get(i).getLeft(),
              infosFrom.get(i).getRight(),
              infosTo.get(i).getLeft(),
              infosTo.get(i).getRight());
      actions.add(action);
    }

    return actions;
  }

  /**
   * Get the node types and labels (if exists) changed in diff hunks
   *
   * @param coveredNodes
   * @return
   */
  private static List<Pair<String, String>> getASTNodesInfo(List<ASTNode> coveredNodes) {
    Set<Pair<String, String>> infos = new LinkedHashSet<>();
    for (ASTNode node : coveredNodes) {
      if (node != null) {
        String type = Annotation.nodeClassForType(node.getNodeType()).getSimpleName();
        String label = "";
        switch (node.getNodeType()) {
          case TYPE_DECLARATION:
            label = ((TypeDeclaration) node).getName().getIdentifier();
            break;
          case ENUM_DECLARATION:
            label = ((EnumDeclaration) node).getName().getIdentifier();
            break;
          case VARIABLE_DECLARATION_STATEMENT:
            label =
                ((VariableDeclarationFragment)
                        ((VariableDeclarationStatement) node).fragments().get(0))
                    .getName()
                    .getIdentifier();
            break;
          case FIELD_DECLARATION:
            label =
                ((VariableDeclarationFragment) ((FieldDeclaration) node).fragments().get(0))
                    .getName()
                    .getIdentifier();
            break;
          case METHOD_DECLARATION:
            label = ((MethodDeclaration) node).getName().getIdentifier();
            break;
          case EXPRESSION_STATEMENT:
            label = ((ExpressionStatement) node).toString();
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
