package com.github.smartcommit.util;

import com.github.smartcommit.model.Action;
import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.model.constant.Operation;
import gr.uom.java.xmi.diff.CodeRange;
import info.debatty.java.stringsimilarity.Cosine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.mozilla.universalchardet.UniversalDetector;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringType;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
  public static String runSystemCommand(String dir, Charset charSet, String... commands) {
    StringBuilder builder = new StringBuilder();
    try {
      Runtime rt = Runtime.getRuntime();
      Process proc = rt.exec(commands, null, new File(dir));

      BufferedReader stdInput =
          new BufferedReader(new InputStreamReader(proc.getInputStream(), charSet));

      BufferedReader stdError =
          new BufferedReader(new InputStreamReader(proc.getErrorStream(), charSet));

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
        String[] items = line.split(delimiter);
        results.add(items);
      }
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
          Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
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
   * Append a string to the end of a file
   *
   * @param filePath
   * @param content
   */
  public static void appendStringToFile(String filePath, String content) {
    Path path = Paths.get(filePath);
    byte[] contentBytes = content.getBytes();
    try {
      Files.write(path, contentBytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Write a list of lines into a file
   *
   * @param lines
   * @return
   */
  public static List<String> writeLinesToFile(List<String> lines, String filePath) {
    String content = lines.stream().collect(Collectors.joining(System.lineSeparator()));
    writeStringToFile(content, filePath);
    return lines;
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
    if (content != null) {
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
    }

    return true;
  }

  /**
   * Rename an existing dir
   *
   * @param dirPath
   * @param newDirName
   */
  public static String renameDir(String dirPath, String newDirName) {
    File dir = new File(dirPath);
    if (!dir.isDirectory()) {
      System.err.println("Not a directory: " + dirPath);
    } else {
      File newDir = new File(dir.getParent() + File.separator + newDirName);
      if (dir.renameTo(newDir)) {
        return newDir.getAbsolutePath();
      } else {
        System.err.println("Renaming failed to: " + newDir.getAbsolutePath());
      }
    }
    return dir.getAbsolutePath();
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
              .map(Path::toString)
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
   * Check the file type by file path. Two ways to determine whether a file is binary: by git diff
   * or by file -bI, the file must accessible on disk.
   *
   * @return
   */
  public static FileType checkFileType(String repoPath, String filePath) {
    //    String output = Utils.runSystemCommand(repoPath, StandardCharsets.UTF_8, "file", "-bI",
    // filePath);
    //    if(output.trim().endsWith("binary")){
    //      return FileType.BIN;
    //    }
    String output =
        Utils.runSystemCommand(
            repoPath,
            StandardCharsets.UTF_8,
            "git",
            "diff",
            "--no-index",
            "--numstat",
            "/dev/null",
            filePath);
    if (output.trim().replaceAll("\\s+", "").startsWith("--")) {
      return FileType.BIN;
    } else {
      // match type by extension
      return Arrays.stream(FileType.values())
          .filter(fileType -> filePath.endsWith(fileType.extension))
          .findFirst()
          .orElse(FileType.OTHER);
    }
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
   * Convert a list of lines to one string without non-word characters
   *
   * @param list
   * @return
   */
  public static String convertListToStringNoFormat(List<String> list) {
    return list.stream()
        .map(str -> str.replaceAll("\\W|[\\t\\r?\\n]+", ""))
        //        .map(str -> str.replaceAll("\\\\t|[\\\\r?\\\\n]+|\\s+", ""))
        .collect(Collectors.joining(""));
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
    return Pair.of("", "");
  }

  /**
   * Compute the string similarity between 2 strings
   *
   * @param s1
   * @param s2
   * @return
   */
  public static double cosineStringSimilarity(String s1, String s2) {
    Cosine cosine = new Cosine();
    return cosine.similarity(s1, s2);
    //    Jaccard jaccard = new Jaccard();
    //    return jaccard.similarity(s1, s2);
  }

  /**
   * Compute the string similarity between 2 strings
   *
   * @param s1
   * @param s2
   * @return
   */
  public static double tokenStringSimilarity(String s1, String s2) {
    JaccardSimilarity jaccard = new JaccardSimilarity();
    return jaccard.apply(s1, s2);
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
        break;
      }
    }
    String typeFrom = splitAndCapitalize(codeChangeFrom.getCodeElementType().name());
    if (codeChangesTo.isEmpty()) {
      return new Action(operation, typeFrom, prettifyCodeElement(codeChangeFrom.getCodeElement()));
    } else {
      CodeRange codeChangeTo = codeChangesTo.get(0);
      String typeTo = splitAndCapitalize(codeChangeTo.getCodeElementType().name());
      return new Action(
          operation,
          typeFrom,
          prettifyCodeElement(codeChangeFrom.getCodeElement()),
          typeTo,
          prettifyCodeElement(codeChangeTo.getCodeElement()));
    }
  }

  /**
   * Convert strings like 'AB_CD' to 'Ab Cd'
   *
   * @param original
   * @return
   */
  private static String splitAndCapitalize(String original) {
    String[] words = original.split("_");
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < words.length; ++i) {
      String w = words[i];
      builder.append(w.substring(0, 1).toUpperCase()).append(w.substring(1).toLowerCase());
      if (i != words.length - 1) {
        builder.append(" ");
      }
    }
    return builder.toString();
  }

  /**
   * Convert the code element label by RMiner to more readable style e.g. from private
   * creationTimestamp : ZonedDateTime to private ZonedDateTime creationTimestamp
   *
   * @return
   */
  private static String prettifyCodeElement(String original) {
    if (original == null) {
      return "";
    }
    if (original.contains(":")) {
      int colonIndex = original.indexOf(":");
      String type = original.substring(colonIndex + 1).trim();
      int spaceIndex = original.substring(0, colonIndex).trim().lastIndexOf(" ");
      // insert the type before the last word
      if (spaceIndex < 0) {
        return type + " " + original.substring(0, colonIndex).trim();
      } else {
        return original.substring(0, spaceIndex).trim()
            + " "
            + type
            + " "
            + original.substring(spaceIndex, colonIndex).trim();
      }
    }
    return original;
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
      return checkSuffix(fileName, new String[] {".md", ".txt"});
    } else {
      return true;
    }
  }

  public static boolean isResourceFile(String fileName) {
    if (fileName.lastIndexOf(".") != -1) {
      return checkSuffix(fileName, new String[] {".json", ".css", ".html"});
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

  /**
   * Get the most common element in one list
   *
   * @param list
   * @param <T>
   * @return
   */
  public static <T> T mostCommon(List<T> list) {
    Map<T, Integer> map = new HashMap<>();

    for (T t : list) {
      Integer val = map.get(t);
      map.put(t, val == null ? 1 : val + 1);
    }

    Map.Entry<T, Integer> max = null;

    for (Map.Entry<T, Integer> e : map.entrySet()) {
      if (max == null || e.getValue() > max.getValue()) max = e;
    }

    return max.getKey();
  }

  /**
   * Sort a map in descending order by the value
   *
   * @param original
   * @return
   */
  public static Map<String, Integer> sortMapByValue(Map<String, Integer> original) {
    return original.entrySet().stream()
        .sorted((Map.Entry.<String, Integer>comparingByValue().reversed()))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
  }

  /**
   * Jaccard = Intersection/Union in [0,1]
   *
   * @param s1
   * @param s2
   * @return
   */
  private static double jaccard(Set s1, Set s2) {
    Set<String> union = new HashSet<>();
    union.addAll(s1);
    union.addAll(s2);
    Set<String> intersection = new HashSet<>();
    intersection.addAll(s1);
    intersection.retainAll(s2);
    if (union.size() <= 0) {
      return 0D;
    } else {
      return (double) intersection.size() / union.size();
    }
  }

  /**
   * Compute the similarity of two lists by comparing as sets (no order, no duplicates)
   *
   * @param list1
   * @param list2
   * @return
   */
  public static double computeListSimilarity(List<Action> list1, List<Action> list2) {
    if (list1.isEmpty() && list2.isEmpty()) {
      return 1D;
    }
    if (list1.isEmpty() || list2.isEmpty()) {
      return 0D;
    }
    return jaccard(new HashSet(list1), new HashSet(list2));
  }

  /**
   * Format the double value to leave 2 digits after .
   *
   * @param value
   * @return
   */
  public static double formatDouble(double value) {
    return (double) Math.round(value * 100) / 100;
  }

  public static Charset detectCharset(String filePath) {
    try {
      Path fileLocation = Paths.get(filePath);
      byte[] content = Files.readAllBytes(fileLocation);
      UniversalDetector detector = new UniversalDetector(null);
      detector.handleData(content, 0, content.length);
      detector.dataEnd();
      String detectorCode = detector.getDetectedCharset();
      if (detectorCode != null && detectorCode.startsWith("GB")) {
        return Charset.forName("GBK");
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return StandardCharsets.UTF_8;
  }

  /**
   * Return the file extension given a file path
   *
   * @param path
   * @return
   */
  public static String getFileExtension(String path) {
    int index = path.lastIndexOf(".");
    if (index == -1) {
      return path;
    } else {
      return path.substring(index);
    }
  }
}
