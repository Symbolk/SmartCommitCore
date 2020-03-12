package com.github.smartcommit.util;

import com.github.smartcommit.model.DiffFile;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.Map;

public class JDTParser {
  private static final Logger logger = Logger.getLogger(JDTParser.class);
  private String repoPath;
  private String jrePath;

  public JDTParser(String repoPath, String jrePath) {
    this.repoPath = repoPath;
    this.jrePath = jrePath;
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
      logger.error("Binding not enabled: " + diffFile.getBaseRelativePath());
    }

    parser = initASTParser();
    parser.setUnitName(Utils.getFileNameFromPath(diffFile.getCurrentRelativePath()));
    parser.setSource(diffFile.getCurrentContent().toCharArray());
    CompilationUnit newCU = (CompilationUnit) parser.createAST(null);
    if (!newCU.getAST().hasBindingsRecovery()) {
      logger.error("Binding not enabled: " + diffFile.getCurrentRelativePath());
    }
    return Pair.of(oldCU, newCU);
  }

  /**
   * Init the JDT ASTParser
   *
   * @return
   */
  public ASTParser initASTParser() {
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
}
