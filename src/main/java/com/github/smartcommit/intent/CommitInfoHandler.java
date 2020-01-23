package com.github.smartcommit.intent;

// DataCollector

import com.github.gumtreediff.actions.ChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.*;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.TreeContext;
import com.github.smartcommit.intent.model.Action;
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

// GumtreeExample

import com.github.gumtreediff.actions.EditScript;
import com.github.smartcommit.model.DiffFile;

// MongoExample
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

// CommitInfo
import com.github.smartcommit.intent.model.CommitTrainningSample;
import com.github.smartcommit.core.*;
import com.github.smartcommit.intent.model.*;


// Main Class: Commit message:  Get, Label, Store
public class CommitInfoHandler {
    public static void main(String[] args) {
        args = new String[]{"D:\\1-TestingData\\google-http-java-client", "commitTrainningSample"};
        String repoPath = args[0];
        String collectionName = args[1];
        // CommitInfo
        List<CommitTrainningSample> commitsInfo = new ArrayList<>();

        /*System.out.println("CommitsCollector? " + CommitsCollector(REPO_DIR, commitsInfo));
        System.out.println("LabelExtractor? " + LabelExtractor(REPO_DIR, REPO_NAME, commitsInfo));
        System.out.println("DBLoader? " + DBLoader(REPO_NAME, commitsInfo));*/

        try {
            CommitsCollector(repoPath, commitsInfo);
            MongoDatabase database = MongoDBUtil.getConnection("localhost", "27017", "commitsDB");
            MongoCollection<Document> collection = database.getCollection(collectionName);
            trainningSampleAnalyzer(repoPath, commitsInfo, collection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Let's Gumtree
    public static boolean CommitsCollector(String REPO_DIR, List<CommitTrainningSample> commitTrainningSample) {
        GitService gitService = new GitServiceCGit();
        String log = Utils.runSystemCommand(REPO_DIR, "git", "log");
        String parts[] = log.split("\\ncommit "), body[];
        parts[0] = parts[0].substring("commit ".length());
        for (String part : parts) {
            List<String> tempList = new ArrayList<String>();
            CommitTrainningSample tempCommitTrainningSample = new CommitTrainningSample();
            body = part.split("\\nAuthor: | <|>\\nDate:   |\\n\\n  ");
            // String commitID
            tempCommitTrainningSample.setCommitID(body[0]);
            // String committer
            tempCommitTrainningSample.setCommitter(body[1]);
            // String committerEmail
            tempCommitTrainningSample.setCommitEmail(body[2]);
            // String commitTime
            tempCommitTrainningSample.setCommitTime(body[3]);
            // String commitMsg
            tempCommitTrainningSample.setCommitMsg(body[4]);
            // Add into List
            commitTrainningSample.add(tempCommitTrainningSample);
        }
        return true;
    }


    public static boolean trainningSampleAnalyzer(String repoPath, List<CommitTrainningSample> commitTrainningSample, MongoCollection<Document> collection) {

        int index = repoPath.lastIndexOf(File.separator);
        String repoName = repoPath.substring(index + 1);
        String repoID = String.valueOf(Math.abs(repoName.hashCode()));

        Integer size = commitTrainningSample.size();
        for (int i = 0; i < size; i++) {
            CommitTrainningSample tempCommitTrainningSample = commitTrainningSample.get(i);
            tempCommitTrainningSample.setRepoID(repoID);
            tempCommitTrainningSample.setRepoPath(repoPath);
            tempCommitTrainningSample.setRepoName(repoName);
            String commitMsg = tempCommitTrainningSample.getCommitMsg();

            Intent intent = getIntentFromMsg(commitMsg);
            tempCommitTrainningSample.setIntent(intent);

            String commitID = tempCommitTrainningSample.getCommitID();
            // RepoAnalyzer

            RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);

            List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(commitID);

            List<Action> tempActionList = new ArrayList<>();
            Integer sizeDiff = diffFiles.size();
            for (int j = 0; j < sizeDiff; j++) {
                String baseContent = diffFiles.get(j).getBaseContent();
                String currentContent = diffFiles.get(j).getCurrentContent();
                if (baseContent == null || baseContent.equals("") || currentContent == null || currentContent.equals("")) {
                    continue;
                }
                try {
                    EditScript editScript = generateEditScript(baseContent, currentContent);
                    if (editScript != null) {
                        List<Action> actionList = generateActionList(editScript);
                        tempActionList.addAll(actionList);
                        tempCommitTrainningSample.setActionList(tempActionList);
                    }
                } catch (Exception e) {
                    System.out.println("\n Exception in the " + i + "th commit of " + repoName + "\n");
                    e.printStackTrace();
                }
            }
            //commitTrainningSample.set(i, tempCommitTrainningSample);
            loadTrainSampleToDB(collection, tempCommitTrainningSample);
            System.out.println("Document inserted successfully in the " + i + "th commit of " + repoName);
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
    private static List<Action> generateActionList(EditScript editScript) {
        List<Action> actionList = new ArrayList<>();
        for (Iterator iter = editScript.iterator(); iter.hasNext(); ) {
            com.github.gumtreediff.actions.model.Action action = (com.github.gumtreediff.actions.model.Action) iter.next();
            ASTOperation ASTOperation = null;
            if (action instanceof Insert) {
                ASTOperation = ASTOperation.ADD;
            } else if (action instanceof Delete) {
                ASTOperation = ASTOperation.DEL;
            } else if (action instanceof Move) {
                ASTOperation = ASTOperation.MOV;
            } else if (action instanceof Update) {
                ASTOperation = ASTOperation.UPD;
            }
            Action myAction = new Action(ASTOperation, action.getNode().getType().toString());
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


    public static boolean DBLoader(String REPO_NAME, List<CommitTrainningSample> commitTrainningSample) {
        MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
        MongoClient mongoClient = new MongoClient(connectionString);
        MongoDatabase commitsDB = mongoClient.getDatabase("commitsDB");
        MongoCollection<Document> repoCol = commitsDB.getCollection(REPO_NAME);
        repoCol.drop();
        repoCol = commitsDB.getCollection(REPO_NAME);


        // Actions stored in seperated collection part1
        MongoDatabase ActionLists = mongoClient.getDatabase("ActionLists");
        MongoCollection<Document> actionListsCollection = ActionLists.getCollection(REPO_NAME + "ActionLists");
        actionListsCollection.drop();
        actionListsCollection = ActionLists.getCollection(REPO_NAME + "ActionLists");

        Integer size = commitTrainningSample.size();
        for (int i = 0; i < size; i++) {
            // key:value
            Document tempDoc = new Document("repo_name", REPO_NAME);
            CommitTrainningSample tempCommitTrainningSample = commitTrainningSample.get(i);
            List<Action> actionList = tempCommitTrainningSample.getActionList();
            String ActionString = generateStringFromActionList(actionList);
            Intent intent = tempCommitTrainningSample.getIntent();
            String commitIntent = generateStringFromIntent(intent);
            tempDoc
                    .append("commitID", tempCommitTrainningSample.getCommitID())
                    .append("commitMsg", tempCommitTrainningSample.getCommitMsg())
                    .append("committer", tempCommitTrainningSample.getCommitter())
                    .append("committerEmail", tempCommitTrainningSample.getCommitterEmail())
                    .append("commitTime", tempCommitTrainningSample.getCommitTime())
                    .append("commitActionList", ActionString)
                    .append("commitIntent", commitIntent)
            ;
            repoCol.insertOne(tempDoc);

            // Actions stored in seperated collection part2
            Document actionDocument = new Document("repo_name", REPO_NAME + "Actions");
            Integer sizeActionList = actionList.size();
            for (int j = 0; j < sizeActionList; j++) {
                actionDocument.append("action " + j + " in commits " + i, actionList.get(j).getASTOperation());
            }
            actionListsCollection.insertOne(actionDocument);
        }

        mongoClient.close();
        return true;
    }

    // Convert ActionList to String
    private static String generateStringFromActionList(List<Action> actions) {
        Integer size = actions.size();
        String str = " ";
        for (int i = 0; i < size; i++) {
            str += actions.get(i).getASTOperation() + "、";
        }
        return str;
    }

    // Convert Intent to String
    private static String generateStringFromIntent(Intent intent) {
        return intent.getLabel();
    }

    private static void loadTrainSampleToDB(MongoCollection<Document> collection, CommitTrainningSample commitTrainningSample) {
        try {
            Document doc1 = new Document();
            doc1.put("repoID", commitTrainningSample.getRepoID());
            doc1.put("repoPath", commitTrainningSample.getRepoPath());
            doc1.put("repoName", commitTrainningSample.getRepoName());
            doc1.put("commitID", commitTrainningSample.getCommitID());
            doc1.put("commitMsg", commitTrainningSample.getCommitMsg());
            doc1.put("committer", commitTrainningSample.getCommitter());
            doc1.put("committerEmail", commitTrainningSample.getCommitterEmail());
            doc1.put("commitTime", commitTrainningSample.getCommitTime());
            doc1.put("commitIntent", String.valueOf(commitTrainningSample.getIntent()));
            List<Action> actionList = commitTrainningSample.getActionList();
            if (actionList != null) {
                List<Document> actions = new ArrayList<>();
                for (Action action : actionList) {
                    Document addrAttr = new Document();
                    addrAttr.put("operation", String.valueOf(action.getASTOperation()));
                    addrAttr.put("astNodeType", action.getASTNodeType());
                    actions.add(addrAttr);
                }
                doc1.put("actions", actions);
                collection.insertOne(doc1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
