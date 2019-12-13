package com.github.smartcommit.util;

import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.model.DiffFile;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JDTService {
  private static final Logger logger = LoggerFactory.getLogger(RepoAnalyzer.class);
  private String repoPath;
  private String jrePath;

  public JDTService(String repoPath, String jrePath) {
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
   * Get all declaration descendants of an ASTNode
   *
   * @param node
   * @return
   */
  public List<BodyDeclaration> getDescendants(ASTNode node) {
    List<BodyDeclaration> descendants = new ArrayList<BodyDeclaration>();
    List list = node.structuralPropertiesForType();
    for (int i = 0; i < list.size(); i++) {
      Object child = node.getStructuralProperty((StructuralPropertyDescriptor) list.get(i));
      if (child instanceof List) {
        for (Iterator it = ((List) child).listIterator(); it.hasNext(); ) {
          Object child2 = it.next();
          if (child2 instanceof BodyDeclaration) {
            descendants.add((BodyDeclaration) child2);
            descendants.addAll(getDescendants((ASTNode) child2));
          }
        }
      }
      if (child instanceof BodyDeclaration) {
        descendants.add((BodyDeclaration) child);
      }
    }
    return descendants;
  }

  /**
   * Get only the direct children of an ASTNode
   *
   * @param node
   * @return
   */
  public List<ASTNode> getChildren(ASTNode node) {
    List<ASTNode> children = new ArrayList<ASTNode>();
    List list = node.structuralPropertiesForType();
    for (int i = 0; i < list.size(); i++) {
      Object child = node.getStructuralProperty((StructuralPropertyDescriptor) list.get(i));
      if (child instanceof ASTNode) {
        children.add((ASTNode) child);
      }
    }
    return children;
  }
}
