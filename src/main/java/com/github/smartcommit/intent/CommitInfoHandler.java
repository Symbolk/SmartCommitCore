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
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

// GumtreeExample
import com.github.gumtreediff.actions.EditScript;
import com.github.smartcommit.model.DiffFile;

// MongoExample
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

// RefactoringMiner
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.lib.*;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

// CommitTrainningSample
import com.github.smartcommit.intent.model.CommitTrainningSample;
import com.github.smartcommit.core.*;
import com.github.smartcommit.intent.model.*;
import org.omg.CORBA.INTF_REPOS;


// Main Class: Commit message:  Get, Label and Store
public class CommitInfoHandler {
    public static void main(String[] args) {
        args = new String[]{"/Users/Chuncen/Desktop/RefactoringMiner", "commitTrainingSample"};
        String repoPath = args[0];
        String collectionName = args[1];
        // CommitTrainningSample
        List<CommitTrainningSample> commitsInfo = new ArrayList<>();

        try {
            CommitsCollector(repoPath, commitsInfo);
            MongoDatabase database = MongoDBUtil.getConnection("localhost", "27017", "commitsDB");
            MongoCollection<Document> collection = database.getCollection(collectionName);
            trainningSampleAnalyzer(repoPath, commitsInfo, collection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Split "git commit"
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

    // get IntentList and ActionList
    public static boolean trainningSampleAnalyzer(String repoPath, List<CommitTrainningSample> commitTrainningSample,
                                                  MongoCollection<Document> collection) throws Exception {

        // get the final dir name as repoName, thus generate repoID using hash
        int index = repoPath.lastIndexOf(File.separator);
        String repoName = repoPath.substring(index + 1);
        String repoID = String.valueOf(Math.abs(repoName.hashCode()));

        // Analyze the Sample List
        Integer size = commitTrainningSample.size();
        for (int i = 0; i < size; i++) {
            CommitTrainningSample tempCommitTrainningSample = commitTrainningSample.get(i);
            String commitID = tempCommitTrainningSample.getCommitID();
            System.out.println("Proceeding: "+commitID+"  "+i+"/"+size);
            tempCommitTrainningSample.setRepoID(repoID);
            tempCommitTrainningSample.setRepoPath(repoPath);
            tempCommitTrainningSample.setRepoName(repoName);


            String commitMsg = tempCommitTrainningSample.getCommitMsg();
            // get Intent from commitMsg
            Intent intent = getIntentFromMsg(commitMsg);
            tempCommitTrainningSample.setIntent(intent);

            // get List<IntentDescription> from commitMsg
            List<IntentDescription> intentList = getIntentDescriptionFromMsg(commitMsg);
            tempCommitTrainningSample.setIntentDescription(intentList);

            // add actionList using gumtree
            RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);
            tempCommitTrainningSample = generateActionListFromCodeChange(tempCommitTrainningSample, repoAnalyzer);

            // add refactorCodeChange using RefactoringMiner
            List<RefactorCodeChange> refactorCodeChanges = getRefactorCodeChangesFromCodeChange(repoPath, commitID);
            tempCommitTrainningSample.setRefactorCodeChanges(refactorCodeChanges);

            // Load into DB
            loadTrainSampleToDB(collection, tempCommitTrainningSample);
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
            //e.printStackTrace();
            // Failure to generate AbstractJdtTree because of the docChange instead of codeChange
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

    // generate action list from code changes: diffFile and EditScript
    private static CommitTrainningSample generateActionListFromCodeChange(
            CommitTrainningSample tempCommitTrainningSample, RepoAnalyzer repoAnalyzer) {
        try {  // if no FileChange
            List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(tempCommitTrainningSample.getCommitID());
            // get EditScript from diffFiles, and get ActionList from EditScript
            List<Action> tempActionList = new ArrayList<>();
            Integer sizeDiff = diffFiles.size();
            for (int j = 0; j < sizeDiff; j++) {
                String baseContent = diffFiles.get(j).getBaseContent();
                String currentContent = diffFiles.get(j).getCurrentContent();
                // File added or deleted, thus no content
                if (baseContent == null || baseContent.equals("") || currentContent == null || currentContent.equals("")) {
                    tempCommitTrainningSample.addIntentDescription(IntentDescription.FIL);
                    // tempCommitTrainningSample.setIntent(Intent.FIL);
                    System.out.println("Exception type: NCC: only FILE change");
                    continue;
                }
                EditScript editScript = generateEditScript(baseContent, currentContent);
                if (editScript != null) {
                    List<Action> actionList = generateActionList(editScript);
                    tempActionList.addAll(actionList);
                    tempCommitTrainningSample.setActionList(tempActionList);
                } else {
                    // Only doc change, thus no CodeChange and AbstractJdtTree generated
                    tempCommitTrainningSample.addIntentDescription(IntentDescription.DOC);
                    //tempCommitTrainningSample.setIntent(Intent.DOC);
                    System.out.println("Exception type: NCC: only DOC change");
                }
            }
        } catch (Exception e) {
            //e.printStackTrace();
            // Lack of File, thus no DiffFiles generated
            tempCommitTrainningSample.addIntentDescription(IntentDescription.NFL);
            //tempCommitTrainningSample.setIntent(Intent.FIL);
            System.out.println("Exception type: NoFile");
        }
        return tempCommitTrainningSample;
    }

    // generate Intent from Message
    private static Intent getIntentFromMsg(String commitMsg) {
        for (Intent intent : Intent.values()) {
            if (commitMsg.contains(intent.label)) {
                return intent;
            }
        }
        return Intent.CHR;
    }

    // generate IntentDescription from Message
    private static List<IntentDescription> getIntentDescriptionFromMsg(String commitMsg) {
        List<IntentDescription> intentList = new ArrayList<>();
        for (IntentDescription intent : IntentDescription.values()) {
            if (commitMsg.toLowerCase().contains(intent.label)) {
                intentList.add(intent);
            }
        }
        return intentList;
    }

    // generate RefactorCodeChangeFromCodeChagne
    private static List<RefactorCodeChange> getRefactorCodeChangesFromCodeChange(String repoPath, String commitID) {
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
        org.refactoringminer.api.GitService gitService = new GitServiceImpl();
        List<RefactorCodeChange> refactorCodeChanges = new ArrayList<>();
        try {
            Repository repo = gitService.cloneIfNotExists(
                    repoPath, // "/Users/Chuncen/Downloads/"+repoName
                    "https://github.com/danilofes/refactoring-toy-example.git");
            miner.detectAtCommit(repo, commitID, new RefactoringHandler() {
                @Override
                public void handle(String commitId, List<Refactoring> refactorings) {
                    // System.out.println("Refactorings at " + commitId);
                    if(refactorings.isEmpty()) {
                        System.out.println("No refactoring generated");
                    } else {
                        for (Refactoring ref : refactorings) {
                            RefactorCodeChange refactorCodeChange = new RefactorCodeChange(ref.getRefactoringType(), ref.getName());
                            refactorCodeChanges.add(refactorCodeChange);
                        }
                    }
                }
            });
        } catch (Exception e) {
            System.out.println("Repo Not Exist");
            e.printStackTrace();
        }
        return refactorCodeChanges;
    }

    // Load given commitTrainingSample into given DB collection
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
            doc1.put("commitIntent", commitTrainningSample.getIntent().getLabel());
            doc1.put("commitIntentDescription", String.valueOf(commitTrainningSample.getIntentDescription()));
            // add ActionList to DB
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
            }
            // add refactorCodeChange to DB
            List<RefactorCodeChange> refactorCodeChangeList = commitTrainningSample.getRefactorCodeChanges();
            if (refactorCodeChangeList != null) {
                List<Document> refactorCodeChanges = new ArrayList<>();
                for (RefactorCodeChange refactorCodeChange : refactorCodeChangeList) {
                    Document addrAttr = new Document();
                    addrAttr.put("operation", refactorCodeChange.getOperation());
                    addrAttr.put("refactoringType", String.valueOf(refactorCodeChange.getRefactoringType()));
                    refactorCodeChanges.add(addrAttr);
                }
                doc1.put("refactorCodeChanges", refactorCodeChanges);
            }
            collection.insertOne(doc1);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}