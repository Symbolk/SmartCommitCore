package com.github.smartcommit.intent;

// DataCollector

import com.github.gumtreediff.actions.ChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.*;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.TreeContext;
import com.github.smartcommit.intent.model.AstAction;
import com.github.smartcommit.model.DiffHunk;
import com.github.smartcommit.model.constant.Operation;
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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

// RefactoringMiner
import org.eclipse.jgit.lib.*;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

// CommitTrainingSample
import com.github.smartcommit.intent.model.CommitTrainingSample;
import com.github.smartcommit.core.*;
import com.github.smartcommit.io.*;
import com.github.smartcommit.intent.model.*;
import com.github.smartcommit.model.Action;

// Main Class: Commit message:  Get, Label and Store
public class CommitInfoHandler {
    public static void main(String[] args) {
        args = new String[]{"/Users/Chuncen/Desktop/refactoring-toy-example", "commitTrainingSample"};
        String repoPath = args[0];
        String collectionName = args[1];
        // CommitTrainingSample
        List<CommitTrainingSample> commitsInfo = new ArrayList<>();

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
    public static boolean CommitsCollector(String REPO_DIR, List<CommitTrainingSample> commitTrainingSample) {
        GitService gitService = new GitServiceCGit();
        String log = Utils.runSystemCommand(REPO_DIR, "git", "log");
        String parts[] = log.split("\\ncommit "), body[];
        parts[0] = parts[0].substring("commit ".length());
        for (String part : parts) {
            List<String> tempList = new ArrayList<String>();
            CommitTrainingSample tempCommitTrainingSample = new CommitTrainingSample();
            body = part.split("\\nAuthor: | <|>\\nDate:   |\\n\\n  ");
            // String commitID
            tempCommitTrainingSample.setCommitID(body[0].substring(0, 40));
            // String committer
            tempCommitTrainingSample.setCommitter(body[1]);
            // String committerEmail
            tempCommitTrainingSample.setCommitEmail(body[2]);
            // String commitTime
            tempCommitTrainingSample.setCommitTime(body[3]);
            // String commitMsg
            tempCommitTrainingSample.setCommitMsg(body[4]);
            // Add into List
            commitTrainingSample.add(tempCommitTrainingSample);
        }
        return true;
    }

    // get IntentList and ActionList
    public static boolean trainningSampleAnalyzer(String repoPath, List<CommitTrainingSample> commitTrainingSample,
                                                  MongoCollection<Document> collection) throws Exception {

        // get the final dir name as repoName, thus generate repoID using hash
        int index = repoPath.lastIndexOf(File.separator);
        String repoName = repoPath.substring(index + 1);
        String repoID = String.valueOf(Math.abs(repoName.hashCode()));



        // Analyze the Sample List
        Integer size = commitTrainingSample.size();
        for (int i = 0; i < size; i++) {
            CommitTrainingSample tempCommitTrainingSample = commitTrainingSample.get(i);
            String commitID = tempCommitTrainingSample.getCommitID();
            System.out.println("Proceeding: "+commitID+"  "+(i+1)+"/"+size);
            tempCommitTrainingSample.setRepoID(repoID);
            tempCommitTrainingSample.setRepoPath(repoPath);
            tempCommitTrainingSample.setRepoName(repoName);


            String commitMsg = tempCommitTrainingSample.getCommitMsg();
            // get Intent from commitMsg
            Intent intent = getIntentFromMsg(commitMsg);
            tempCommitTrainingSample.setIntent(intent);

            // get List<IntentDescription> from commitMsg
            List<IntentDescription> intentList = getIntentDescriptionFromMsg(commitMsg);
            tempCommitTrainingSample.setIntentDescription(intentList);

            RepoAnalyzer repoAnalyzer = new RepoAnalyzer(repoID, repoName, repoPath);
            DataCollector dataCollector = new DataCollector(repoName, "~/Downloads");
            // add astActionList using gumtree
            tempCommitTrainingSample = generateGumtreeActionsFromCodeChange(tempCommitTrainingSample, repoAnalyzer);

            // add DiffHunkActions using DiffHunks
            List<Action> DiffHunkActions=
                    generateActionListFromDiffHunks(tempCommitTrainingSample, repoAnalyzer, dataCollector);
            tempCommitTrainingSample.setDiffHunksActions(DiffHunkActions);

            // add refactorCodeChange using RefactoringMiner
            List<RefactorMinerAction> refactorMinerActions = getRefactorCodeChangesFromCodeChange(repoPath, commitID);
            tempCommitTrainingSample.setRefactorMinerActions(refactorMinerActions);

            // Load into DB
            loadTrainSampleToDB(collection, tempCommitTrainingSample);
        }

        return true;
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
    private static List<AstAction> generateAstActionList(EditScript editScript) {
        List<AstAction> actionList = new ArrayList<>();
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
            AstAction myAction = new AstAction(ASTOperation, action.getNode().getType().toString());
            actionList.add(myAction);
        }
        return actionList;
    }

    // generate Gumtree action list from code changes: diffFile and EditScript
    private static CommitTrainingSample generateGumtreeActionsFromCodeChange(
            CommitTrainingSample tempCommitTrainingSample, RepoAnalyzer repoAnalyzer) {
        try {  // if no FileChange
            List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(tempCommitTrainingSample.getCommitID());
            // get EditScript from diffFiles, and get ActionList from EditScript
            List<AstAction> tempActionList = new ArrayList<>();
            Integer sizeDiff = diffFiles.size();
            for (int j = 0; j < sizeDiff; j++) {
                String baseContent = diffFiles.get(j).getBaseContent();
                String currentContent = diffFiles.get(j).getCurrentContent();
                // File added or deleted, thus no content
                if (baseContent == null || baseContent.equals("") || currentContent == null || currentContent.equals("")) {
                    tempCommitTrainingSample.addGumtreeCountFileChange();
                    continue;
                }
                EditScript editScript = generateEditScript(baseContent, currentContent);
                if (editScript != null) {
                    List<AstAction> actionList = generateAstActionList(editScript);
                    tempActionList.addAll(actionList);
                    tempCommitTrainingSample.setGumtreeActionList(tempActionList);
                } else {
                    // Only doc change, thus no CodeChange and AbstractJdtTree generated
                    tempCommitTrainingSample.addGumtreeCountDocChange();
                }
            }
        } catch (Exception e) {
            //e.printStackTrace();
            // Lack of File, thus no DiffFiles generated
            tempCommitTrainingSample.addGumtreeCountFileChange();
        }
        return tempCommitTrainingSample;
    }

    public static Action convertAstActionToAction(AstAction astAction) {
        Operation op = Operation.UKN;
        for (Operation operation : Operation.values()) {
            if (astAction.getASTOperation().label.equals(operation.label)) {
                op = operation;
                break;
            }
        }
        return new Action(op, astAction.getASTNodeType(), "");
    }

    // generate DiffHunkActions from diffHunks
    private static List<Action> generateActionListFromDiffHunks(
            CommitTrainingSample tempCommitTrainingSample, RepoAnalyzer repoAnalyzer, DataCollector dataCollector) {
        String commitID = tempCommitTrainingSample.getCommitID();
        List<DiffFile> diffFiles = repoAnalyzer.analyzeCommit(commitID);
        List<DiffHunk> allDiffHunks = repoAnalyzer.getDiffHunks();
        if(diffFiles.isEmpty() || allDiffHunks.isEmpty())
            System.out.println("No DiffFiles generated ");
        Integer sizeDiffHunk = allDiffHunks.size();
        List<Action> AstActions = new ArrayList<>();
        for(Integer i = 0; i < sizeDiffHunk; i++) {
            List<Action> actions = dataCollector.analyzeASTActions(allDiffHunks.get(i));
            AstActions.addAll(actions);
        }
        return AstActions;
    }

    // generate RefactorCodeChangeFromCodeChagne
    private static List<RefactorMinerAction> getRefactorCodeChangesFromCodeChange(String repoPath, String commitID) {
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
        org.refactoringminer.api.GitService gitService = new GitServiceImpl();
        List<RefactorMinerAction> refactorMinerActions = new ArrayList<>();
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
                            RefactorMinerAction refactorMinerAction = new RefactorMinerAction(ref.getRefactoringType(), ref.getName());
                            refactorMinerActions.add(refactorMinerAction);
                        }
                    }
                }
            });
        } catch (Exception e) {
            System.out.println("Repo Not Exist");
            //e.printStackTrace();
        }
        return refactorMinerActions;
    }

    public static Action convertRefactorCodeChangeToAction(RefactorMinerAction refactorMinerAction) {
        Operation op = Operation.UKN;
        for (Operation operation : Operation.values()) {
            if (refactorMinerAction.getName().equals(operation.label)) {
                op = operation;
                break;
            }
        }
        return new Action(op, refactorMinerAction.getRefactoringType(), "");
    }


    // Load given commitTrainingSample into given DB collection
    private static void loadTrainSampleToDB(MongoCollection<Document> collection, CommitTrainingSample commitTrainingSample) {
        try {
            Document doc1 = new Document();
            doc1.put("repoID", commitTrainingSample.getRepoID());
            doc1.put("repoPath", commitTrainingSample.getRepoPath());
            doc1.put("repoName", commitTrainingSample.getRepoName());
            doc1.put("commitID", commitTrainingSample.getCommitID());
            doc1.put("commitMsg", commitTrainingSample.getCommitMsg());
            doc1.put("committer", commitTrainingSample.getCommitter());
            doc1.put("committerEmail", commitTrainingSample.getCommitterEmail());
            doc1.put("commitTime", commitTrainingSample.getCommitTime());
            doc1.put("commitIntent", commitTrainingSample.getIntent().getLabel());
            doc1.put("commitIntentDescription", String.valueOf(commitTrainingSample.getIntentDescription()));
            doc1.put("GumtreeCountFileChange", commitTrainingSample.getGumtreeCountFileChange());
            doc1.put("GumtreeCountDocChange", commitTrainingSample.getGumtreeCountDocChange());

            {
            List<Action> Actions1 = new ArrayList<>();
            // add ActionList to DB
            List<AstAction> GumtreeActions = commitTrainingSample.getGumtreeActionList();
            if (GumtreeActions != null) {
                List<Document> actions = new ArrayList<>();
                for (AstAction astAction : GumtreeActions) {
                    Document addrAttr = new Document();
                    Actions1.add(convertAstActionToAction(astAction));
                    addrAttr.put("operation", String.valueOf(astAction.getASTOperation()));
                    addrAttr.put("astNodeType", astAction.getASTNodeType());
                    actions.add(addrAttr);
                }
                doc1.put("GumtreeActions", actions);
            }

            List<Action> Actions2 = new ArrayList<>();
            // add DiffHunkActions to DB
            List<Action> DiffHunkActions = commitTrainingSample.getDiffHunksActions();
            if (DiffHunkActions != null) {
                List<Document> actions = new ArrayList<>();
                for (Action DiffHunkAction : DiffHunkActions) {
                    Document addrAttr = new Document();
                    addrAttr.put("diffHunkAction", DiffHunkAction.toString());
                    actions.add(addrAttr);
                    Actions2.add(DiffHunkAction);
                }
                doc1.put("DiffHunkActions", actions);
            }

            List<Action> Actions3 = new ArrayList<>();
            // add refactorCodeChange to DB
            List<RefactorMinerAction> refactorMinerActions = commitTrainingSample.getRefactorMinerActions();
            if (refactorMinerActions != null) {
                List<Document> actions = new ArrayList<>();
                for (RefactorMinerAction refactorMinerAction : refactorMinerActions) {
                    Document addrAttr = new Document();
                    addrAttr.put("operation", refactorMinerAction.getOperation());
                    addrAttr.put("refactoringType", refactorMinerAction.getRefactoringType());
                    actions.add(addrAttr);
                    Actions3.add(convertRefactorCodeChangeToAction(refactorMinerAction));
                }
                doc1.put("refactorMinerActions", actions);
            }
            // add 3in1 to DB
            List<Document> actions = new ArrayList<>();
            Integer sizeActions1 = Actions1.size();
            Integer sizeActions2 = Actions2.size();
            Integer sizeActions3 = Actions3.size();
            for (int i = 0; i < sizeActions1; i ++) {
                Document addrAttr = new Document();
                addrAttr.put("Gumtree", Actions1.get(i).toString());
                actions.add(addrAttr);
            }
            for (int i = 0; i < sizeActions2; i ++) {
                Document addrAttr = new Document();
                addrAttr.put("DiffHunk", Actions2.get(i).toString());
                actions.add(addrAttr);
            }
            for (int i = 0; i < sizeActions3; i ++) {
                Document addrAttr = new Document();
                addrAttr.put("RefactorMiner", Actions3.get(i).toString());
                actions.add(addrAttr);
            }
            doc1.put("AllActions", actions);
            }


            collection.insertOne(doc1);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}