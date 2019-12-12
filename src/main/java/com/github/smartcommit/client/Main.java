package com.github.smartcommit.client;

import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.core.SimpleVisitor;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.JDTService;
import org.apache.commons.lang3.tuple.Pair;
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
    String REPO_PATH = "/Users/symbolk/coding/data/nomulus";
    String COMMIT_ID = "906b054f4b7a2e38681fd03282996955406afd65";
    String JRE_PATH =
        "/Library/Java/JavaVirtualMachines/jdk1.8.0_231.jdk/Contents/Home/jre/lib/rt.jar";
    GitService gitService = new GitServiceCGit();
    JDTService jdtService = new JDTService(REPO_PATH, JRE_PATH);
    RepoAnalyzer repoAnalyzer = new RepoAnalyzer(REPO_PATH, COMMIT_ID);
    // collect the changed files and all diff hunks
    ArrayList<DiffFile> diffFiles = gitService.getChangedFilesAtCommit(REPO_PATH, COMMIT_ID);
    //        ArrayList<DiffFile> diffFiles = gitService.getChangedFilesInWorkingTree(REPO_PATH);
    List<DiffHunk> allDiffHunks = gitService.getDiffHunksAtCommit(REPO_PATH, COMMIT_ID, diffFiles);
    //        List<DiffHunk> allDiffHunks = gitService.getDiffHunksInWorkingTree(REPO_PATH);
    repoAnalyzer.setDiffFiles(diffFiles);
    repoAnalyzer.setDiffHunks(allDiffHunks);

    // find the AST nodes covered by each diff hunk
    for (DiffFile diffFile : diffFiles) {
      if (diffFile.getFileType().equals(FileType.JAVA)) {
        // get all diff hunks within this file
        List<DiffHunk> diffHunksInFile = diffFile.getDiffHunks();
        // parse the changed files into ASTs
        Pair<CompilationUnit, CompilationUnit> CUPair = jdtService.generateCUPair(diffFile);

        // extract change stems of each diff hunk, resolve symbols to get qualified name as the
        // feature of the diff hunk
        if (CUPair.getRight() != null) {
          CompilationUnit cu = CUPair.getRight();
          for (DiffHunk diffHunk : diffHunksInFile) {
            // find nodes covered or covering by each diff hunk
            int startPos = cu.getPosition(diffHunk.getCurrentStartLine(), 0);
            int endPos = cu.getPosition(diffHunk.getCurrentEndLine() + 1, 0);
            int length = endPos - startPos;
            if (length > 0) {
              NodeFinder nodeFinder = new NodeFinder(cu, startPos, length);
              ASTNode coveredNode = nodeFinder.getCoveredNode();
              if (coveredNode != null) {
                //                coveredNode = coveredNode.getParent();
                // collect data and control flow symbols
                //                IdentifierVisitor v = new IdentifierVisitor();
                SimpleVisitor v = new SimpleVisitor();
                coveredNode.accept(v);
                //                v.getInvokedMethods();
                diffHunk.setSimpleTypes(v.getSimpleTypes());
                diffHunk.setSimpleNames(v.getSimpleNames());
              }
            }
          }
        }
      }
    }
    // compute the distance matrix
    // build the graph
    for (DiffHunk diffHunk : allDiffHunks) {}

    // visualize the graph
  }
}
