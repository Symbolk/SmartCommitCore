package com.github.smartcommit.util;

import com.github.smartcommit.model.DiffFileStatus;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

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
  public static DiffFileStatus convertSymbolToStatus(String symbol) {
    for (DiffFileStatus status : DiffFileStatus.values()) {
      if (symbol.equals(status.symbol)) {
        return status;
      }
    }
    return DiffFileStatus.UNMODIFIED;
  }

  /**
   * Read the content of a given file.
   *
   * @param path to be read
   * @return string content of the file, or null in case of errors.
   */
  public static String readFileToString(String path) {
    String content = "";
    File file = new File(path);
    if (file.exists()) {
      String fileEncoding = "UTF-8";
      try (BufferedReader reader =
          Files.newBufferedReader(Paths.get(path), Charset.forName(fileEncoding))) {
        content = reader.lines().collect(Collectors.joining("\n"));
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      System.err.println(path + " does not exist!");
    }
    return content;
  }

  /**
   * Writes the given content in the file of the given file path.
   *
   * @param filePath
   * @param content
   * @return boolean indicating the success of the write operation.
   */
  public static boolean writeContentToPath(String filePath, String content) {
    if (!content.isEmpty()) {
      try {
        File file = new File(filePath);
        if (!file.exists()) {
          file.getParentFile().mkdirs();
          file.createNewFile();
        }
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
        writer.write(content);
        writer.flush();
        writer.close();
      } catch (NullPointerException ne) {
        ne.printStackTrace();
        // empty, necessary for integration with git version control system
      } catch (Exception e) {
        e.printStackTrace();
        return false;
      }
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
}
