package com.github.smartcommit.util;

import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.model.constant.FileType;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Helper functions to operate the file and the system. */
public class Utils {
  /**
   * Run system command and return the output
   *
   * @param dir
   * @param commands
   * @return
   */
  public static String runSystemCommand(String dir, String... commands) {
    StringBuilder builder = new StringBuilder();
    try {
      Runtime rt = Runtime.getRuntime();
      Process proc = rt.exec(commands, null, new File(dir));

      BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

      BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

      String s = null;
      while ((s = stdInput.readLine()) != null) {
        builder.append(s);
        builder.append("\n");
        //                if (verbose) log(s);
      }

      while ((s = stdError.readLine()) != null) {
        builder.append(s);
        builder.append("\n");
        //                if (verbose) log(s);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return builder.toString();
  }

  /**
   * Convert the abbr symbol to status enum
   *
   * @param symbol
   * @return
   */
  public static FileStatus convertSymbolToStatus(String symbol) {
    for (FileStatus status : FileStatus.values()) {
      if (symbol.equals(status.symbol) || symbol.startsWith(status.symbol)) {
        return status;
      }
    }
    return FileStatus.UNMODIFIED;
  }

  /**
   * Read the content of a file into string
   *
   * @return
   */
  public static String readFileToString(String filePath) {
    String content = "";
    try {
      content = FileUtils.readFileToString(new File(filePath), "UTF-8");
    } catch (IOException e) {
      e.printStackTrace();
    }
    return content;
  }

  /**
   * Write the given content in the file of the given file path.
   *
   * @param content
   * @param filePath
   * @return boolean indicating the success of the write operation.
   */
  public static boolean writeStringToFile(String content, String filePath) {
    try {
      FileUtils.writeStringToFile(new File(filePath), content, "UTF-8");
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Delete all files and subfolders to clear the directory
   *
   * @param dir absolute path
   * @return
   */
  public static boolean clearDir(String dir) {
    File file = new File(dir);
    if (!file.exists()) {
      return false;
    }

    String[] content = file.list();
    for (String name : content) {
      File temp = new File(dir, name);
      if (temp.isDirectory()) {
        clearDir(temp.getAbsolutePath());
        temp.delete();
      } else {
        if (!temp.delete()) {
          System.err.println("Failed to delete the directory: " + name);
        }
      }
    }
    return true;
  }

  /**
   * Generate the absolute path of a diff file from its name in the diff output
   *
   * @param repoPath
   * @param diffFileName
   */
  public static String generatePathFromName(String repoPath, String diffFileName) {
    String separator = repoPath.endsWith(File.separator) ? "" : File.separator;
    if (diffFileName.startsWith("a/")) {
      return repoPath + diffFileName.replaceFirst("a/", separator);
    } else if (diffFileName.startsWith("b/")) {
      return repoPath + diffFileName.replaceFirst("b/", separator);
    } else {
      return repoPath + diffFileName;
    }
  }

  /**
   * Get the file name from given path (in Git, linux style)
   *
   * @param path
   * @return
   */
  public static String getFileNameFromPath(String path) {
    return path.substring(path.lastIndexOf("/") + 1);
  }

  /**
   * Check the file type by file path
   *
   * @return
   */
  public static FileType checkFileType(String filePath) {
    return Arrays.asList(FileType.values()).stream()
        .filter(fileType -> filePath.endsWith(fileType.extension))
        .findFirst()
        .orElse(FileType.OTHER);
  }

  /**
   * Check the content type of hunk
   *
   * @param codeLines
   * @return
   */
  public static ContentType checkContentType(List<String> codeLines) {
    if (codeLines.isEmpty()) {
      return ContentType.EMPTY;
    }
    ContentType contentType = ContentType.CODE;
    boolean isAllEmpty = true;
    for (String line : codeLines) {
      String trimmedLine = line.trim();
      if (trimmedLine.length() > 0) {
        isAllEmpty = false;
      }
      if (trimmedLine.startsWith("import")) {
        contentType = ContentType.IMPORT;
      } else {
        // TODO check for pure comments here

      }
    }
    return isAllEmpty ? ContentType.BLANKLINE : contentType;
  }

  /** Convert system-dependent path to the unified unix style */
  public static String formatPath(String path) {
    return path.replaceAll(Pattern.quote(File.separator), "/").replaceAll("/+", "/");
  }
}
