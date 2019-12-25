package com.github.smartcommit.io;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.constant.Version;
import com.github.smartcommit.util.Utils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

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
}
