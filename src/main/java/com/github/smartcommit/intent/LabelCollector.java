package com.github.smartcommit.intent;

// DataCollector
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import java.io.File;
import java.util.ArrayList;

// Example
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.matchers.SimilarityMetrics;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import org.eclipse.jdt.core.dom.ASTParser;
import java.io.IOException;

// MongoExample
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;


// Get commit message from a specific commit
public class LabelCollector {
    public static void main(String[] args) {
        String REPO_NAME = "guava";
        String REPO_DIR = "/Users/Chuncen/IdeaProjects/" + REPO_NAME;
        String commitID = "f4b3f611c4e49ecaded58dcb49262f55e56a3322";
        String log, body1, body2;
        System.out.println(getLog(REPO_NAME, REPO_DIR, commitID));
        System.out.println(gumtree(REPO_NAME, REPO_DIR, commitID));
        System.out.println(mongodb(REPO_NAME, REPO_DIR, commitID));
    }
    public static boolean getLog(String REPO_NAME, String REPO_DIR, String commitID) {
        GitService gitService = new GitServiceCGit();
        //String log = Utils.runSystemCommand(REPO_DIR, "git", "log", "-oneline");
        String log = Utils.runSystemCommand(REPO_DIR, "git", "log", "--pretty=oneline");
        String lines[], body[];
        lines = log.split("\\r?\\n");
        List<String> commitLog =new ArrayList<String>();
        List<String> commitIDs =new ArrayList<String>();
        List<String> commitMessages =new ArrayList<String>();
        for (String line : lines){
            commitLog.add(line);
            body = line.split(" ");
            commitIDs.add(body[0]);
            commitMessages.add(body[1]);
        }
        System.out.println(commitIDs.get(0));
        return true;
    }
    public static boolean gumtree(String REPO_NAME, String REPO_DIR, String commitID) {
        try{
            double similarity = 0D;
            JdtTreeGenerator generator = new JdtTreeGenerator();
            generator.setKind(ASTParser.K_STATEMENTS);
            TreeContext baseContext = generator.generateFrom().string(commitID);
            TreeContext othersContext = generator.generateFrom().string(commitID);
            ITree baseRoot = baseContext.getRoot();
            ITree othersRoot = othersContext.getRoot();
            Matcher matcher = Matchers.getInstance().getMatcher();
            MappingStore mappings = matcher.match(baseRoot, othersRoot);
            similarity = SimilarityMetrics.diceSimilarity(baseRoot, othersRoot, mappings);
            if(Double.isNaN(similarity)){
                System.out.println("NaN");
            } else {
                System.out.println("Similarity: "+ similarity);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean mongodb(String REPO_NAME, String REPO_DIR, String commitID) {
        MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
        MongoClient mongoClient = new MongoClient(connectionString);
        MongoDatabase sampleDB = mongoClient.getDatabase("samples");
        MongoCollection<Document> repoCol = sampleDB.getCollection(REPO_NAME);
        Document sampleDoc = new Document("repo_name", REPO_NAME);
        return true;
    }
}

// read from json
// https://www.cnblogs.com/dwb91/p/6726823.html

// read from txt
// https://blog.csdn.net/xuehyunyu/article/details/77873420

// into Mongodb
// https://github.com/Symbolk/IntelliMerge/blob/f4b5166abbd7dffc2040b819670dad31a6b89ae0/src/main/java/edu/pku/intellimerge/evaluation/Evaluator.java#L49
