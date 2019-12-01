package com.github.smartcommit.client;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;

public class RepoAnalyzer {
  private String repoPath;
  private String jrePath;
  private String commitID;
  private List<DiffFile> diffFiles;
  private List<DiffHunk> diffHunks;

  public RepoAnalyzer(String jrePath, String repoPath) {
    this.jrePath = jrePath;
    this.repoPath = repoPath;
  }

  public RepoAnalyzer(String jrePath, String repoPath, String commitID) {
    this.jrePath = jrePath;
    this.repoPath = repoPath;
    this.commitID = commitID;
  }

  public String getRepoPath() {
    return repoPath;
  }

  public String getCommitID() {
    return commitID;
  }

  public List<DiffFile> getDiffFiles() {
    return diffFiles;
  }

  public void setDiffFiles(List<DiffFile> diffFiles) {
    this.diffFiles = diffFiles;
  }

  public List<DiffHunk> getDiffHunks() {
    return diffHunks;
  }

  public void setDiffHunks(List<DiffHunk> diffHunks) {
    this.diffHunks = diffHunks;
  }

  public static void main(String[] args) {
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
              SimpleNameVisitor v = new SimpleNameVisitor();
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

  static class SimpleNameVisitor extends ASTVisitor {
    @Override
    public boolean visit(SimpleName node) {
      System.out.println(node.getIdentifier());
      System.out.println(node.resolveBinding());
      System.out.println(node.resolveTypeBinding());
      System.out.println("--------------");
      return true;
    }
  }

  /**
   * Currently for Java 8
   *
   * @param diffFile
   * @return
   */
  private Pair<CompilationUnit, CompilationUnit> generateCUPair(DiffFile diffFile) {

    ASTParser parser = initASTParser();
    parser.setUnitName(Utils.getFileNameFromPath(diffFile.getOldRelativePath()));
    parser.setSource(diffFile.getOldContent().toCharArray());
    CompilationUnit oldCU = (CompilationUnit) parser.createAST(null);
    if (oldCU.getAST().hasBindingsRecovery()) {
      System.out.println("Old CU binding enabled!");
    }

    parser = initASTParser();
    parser.setUnitName(Utils.getFileNameFromPath(diffFile.getNewRelativePath()));
    parser.setSource(diffFile.getNewContent().toCharArray());
    CompilationUnit newCU = (CompilationUnit) parser.createAST(null);
    if (newCU.getAST().hasBindingsRecovery()) {
      System.out.println("New CU binding enabled!");
    }
    return Pair.of(oldCU, newCU);
  }

  /**
   * Init the JDT ASTParser
   *
   * @return
   */
  private ASTParser initASTParser() {
    // set up the parser and resolver options
    ASTParser parser = ASTParser.newParser(8);
    parser.setResolveBindings(true);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setBindingsRecovery(true);
    Map options = JavaCore.getOptions();
    parser.setCompilerOptions(options);

    // set up the arguments
    String[] sources = {this.repoPath}; // sources to resolve symbols
    String[] classpath = {this.jrePath}; // local java runtime (rt.jar) path
    parser.setEnvironment(classpath, sources, new String[] {"UTF-8"}, true);
    return parser;
  }

  /**
   * Filter the diff hunks within the given diff file
   *
   * @param diffFile
   * @param diffHunks
   * @return
   */
  private List<DiffHunk> getDiffHunksInFile(DiffFile diffFile, List<DiffHunk> diffHunks) {
    String oldRelativePath = diffFile.getOldRelativePath();
    String newRelativePath = diffFile.getNewRelativePath();

    List<DiffHunk> results =
        diffHunks.stream()
            .filter(
                diffHunk ->
                    diffHunk.getOldRelativePath().contains(oldRelativePath)
                        && diffHunk.getNewRelativePath().contains(newRelativePath))
            .collect(Collectors.toList());
    return results;
  }
}
