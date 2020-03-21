package com.github.smartcommit.util;

import com.github.smartcommit.commitmsg.RefActionType;
import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.constant.Operation;
import gr.uom.java.xmi.diff.CodeRange;
import info.debatty.java.stringsimilarity.Cosine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringType;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Helper functions to operate the file and the system. */
public class Utils {
  /**
   * Run system command under the given dir
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
   * Read csv file and return a list of separated items as string
   *
   * @param path
   * @param delimiter
   * @return
   */
  public static List<String[]> readCSV(String path, String delimiter) {
    List<String[]> results = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
      //      reader.readLine(); // skip header
      String line = null;
      while ((line = reader.readLine()) != null) {
        String items[] = line.split(delimiter);
        results.add(items);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return results;
  }

  /**
   * Read the content of a given file.
   *
   * @param path to be read
   * @return string content of the file, or null in case of errors.
   */
  public static List<String> readFileToLines(String path) {
    List<String> lines = new ArrayList<>();
    File file = new File(path);
    if (file.exists()) {
      try (BufferedReader reader =
          Files.newBufferedReader(Paths.get(path), Charset.forName("UTF-8"))) {
        lines = reader.lines().collect(Collectors.toList());
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      return lines;
    }
    return lines;
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
   * Create a folder if not exists
   *
   * @param dir abs path
   * @return
   */
  public static String createDir(String dir) {
    File directory = new File(dir);
    if (!directory.exists()) {
      // create the entire directory path including parents
      directory.mkdirs();
    }
    return directory.getAbsolutePath();
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
   * List all java files under a folder/directory
   *
   * @param dir
   * @return relative paths
   */
  public static List<String> listAllJavaFilePaths(String dir) {
    List<String> result = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(Paths.get(dir))) {
      result =
          walk.filter(Files::isRegularFile)
              .map(x -> x.toString())
              .filter(f -> f.endsWith(".java"))
              .map(s -> s.substring(dir.length()))
              .collect(Collectors.toList());
    } catch (IOException e) {
      e.printStackTrace();
    }
    return result;
  }

  /**
   * List all json files under a folder/directory
   *
   * @param dir
   * @return absolute paths
   */
  public static List<String> listAllJsonFilePaths(String dir) {
    List<String> result = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(Paths.get(dir))) {
      result =
          walk.filter(Files::isRegularFile)
              .map(x -> x.toString())
              .filter(f -> f.endsWith(".json"))
              //              .map(s -> s.substring(dir.length()))
              .collect(Collectors.toList());
    } catch (IOException e) {
      e.printStackTrace();
    }
    return result;
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
    boolean isAllEmpty = true;
    Set<String> lineTypes = new HashSet<>();

    for (int i = 0; i < codeLines.size(); ++i) {
      String trimmedLine = codeLines.get(i).trim();
      if (trimmedLine.length() > 0) {
        isAllEmpty = false;
        if (trimmedLine.startsWith("import")) {
          lineTypes.add("IMPORT");
        } else if (trimmedLine.startsWith("//")
            || trimmedLine.startsWith("/*")
            || trimmedLine.startsWith("/**")
            || trimmedLine.startsWith("*")) {
          lineTypes.add("COMMENT");
        } else {
          lineTypes.add("CODE");
        }
      }
    }

    if (isAllEmpty) {
      return ContentType.BLANKLINE;
    } else if (lineTypes.contains("CODE")) {
      return ContentType.CODE;
    } else if (lineTypes.contains("IMPORT")) {
      // import + comment ~= import
      return ContentType.IMPORT;
    } else if (lineTypes.contains("COMMENT")) {
      // pure comment
      return ContentType.COMMENT;
    }
    return ContentType.CODE;
  }

  /**
   * Remove comment from a string WARNING: Java's builtin regex support has problems with regexes
   * containing repetitive alternative paths (that is, (A|B)*), so this may lead to
   * StackOverflowError
   *
   * @param source
   * @return
   */
  private static String removeComment(String source) {
    return source.replaceAll(
        "((['\"])(?:(?!\\2|\\\\).|\\\\.)*\\2)|\\/\\/[^\\n]*|\\/\\*(?:[^*]|\\*(?!\\/))*\\*\\/", "");
    //    return source.replaceAll("[^:]//.*|/\\\\*((?!=*/)(?s:.))+\\\\*", "");
    //    return source.replaceAll("(?:/\\*(?:[^*]|(?:\\*+[^*/]))*\\*+/)|(?://.*)", "");
  }

  /** Convert system-dependent path to the unified unix style */
  public static String formatPath(String path) {
    return path.replaceAll(Pattern.quote(File.separator), "/").replaceAll("/+", "/");
  }

  /**
   * Generate the unique id
   *
   * @return
   */
  public static String generateUUID() {
    return UUID.randomUUID().toString().replaceAll("-", "");
  }

  /**
   * Convert string to a list of lines
   *
   * @param s
   * @return
   */
  public static List<String> convertStringToList(String s) {
    return Arrays.asList(s.split("\\r?\\n"));
  }

  /**
   * Convert a list of lines into one string with enter
   *
   * @param list
   * @return
   */
  public static String convertListLinesToString(List<String> list) {
    return String.join(System.lineSeparator(), list);
  }

  /**
   * Convert a list of lines to one string without format (to compare)
   *
   * @param list
   * @return
   */
  public static String convertListToStringNoFormat(List<String> list) {
    return list.stream().map(str -> str.replaceAll("\\s+", "")).collect(Collectors.joining(""));
  }

  /**
   * Split fileIndex:diffHunkIndex to get separate fields
   *
   * @param s
   * @return
   */
  public static Pair<Integer, Integer> parseIndices(String s) {
    String[] pair = s.split(":");
    if (pair.length == 2) {
      return Pair.of(Integer.valueOf(pair[0]), Integer.valueOf(pair[1]));
    }
    return Pair.of(-1, -1);
  }

  /**
   * Split fileIndex:diffHunkIndex to get separate fields
   *
   * @param s
   * @return
   */
  public static Pair<String, String> parseUUIDs(String s) {
    String[] pair = s.split(":");
    if (pair.length == 2) {
      return Pair.of(pair[0], pair[1]);
    }
    return null;
  }

  /**
   * Compute the string similarity between 2 strings
   *
   * @param s1
   * @param s2
   * @return
   */
  public static double computeStringSimilarity(String s1, String s2) {
    Cosine cosine = new Cosine();
    return cosine.similarity(s1, s2);
  }

  /**
   * Convert Refactoring To Action (suppose one to one currently)
   *
   * @param ref
   * @return
   */
  public static Action convertRefactoringToAction(Refactoring ref) {
    RefactoringType type = ref.getRefactoringType();
    List<CodeRange> codeChangesFrom = ref.leftSide();
    List<CodeRange> codeChangesTo = ref.rightSide();
    CodeRange codeChangeFrom = codeChangesFrom.get(0);
    Operation operation = Operation.UKN;
    String displayName = type.getDisplayName();
    for (Operation op : Operation.values()) {
      if (displayName.contains(op.label)) {
        operation = op;
      }
    }
    String TypeFrom = "";
    String TypeTo = "";
    // tell apart from-to via order
    boolean OnlyOne = true;
    for (RefActionType refActionType : RefActionType.values()) {
      if (displayName.contains(refActionType.label)) {
        TypeFrom = refActionType.label;
        if (OnlyOne) {
          TypeTo = TypeFrom;
          OnlyOne = false;
        } else {
          TypeTo = refActionType.label;
          break;
        }
      }
    }
    if (codeChangesTo.isEmpty()) {
      return new Action(operation, TypeFrom, codeChangeFrom.getCodeElement());
    } else {
      CodeRange codeChangeTo = codeChangesTo.get(0);
      return new Action(
          operation,
          TypeFrom,
          codeChangeFrom.getCodeElement(),
          TypeTo,
          codeChangeTo.getCodeElement());
    }
  }

  private static boolean checkSuffix(String str, String[] suffixes) {
    return Arrays.stream(suffixes).parallel().anyMatch(str::contains);
    //      for(String s : suffixes){
    //          if(str.endsWith(s)){
    //              return true;
    //          }
    //      }
    //      return  false;
  }

  public static boolean isDocFile(String fileName) {
    if (fileName.lastIndexOf(".") != -1) {
      return checkSuffix(fileName, new String[] {".md", ".txt", ".html"});
    } else {
      return true;
    }
  }

  public static boolean isConfigFile(String fileName) {
    if (fileName.lastIndexOf(".") != -1) {
      return checkSuffix(
          fileName, new String[] {".xml", ".yml", ".gitignore", ".gradle", ".properties"});
    } else {
      return false;
    }
  }

  /**
   * Merge two maps into one
   *
   * @param map1
   * @param map2
   * @return
   */
  public static Map<String, Set<String>> mergeTwoMaps(
      Map<String, Set<String>> map1, Map<String, Set<String>> map2) {
    map2.forEach(
        (key, value) ->
            map1.merge(
                key,
                value,
                (v1, v2) -> {
                  v1.addAll(v2);
                  return v1;
                }));

    return map1;
  }
}
