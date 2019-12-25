package com.github.smartcommit.io;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.model.mergebot.Diff;
import com.github.smartcommit.util.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DataCollector {
  private static final Logger logger = LoggerFactory.getLogger(DataCollector.class);

  private String repoName;
  private String tempDir;

  public DataCollector(String repoName, String tempDir) {
    this.repoName = repoName;
    this.tempDir = tempDir;
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
            + repoName
            + File.separator
            + commitID
            + File.separator
            + Version.BASE.asString()
            + File.separator;
    String currentDir =
        tempDir
            + File.separator
            + repoName
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
    for (DiffFile diffFile : diffFiles) {
      // TODO: currently only collect MODIFIED Java files
      if (diffFile.getFileType().equals(FileType.JAVA)
          && diffFile.getStatus().equals(FileStatus.MODIFIED)) {

        String aPath = baseDir + diffFile.getBaseRelativePath();
        String bPath = currentDir + diffFile.getCurrentRelativePath();
        boolean aOk = Utils.writeStringToFile(diffFile.getBaseContent(), aPath);
        boolean bOk = Utils.writeStringToFile(diffFile.getCurrentContent(), bPath);
        if (aOk && bOk) {
          count++;
        } else {
          logger.error("Error with: " + diffFile.getBaseRelativePath());
        }
      }
    }
    return count;
  }

  /**
   * Save diffs inside each DiffFile (for UI) Only needed for working tree
   *
   * @param diffFiles
   * @return fileID : filePath (base if status!=ADDED else current)
   */
  public Map<String, String> collectDiffHunksWorking(List<DiffFile> diffFiles) {
    String diffDir = tempDir + File.separator + "diffs";
    Map<String, String> fileIDToPathMap = new HashMap<>();
    for (DiffFile diffFile : diffFiles) {
      String filePath =
          diffFile.getStatus().equals(FileStatus.ADDED)
              ? diffFile.getCurrentRelativePath()
              : diffFile.getBaseRelativePath();
      String fileID = UUID.randomUUID().toString().replaceAll("-", "");
      fileIDToPathMap.put(fileID, filePath);

      Diff diff =
          new Diff(
              String.valueOf(repoName.hashCode()),
              repoName,
              fileID,
              diffFile.getBaseRelativePath(),
              diffFile.getCurrentRelativePath(),
              diffFile.getStatus().toString(),
              diffFile.getDiffHunks());
      Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
      Utils.writeStringToFile(gson.toJson(diff), diffDir + File.separator + fileID + ".json");
    }

    // save the fileID to path map
    Gson gson = new Gson();
    String str = gson.toJson(fileIDToPathMap);
    Utils.writeStringToFile(str, tempDir + File.separator + "file_ids.json");
    return fileIDToPathMap;
  }
}
