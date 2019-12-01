package com.github.smartcommit.client;

import com.github.smartcommit.core.IdentifierVisitor;
import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Main test and debug class */
public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    PropertyConfigurator.configure("log4j.properties");
    //    BasicConfigurator.configure();

    // given a git repo, get the file-level change set of the working directory
    String REPO_PATH = "/Users/symbolk/coding/dev/IntelliMerge";
    String COMMIT_ID = "53c1c430de96e459fc6b633d20c328eaff7d0374";
    String JRE_PATH =
        "/Library/Java/JavaVirtualMachines/jdk1.8.0_231.jdk/Contents/Home/jre/lib/rt.jar";
    GitService gitService = new GitServiceCGit();
    RepoAnalyzer repoAnalyzer = new RepoAnalyzer(JRE_PATH, REPO_PATH, COMMIT_ID);
    // collect the changed files and diff hunks
    ArrayList<DiffFile> diffFiles = gitService.getChangedFilesAtCommit(REPO_PATH, COMMIT_ID);
    //        ArrayList<DiffFile> diffFiles = gitService.getChangedFilesInWorkingTree(REPO_PATH);
    List<DiffHunk> diffHunks = gitService.getDiffHunksAtCommit(REPO_PATH, COMMIT_ID);
    //        List<DiffHunk> diffHunks = gitService.getDiffHunksInWorkingTree(REPO_PATH);
    repoAnalyzer.setDiffFiles(diffFiles);
    repoAnalyzer.setDiffHunks(diffHunks);

    // find the AST nodes covered by each diff hunk
    for (DiffFile diffFile : diffFiles) {
      // get all diff hunks within this file
      List<DiffHunk> diffHunksInFile = repoAnalyzer.getDiffHunksInFile(diffFile, diffHunks);
      // parse the changed files into ASTs
      Pair<CompilationUnit, CompilationUnit> CUPair = repoAnalyzer.generateCUPair(diffFile);

      // extract change stems of each diff hunk, resolve symbols to get qualified name as the
      // feature of the diff hunk
      if (CUPair.getRight() != null) {
        CompilationUnit cu = CUPair.getRight();
        for (DiffHunk diffHunk : diffHunksInFile) {
          // find nodes covered or covering by each diff hunk
          int startPos = cu.getPosition(diffHunk.getNewStartLine(), 0);
          int endPos = cu.getPosition(diffHunk.getNewEndLine() + 1, 0);
          int length = endPos - startPos;
          if (length > 0) {
            NodeFinder nodeFinder = new NodeFinder(CUPair.getLeft(), startPos, length);
            ASTNode coveredNode = nodeFinder.getCoveredNode();
            if (coveredNode != null) {
              IdentifierVisitor v = new IdentifierVisitor();
              coveredNode.accept(v);
            }
          }
        }
        // resolve symbols to get fully qualified name
        // build nodes for diff hunks and unchanged nodes

        // visualize the graph
      }
    }
  }
}
