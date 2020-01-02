package com.github.smartcommit.intent;

// DataCollector
import com.github.gumtreediff.actions.ChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.*;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.TreeContext;
import com.github.smartcommit.io.DataCollector;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;

// GumtreeExample

import com.github.gumtreediff.actions.EditScript;
import com.github.smartcommit.intent.GumtreeExample;
import com.github.smartcommit.model.DiffFile;

// MongoExample
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;

// CommitInfo
import com.github.smartcommit.intent.model.CommitInfo;
import com.github.smartcommit.core.*;
import com.github.smartcommit.intent.model.*;
import sun.jvm.hotspot.debugger.win32.coff.COMDATSelectionTypes;

import javax.lang.model.type.IntersectionType;




// Main Class: Commit message:  Get, Label, Store
public class CommitLog_GetLabelStore {
    public static void main(String[] args) {
        String REPO_NAME = "guava";
        String REPO_DIR = "/Users/Chuncen/IdeaProjects/" + REPO_NAME;
        String commitID = "f4b3f611c4e49ecaded58dcb49262f55e56a3322";
        //DataCollector dataCollector = new DataCollector(REPO_NAME, REPO_DIR);

        // List<List<>>
        List<List<String>> commits = new ArrayList<List<String>>();
        // CommitInfo
        List<CommitInfo> commitsInfo = new ArrayList<>();
        /*
        System.out.println("getIt? " + getIt(REPO_DIR, commits));
        System.out.println("labelIt? " + labelIt(REPO_DIR, commits));
        System.out.println("storeIt? " + storeIt(REPO_NAME, commits));
        */

        System.out.println("CommitsCollector? " + CommitsCollector(REPO_DIR, commitsInfo));
        System.out.println("LabelExtractor? " + LabelExtractor(REPO_DIR, REPO_NAME, commitsInfo));
        System.out.println("DBLoader? " + DBLoader(REPO_NAME, commitsInfo));

    }

