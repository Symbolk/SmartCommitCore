package com.github.smartcommit.client;

import com.github.smartcommit.core.RepoAnalyzer;
import com.github.smartcommit.core.visitor.MyNodeFinder;
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.FileType;
import com.github.smartcommit.util.JDTParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Main test and debug class */
public class Test {
  private static final Logger logger = LoggerFactory.getLogger(Test.class);

  public static void main(String[] args) {
    PropertyConfigurator.configure("log4j.properties");
    //    BasicConfigurator.configure();

    String REPO_NAME = Config.REPO_NAME;
    String REPO_PATH = Config.REPO_PATH;
    String COMMIT_ID = Config.COMMIT_ID;
    String JRE_PATH = Config.JRE_PATH;
    String TEMP_DIR = Config.TEMP_DIR; // temp folder to collect diff files

    RepoAnalyzer repoAnalyzer = new RepoAnalyzer(REPO_NAME, REPO_PATH);
    repoAnalyzer.analyzeCommit(COMMIT_ID);

    String uuid = UUID.randomUUID().toString().replaceAll("-", "");

    Gson g = new GsonBuilder().create();
    String str = g.toJson(repoAnalyzer.getDiffHunks().get(0));

    // find the AST nodes covered by each diff hunk
    JDTParser jdtParser = new JDTParser(REPO_PATH, JRE_PATH);
    for (DiffFile diffFile : repoAnalyzer.getDiffFiles()) {
      if (diffFile.getFileType().equals(FileType.JAVA)) {
        // get all diff hunks within this file
        List<DiffHunk> diffHunksContainCode =
            diffFile.getDiffHunks().stream()
                .filter(diffHunk -> diffHunk.containsCode())
                .collect(Collectors.toList());
        // parse the changed files into ASTs
        Pair<CompilationUnit, CompilationUnit> CUPair = jdtParser.generateCUPair(diffFile);

        // use gumtree to compare change stem subtrees
        if (CUPair.getRight() != null) {
          CompilationUnit cu = CUPair.getRight();
          for (DiffHunk diffHunk : diffHunksContainCode) {
            List<ASTNode> coveredNodes = new ArrayList<>();
            // find nodes covered or covering by each diff hunk
            int startPos = cu.getPosition(diffHunk.getCurrentStartLine(), 0);
            int endPos = cu.getPosition(diffHunk.getCurrentEndLine() + 1, 0);
            int length = endPos - startPos;
            if (length > 0) {
              MyNodeFinder nodeFinder = new MyNodeFinder(cu, startPos, length);
              List<ASTNode> nodes = nodeFinder.getCoveredNodes();
              for (ASTNode node : nodes) {
                while (node != null
                    && !(node instanceof Statement || node instanceof BodyDeclaration)) {
                  node = node.getParent();
                }
                coveredNodes.add(node);
                //                System.out.println(node);
              }
            }
            //              if (node != null) {
            //                //                node = node.getParent();
            //                // collect data and control flow symbols
            //                //                IdentifierVisitor v = new IdentifierVisitor();
            //                SimpleVisitor v = new SimpleVisitor();
            //                node.accept(v);
            //                //                v.getInvokedMethods();
            //                diffHunk.setSimpleTypes(v.getSimpleTypes());
            //                diffHunk.setSimpleNames(v.getSimpleNames());
            //              }
            //            }
          }
        }
      }
    }
    // compute the distance matrix
    // build the graph
    for (DiffHunk diffHunk : repoAnalyzer.getDiffHunks()) {}

    // visualize the graph
  }
}
