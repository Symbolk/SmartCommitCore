package com.github.smartcommit.core;

import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.util.Utils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;

public class RepoAnalyzer {

  private static final Logger logger = LoggerFactory.getLogger(RepoAnalyzer.class);

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

  /**
   * Currently for Java 8
   *
   * @param diffFile
   * @return
   */
  public Pair<CompilationUnit, CompilationUnit> generateCUPair(DiffFile diffFile) {

    ASTParser parser = initASTParser();
    parser.setUnitName(Utils.getFileNameFromPath(diffFile.getBaseRelativePath()));
    parser.setSource(diffFile.getBaseContent().toCharArray());
    CompilationUnit oldCU = (CompilationUnit) parser.createAST(null);
    if (!oldCU.getAST().hasBindingsRecovery()) {
      logger.error("Binding not enabled: {}", diffFile.getBaseRelativePath());
    }

    parser = initASTParser();
    parser.setUnitName(Utils.getFileNameFromPath(diffFile.getCurrentRelativePath()));
    parser.setSource(diffFile.getCurrentContent().toCharArray());
    CompilationUnit newCU = (CompilationUnit) parser.createAST(null);
    if (!newCU.getAST().hasBindingsRecovery()) {
      logger.error("Binding not enabled: {}", diffFile.getCurrentRelativePath());
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
  public List<DiffHunk> getDiffHunksInFile(DiffFile diffFile, List<DiffHunk> diffHunks) {
    String baseRelativePath = diffFile.getBaseRelativePath();
    String currentRelativePath = diffFile.getCurrentRelativePath();

    List<DiffHunk> results =
        diffHunks.stream()
            .filter(
                diffHunk ->
                    diffHunk.getBaseHunk().getRelativeFilePath().contains(baseRelativePath)
                        && diffHunk
                            .getCurrentHunk()
                            .getRelativeFilePath()
                            .contains(currentRelativePath))
            .collect(Collectors.toList());
    return results;
  }
}
