package com.github.smartcommit.intent;

// DataCollector
import com.github.smartcommit.util.GitService;
import com.github.smartcommit.util.GitServiceCGit;
import com.github.smartcommit.util.Utils;

import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import java.io.File;

// GumtreeExample

import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.model.Action;
import com.github.smartcommit.intent.GumtreeExample;
import com.github.smartcommit.model.DiffFile;

// MongoExample
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;


// Commit message:  Get, Label, Store
public class CommitLog_GetLabelStore {
    public static void main(String[] args) {
        String REPO_NAME = "guava";
        String REPO_DIR = "/Users/Chuncen/IdeaProjects/" + REPO_NAME;
        String commitID = "f4b3f611c4e49ecaded58dcb49262f55e56a3322";
        List<List<String>> commits = new ArrayList<List<String>>();

        System.out.println("Get Succeed? " + getIt(REPO_DIR, commits));
        System.out.println("Label Succeed? " + lableIt(REPO_DIR, commits));
        System.out.println("Store Succeed? " + storeIt(REPO_NAME, commits));
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
        //System.out.println("Log0: " + commitMessages.get(1));
        return true;
    }
    // Cluster to get label using gumtree
    public static boolean lableIt(String REPO_DIR, List<List<String>> commits) {
        try {

            List<String> intents;
            intents = FileUtils.readLines(new File(REPO_DIR +File.separator+"intents-selected.txt"));
            for (List<String> commit : commits) {
                for (String intent : intents) {
                    String msg = commit.get(0);
                    //System.out.println(msg);
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
        MongoDatabase sampleDB = mongoClient.getDatabase("commits");
        MongoCollection<Document> repoCol = sampleDB.getCollection(REPO_NAME);
        Integer size = commits.size();
        for(int i = 0 ; i < size ; i++) {
            // key:value
            Document sampleDoc = new Document("repo_name", REPO_NAME);
            sampleDoc
                    .append("commit_log", commits.get(i).get(0))
                    .append("commit_ID", commits.get(i).get(1))
                    .append("commit_Author", commits.get(i).get(2))
                    .append("commit_Date", commits.get(i).get(3))
                    .append("commit_msg", commits.get(i).get(4))
                    .append("commit_intent", commits.get(i).get(5))
            ;
            repoCol.insertOne(sampleDoc);
        }
        mongoClient.close();
        return true;
    }

}

// read from json
// https://www.cnblogs.com/dwb91/p/6726823.html

// read from txt
// https://blog.csdn.net/xuehyunyu/article/details/77873420

// into Mongodb
// https://github.com/Symbolk/IntelliMerge/blob/f4b5166abbd7dffc2040b819670dad31a6b89ae0/……
// src/main/java/edu/pku/intellimerge/evaluation/Evaluator.java#L49
