package com.github.smartcommit.intent;

import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.matchers.SimilarityMetrics;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import org.eclipse.jdt.core.dom.ASTParser;

import java.io.IOException;

public class Example {
    public static void main(String[] args) {
        String body1 = " double similarity = 0D; return similarity;";
        String body2 = " double similarity = 0D; if(similarity != 0) {return similarity;}";
        System.out.println(bodyAST(body1, body2));
    }

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
}