    // Get All commit Messages
    public static boolean getIt(String REPO_DIR, List<List<String>> commits) {
        GitService gitService = new GitServiceCGit();
        // get all the commit details
        String log = Utils.runSystemCommand(REPO_DIR, "git", "log");
        String parts[] = log.split("\\ncommit"), body[];
        for (String part : parts) {
            List<String> tempList = new ArrayList<String>();
            //tempList.add(part);
            body = part.split("\\nAuthor:|\\nDate:|\\n\\n");
            // commit_ID
            tempList.add(body[0]);
            // commit_Author
            tempList.add(body[1]);
            // commit_Date
            tempList.add(body[2]);
            // commit_msg
            tempList.add(body[3]);
            // commit_intent
            tempList.add("unsure");
            // add list1 into list2
            commits.add(tempList);
        }
        return true;
    }
    // Cluster to get label simply matching
    public static boolean labelIt(String REPO_DIR, List<List<String>> commits) {
        try {
            List<String> intents;
            intents = FileUtils.readLines(new File(REPO_DIR +File.separator+"intents-selected.txt"));
            for (List<String> commit : commits) {
                for (String intent : intents) {
                    String msg = commit.get(0);
                    if(msg.toLowerCase().contains(intent)) {
                         commit.add(intent);
                         break;
                    }
                    else commit.add("unknown");
                }

            }
       }
        catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
    // Store the label and message into mongodb
    public static boolean storeIt(String REPO_NAME, List<List<String>> commits) {
        MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
        MongoClient mongoClient = new MongoClient(connectionString);

        MongoDatabase commitsDB = mongoClient.getDatabase("commits");
        MongoCollection<Document> repoCol = commitsDB.getCollection(REPO_NAME);
        Integer size = commits.size();
        for(int i = 0 ; i < size ; i++) {
            // key:value
            Document tempDoc = new Document("repo_name", REPO_NAME);
            tempDoc
                    .append("commit_log", commits.get(i).get(0))
                    .append("commit_ID", commits.get(i).get(1))
                    .append("commit_Author", commits.get(i).get(2))
                    .append("commit_Date", commits.get(i).get(3))
                    .append("commit_msg", commits.get(i).get(4))
                    .append("commit_intent", commits.get(i).get(5))
            ;
            repoCol.insertOne(tempDoc);
        }
        mongoClient.close();
        return true;
    }


    // Let's Gumtree
    public static boolean CommitsCollector(String REPO_DIR, List<CommitInfo> commitInfo) {
        GitService gitService = new GitServiceCGit();
        String log = Utils.runSystemCommand(REPO_DIR, "git", "log");
        String parts[] = log.split("\\ncommit "), body[];
        parts[0] = parts[0].substring("commit ".length());
        for (String part : parts) {
            List<String> tempList = new ArrayList<String>();
            CommitInfo tempCommitInfo = new CommitInfo();
            body = part.split("\\nAuthor: | <|>\\nDate:   |\\n\\n  ");
            // String commitID
            tempCommitInfo.setCommitID(body[0]);
            // String committer
            tempCommitInfo.setCommitter(body[1]);
            // String committerEmail
            tempCommitInfo.setCommitEmail(body[2]);
            // String commitTime
            tempCommitInfo.setCommitTime(body[3]);
            // String commitMsg
            tempCommitInfo.setCommitMsg(body[4]);
            // Add into List
            commitInfo.add(tempCommitInfo);
        }
        return true;
    }
    public static boolean LabelExtractor(String REPO_DIR, String REPO_NAME, List<CommitInfo> commitInfo) {
        Integer size = commitInfo.size();
        for(int i = 10 ; i < 12 ; i++) {
            CommitInfo tempCommitInfo = commitInfo.get(i);
            String commitMsg = tempCommitInfo.getCommitMsg();

            Intent intent = getIntentFromMsg(commitMsg);
            tempCommitInfo.setIntent(intent);

            String commitID = tempCommitInfo.getCommitID();
            // RepoAnalyzer
            RepoAnalyzer repoAnalyzer = new RepoAnalyzer(REPO_NAME, REPO_DIR);
            List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(commitID);

            List<MyAction> tempActionList = new ArrayList<>();
            Integer sizeDiff = diffFiles.size();
            for(int j = 0 ; j < sizeDiff ; j++) {
                String baseContent = diffFiles.get(j).getBaseContent();
                String currentContent = diffFiles.get(j).getCurrentContent();
                try {
                    EditScript editScript = generateEditScript(baseContent, currentContent);
                    List<MyAction> actionList = generateActionList(editScript);
                    tempActionList.addAll(actionList);
                } catch (Exception e) {
                    System.out.println("\n Exception in the "+i+ "th commit of "+REPO_NAME+"\n");
                    e.printStackTrace();
                }
            }

            tempCommitInfo.setActionList(tempActionList);
            commitInfo.set(i, tempCommitInfo);
        }
        return true;
    }
    // generate Edit Script from file contents
    private static EditScript generateEditScript(String baseContent, String currentContent) {
        JdtTreeGenerator generator = new JdtTreeGenerator();
        try {
            TreeContext oldContext = generator.generateFrom().string(baseContent);
            TreeContext newContext = generator.generateFrom().string(currentContent);

            Matcher matcher = Matchers.getInstance().getMatcher();
            MappingStore mappings = matcher.match(oldContext.getRoot(), newContext.getRoot());
            EditScript editScript = new ChawatheScriptGenerator().computeActions(mappings);
            return editScript;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }
    // generate commit info from different file pathway
    private static List<MyAction> generateActionList(EditScript editScript) {

        List<MyAction> actionList = new ArrayList<>();
        for (Iterator iter = editScript.iterator(); iter.hasNext(); ) {
            Action action = (Action) iter.next();
            ActionType actionType = null;
            if (action instanceof Insert) {
                actionType = ActionType.ADD;
            } else if (action instanceof Delete) {
                actionType = ActionType.DEL;
            } else if (action instanceof Move) {
                actionType = ActionType.MOV;
            } else if (action instanceof Update) {
                actionType = ActionType.UPD;
            }
            MyAction myAction = new MyAction(actionType, action.getNode().getType().toString());
            actionList.add(myAction);
        }
        return actionList;
    }
    // generate Intent from Message
    private static Intent getIntentFromMsg(String commitMsg) {
        for (Intent intent : Intent.values()) {
            if (commitMsg.contains(intent.label)) {
                return intent;
            }
        }
        return Intent.UNKNOWN;
    }

    public static boolean DBLoader(String REPO_NAME, List<CommitInfo> commitInfo) {
        MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
        MongoClient mongoClient = new MongoClient(connectionString);
        MongoDatabase commitsDB = mongoClient.getDatabase("commitInfo");
        MongoCollection<Document> repoCol = commitsDB.getCollection(REPO_NAME);
        repoCol.drop();
        repoCol = commitsDB.getCollection(REPO_NAME);
        Integer size = commitInfo.size();
        for(int i = 10 ; i < 12 ; i++) {
            // key:value
            Document tempDoc = new Document("repo_name", REPO_NAME);
            CommitInfo tempCommitInfo = commitInfo.get(i);
            List<MyAction> actionList = tempCommitInfo.getActionList();
            String ActionString = generateStringFromActionList(actionList);
            Intent intent = tempCommitInfo.getIntent();
            String commitIntent = generateStringFromIntent(intent);
            tempDoc
                    .append("commitID", tempCommitInfo.getCommitID())
                    .append("commitMsg", tempCommitInfo.getCommitMsg())
                    .append("committer", tempCommitInfo.getCommitter())
                    .append("committerEmail", tempCommitInfo.getCommitterEmail())
                    .append("commitTime", tempCommitInfo.getCommitTime())
                    .append("commitActionList", ActionString)
                    .append("commitIntent", commitIntent)
            ;
            repoCol.insertOne(tempDoc);

        }
        mongoClient.close();
        return true;
    }
    // Convert ActionList to String
    private static String generateStringFromActionList(List<MyAction> myActions) {
        Integer size = myActions.size();
        String str = " ";
        for(int i = 0 ; i < size ; i++) {
            str += myActions.get(i).getActions()+"、";
        }
        return str;
    }
    // Convert Intent to String
    private static String generateStringFromIntent(Intent intent) {
        return intent.getLabel();
    }
}
