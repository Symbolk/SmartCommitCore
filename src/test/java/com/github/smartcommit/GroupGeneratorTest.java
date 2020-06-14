package com.github.smartcommit;

import com.github.smartcommit.client.Config;
import com.github.smartcommit.client.SmartCommit;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.Group;
import com.github.smartcommit.util.Utils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class GroupGeneratorTest {

  private static String repoName = "nomulus";
  private static String repoPath =
      System.getProperty("user.dir") + File.separator + ".." + File.separator + repoName;
  private static String cloneUrl = "https://github.com/google/nomulus";
  public static String tempDir =
      System.getProperty("user.home")
          + File.separator
          + ".mergebot"
          + File.separator
          + "repos"
          + File.separator
          + repoName
          + "_mergebot"
          + File.separator
          + "smart_commit";

  @BeforeAll
  public static void setUpBeforeAll() {
    //    extractDiffHunkIDs(tempDir);
    BasicConfigurator.configure();
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
    // clone test repo if not exist
    try {
      cloneIfNotExists(repoPath, cloneUrl);
    } catch (Exception e) {
      e.printStackTrace();
    }

    // prepare workspace
    try (Git git = Git.open(Paths.get(repoPath).toFile())) {
      git.reset()
          .setMode(ResetCommand.ResetType.HARD)
          .setRef("bc7f3546c73631ff241dd4406b2317d1cc1b7a58")
          .call();
      git.reset().setMode(ResetCommand.ResetType.MIXED).setRef("HEAD~").call();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testGrouping() {
    // run group generation and get result
    SmartCommit smartCommit =
        new SmartCommit(String.valueOf(repoName.hashCode()), repoName, repoPath, tempDir);
    smartCommit.setDetectRefactorings(true);
    smartCommit.setProcessNonJavaChanges(false);
    smartCommit.setSimilarityThreshold(Config.SIMI_THRESHOLD);
    smartCommit.setDistanceThreshold(Config.DIS_THRESHOLD);

    // expected groups
    Map<String, List<String>> expected = new HashMap<>();
    // diff hunk position in base: <path, startline>
    List<String> diffHunkIndices =
        new ArrayList<>(Arrays.asList("10:0", "10:1", "11:0", "11:1", "15:0"));
    expected.put("group0", diffHunkIndices);

    diffHunkIndices =
        new ArrayList<>(
            Arrays.asList(
                "1:5", "1:7", "1:8", "1:9", "1:10", "1:11", "1:13", "1:14", "2:2", "2:3", "2:5",
                "3:0", "4:14", "5:1", "7:0", "1:4"));
    expected.put("group1", diffHunkIndices);

    diffHunkIndices = new ArrayList<>(Arrays.asList("3:2", "4:6"));
    diffHunkIndices.add("3:2");
    diffHunkIndices.add("4:6");
    expected.put("group2", diffHunkIndices);

    diffHunkIndices = new ArrayList<>(Arrays.asList("0:0", "1:12"));
    expected.put("group3", diffHunkIndices);

    diffHunkIndices =
        new ArrayList<>(
            Arrays.asList(
                "1:0", "1:1", "1:2", "1:3", "1:6", "2:0", "2:1", "2:4", "2:6", "2:7", "3:1", "4:0",
                "4:1", "4:2", "4:3", "4:4", "4:5", "4:7", "4:8", "4:9", "4:10", "4:11", "4:12",
                "4:13", "5:0", "5:2", "6:0", "7:1", "8:0", "8:1", "9:0", "9:1", "12:0", "13:0",
                "14:0"));
    expected.put("group4", diffHunkIndices);

    try {
      Map<String, Group> groups = smartCommit.analyzeWorkingTree();
      Map<String, DiffHunk> id2DiffHunkMap = smartCommit.getId2DiffHunkMap();
      smartCommit.exportGroupDetails(groups, tempDir + File.separator + "details");

      assertThat(groups.size()).isEqualTo(expected.size());

      for (Map.Entry entry : expected.entrySet()) {
        Group group = groups.get(entry.getKey());
        List<String> expectedIndices = (List<String>) entry.getValue();
        for (int i = 0; i < group.getDiffHunkIDs().size(); ++i) {
          String id = group.getDiffHunkIDs().get(i);
          String[] pair = id.split(":");
          if (pair.length == 2) {
            DiffHunk diffHunk = id2DiffHunkMap.get(pair[1]);

            // compare with expected
            // diff hunks in each group (filepath:line)
            assertThat(diffHunk.getUniqueIndex()).isEqualTo(expectedIndices.get(i));

          } else {
            System.err.println("Invalid id: " + id);
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static Repository cloneIfNotExists(String repoPath, String cloneUrl) throws Exception {
    File folder = new File(repoPath);
    Repository repository;
    if (folder.exists()) {
      RepositoryBuilder builder = new RepositoryBuilder();
      repository =
          builder.setGitDir(new File(folder, ".git")).readEnvironment().findGitDir().build();

      System.out.println(
          "Repo " + cloneUrl + " has been cloned, current branch is " + repository.getBranch());

    } else {
      System.out.println("Cloning " + cloneUrl + " ...");
      Git git =
          Git.cloneRepository()
              .setDirectory(folder)
              .setURI(cloneUrl)
              .setCloneAllBranches(false)
              .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
              .call();
      repository = git.getRepository();
      System.out.println(
          "Done cloning " + cloneUrl + ", current branch is " + repository.getBranch());
    }
    return repository;
  }

  private static void extractDiffHunkIDs(String tempDir) {
    List<String> paths = Utils.listAllJsonFilePaths(tempDir + File.separator + "details");
    for (String path : paths) {
      System.out.println(path);
      List<String> lines = Utils.readFileToLines(path);
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < lines.size(); ++i) {
        if (lines.get(i).equals("------------")) {
          builder.append("\"").append(lines.get(i + 1)).append("\",");
        }
      }

      System.out.println(builder.toString());
    }
  }
}
