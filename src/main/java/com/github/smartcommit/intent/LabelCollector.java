package com.github.smartcommit.intent;

// DataCollector
import com.github.smartcommit.model.DiffFile;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.FileStatus;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;
import java.io.File;
import java.util.List;
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


// Commit message:  Get, Label, Store
public class LabelCollector {
    public static void main(String[] args) {
        String REPO_NAME = "guava";
        String REPO_DIR = "/Users/Chuncen/IdeaProjects/" + REPO_NAME;
        String commitID = "f4b3f611c4e49ecaded58dcb49262f55e56a3322";
        List<String> commitLog =new ArrayList<String>();
        List<String> commitIDs =new ArrayList<String>();
        List<String> commitAuthors =new ArrayList<String>();
        List<String> commitDates =new ArrayList<String>();
        List<String> commitMessages =new ArrayList<String>();
        List<String> commitIndents =new ArrayList<String>();
        System.out.println("Get? " + getLog(REPO_DIR, commitLog, commitIDs, commitAuthors, commitDates, commitMessages));
        System.out.println("Label? " + gumtree(REPO_NAME, REPO_DIR, commitID));
        System.out.println("Store? " + mongodb(REPO_NAME, REPO_DIR, commitLog, commitIDs));
    }
    // Get All commit Messages
    public static boolean getLog(String REPO_DIR, List commitLog,
                                 List commitIDs, List commitAuthors, List commitDates,  List commitMessages) {
        GitService gitService = new GitServiceCGit();

        // Just get ID and msg
        // String log = Utils.runSystemCommand(REPO_DIR, "git", "log", "--oneline");
        /*
        String log = Utils.runSystemCommand(REPO_DIR, "git", "log", "--pretty=oneline");
        String lines[] = log.split("\\r?\\n"), body[];
        for (String line : lines) {
            commitLog.add(line);
            body = line.split(" ");
            commitIDs.add(body[0]);
            commitMessages.add(body[1]);
        }

         */
        //System.out.println("Log0: " + commitIDs.get(0));

        // get all the commit details
        String log = Utils.runSystemCommand(REPO_DIR, "git", "log");
        String parts[] = log.split("\\ncommit"), body[];
        for (String part : parts) {
            commitLog.add(part);
            body = part.split("\\nAuthor:|\\nDate:|\\n\\n");
            commitIDs.add(body[0]);
            commitAuthors.add(body[1]);
            commitDates.add(body[2]);
            commitMessages.add(body[3]);
        }
        //System.out.println("Log0: " + commitMessages.get(1));
        return true;
    }
    // Cluster to get label using gumtree
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

    // Store the label and message into mongodb
    public static boolean mongodb(String REPO_NAME, String REPO_DIR, List<String> commitLog, List<String> commitIDs) {
        MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
        MongoClient mongoClient = new MongoClient(connectionString);
        MongoDatabase sampleDB = mongoClient.getDatabase("samples");
        MongoCollection<Document> repoCol = sampleDB.getCollection(REPO_NAME);
        Integer size = commitIDs.size();
        for(int i = 0 ; i < size ; i++) {
            //System.out.println(commitIDs.get(i));
            // key:value
            Document sampleDoc = new Document("repo_name", REPO_NAME);
            sampleDoc
                    .append("commit_id", commitIDs.get(i))
                    .append("commit_msg", commitLog.get(i))
            //        .append("author", "non-known")
            //        .append("email", "non-known")
            //        .append("intent", "non-known")
            ;
            repoCol.insertOne(sampleDoc);
        }
        // if simply read from json
        /*
        GitService gitService = new GitServiceCGit();
        // mongoimport -d 数据库名 -c 数据表  --type json --file D:\data.json
        String log = Utils.runSystemCommand(REPO_DIR, "mongoimport", "-d", "sampleDB",
                "-c", "repoCol", "--type", "json", "--file", "commit-all.json");
        */
        mongoClient.close();
        return true;
    }
}

// read from json
// https://www.cnblogs.com/dwb91/p/6726823.html

// read from txt
// https://blog.csdn.net/xuehyunyu/article/details/77873420

// into Mongodb
// https://github.com/Symbolk/IntelliMerge/blob/f4b5166abbd7dffc2040b819670dad31a6b89ae0/src/main/java/edu/pku/intellimerge/evaluation/Evaluator.java#L49
