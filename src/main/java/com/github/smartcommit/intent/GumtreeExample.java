package com.github.smartcommit.intent;

import com.github.gumtreediff.actions.ActionClusterFinder;
import com.github.gumtreediff.actions.ChawatheScriptGenerator;
import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.matchers.SimilarityMetrics;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.ASTParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class GumtreeExample {
  public static void main(String[] args) throws IOException {
    String body1 = " double similarity = 0D; if(similarity == 0) return similarity;";
    String body2 = " double similarity = 0D; if(similarity != 0) return similarity;";
    System.out.println("the score is ");
    System.out.println(bodyAST(body1, body2));

    GumtreeExample example = new GumtreeExample();
    System.out.println(example.getCurrentFilePath());

    String projectPath = System.getProperty("user.dir");
    String fileRelativePath1 = "src/main/java/com/github/smartcommit/intent/GumtreeExample.java";
    String fileRelativePath2 = "src/main/java/com/github/smartcommit/intent/LabelCollector.java";
    EditScript editScript =
        generateEditScript(
            FileUtils.readFileToString(new File(fileRelativePath1)),
            FileUtils.readFileToString(new File(fileRelativePath2)));
    for (Iterator iter = editScript.iterator(); iter.hasNext(); ) {
      Action action = (Action) iter.next();
      System.out.println(action);
    }
  }

  /**
   * Compute the similarity of two method bodies
   *
   * @param body1
   * @param body2
   * @return
   */
  public static double bodyAST(String body1, String body2) {
    double similarity = 0D;
    try {
      JdtTreeGenerator generator = new JdtTreeGenerator();
      generator.setKind(ASTParser.K_STATEMENTS);
      TreeContext baseContext = generator.generateFrom().string(body1);
      TreeContext othersContext = generator.generateFrom().string(body2);
      ITree baseRoot = baseContext.getRoot();
      ITree othersRoot = othersContext.getRoot();
      Matcher matcher = Matchers.getInstance().getMatcher();
      MappingStore mappings = matcher.match(baseRoot, othersRoot);
      similarity = SimilarityMetrics.diceSimilarity(baseRoot, othersRoot, mappings);
      if (Double.isNaN(similarity)) {
        similarity = 0D;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return similarity;
  }

  /**
   * Compute edit script between two compilation units
   *
   * @param oldContent
   * @param newContent
   * @return
   */
  private static EditScript generateEditScript(String oldContent, String newContent) {
    JdtTreeGenerator generator = new JdtTreeGenerator();
    //        Generators generator = Generators.getInstance();
    try {
      TreeContext oldContext = generator.generateFrom().string(oldContent);
      TreeContext newContext = generator.generateFrom().string(newContent);
      Matcher matcher = Matchers.getInstance().getMatcher();

      MappingStore mappings = matcher.match(oldContext.getRoot(), newContext.getRoot());
      EditScript editScript = new ChawatheScriptGenerator().computeActions(mappings);
      return editScript;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Compute clustered actions between two compilation units
   *
   * @param oldContent
   * @param newContent
   * @return
   */
  public static List<Set<Action>> generateActionClusters(String oldContent, String newContent) {
    List<Set<Action>> actionClusters = new ArrayList<>();
    JdtTreeGenerator generator = new JdtTreeGenerator();
    //        Generators generator = Generators.getInstance();
    try {
      TreeContext oldContext = generator.generateFrom().string(oldContent);
      TreeContext newContext = generator.generateFrom().string(newContent);
      Matcher matcher = Matchers.getInstance().getMatcher();

      MappingStore mappings = matcher.match(oldContext.getRoot(), newContext.getRoot());
      EditScript editScript = new ChawatheScriptGenerator().computeActions(mappings);

      ActionClusterFinder finder = new ActionClusterFinder(oldContext, newContext, editScript);
      actionClusters = finder.getClusters();

    } catch (Exception e) {
      e.printStackTrace();
    }
    return actionClusters;
  }

  /**
   * Get the absolute path of the current java file
   *
   * @return
   */
  private String getCurrentFilePath() {
    return this.getClass().getResource("").getPath();
  }
}
