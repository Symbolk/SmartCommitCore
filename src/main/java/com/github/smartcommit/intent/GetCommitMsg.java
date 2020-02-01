package com.github.smartcommit.intent;
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

import javax.lang.model.type.IntersectionType;

public class GetCommitMsg {
    public static void main(String[] args) {
        String REPO_NAME = "vue";
        String REPO_DIR = "/Users/Chuncen/IdeaProjects/" + REPO_NAME;

        // CommitInfo
        List<CommitInfo> commitsInfo = new ArrayList<>();

        System.out.println("CommitsMsg Collected? " + CommitsMsgCollector(REPO_NAME, REPO_DIR, commitsInfo));
    }

    // Simply use --pretty=oneline
    public static boolean CommitsMsgCollector(String REPO_NAME, String REPO_DIR, List<CommitInfo> commitInfo) {
        MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
        MongoClient mongoClient = new MongoClient(connectionString);
        MongoDatabase CommitMsg = mongoClient.getDatabase("CommitMsg");
        MongoCollection<Document> commitMsgCollection = CommitMsg.getCollection(REPO_NAME+"CommitMsg");
        commitMsgCollection.drop();
        commitMsgCollection = CommitMsg.getCollection(REPO_NAME+"CommitMsg");

        String log = Utils.runSystemCommand(REPO_DIR, "git", "log", "--pretty=oneline");
        String parts[] = log.split("\\n"), body[];
        for (String part : parts) {
            String commitID = part.substring(0, 40);
            String commitMsg = part.substring(41);
            Document tempDoc = new Document("commitID", commitID);
            tempDoc.append("commitMsg", commitMsg);
            tempDoc.append("tag", 0);
            commitMsgCollection.insertOne(tempDoc);
        }

        mongoClient.close();
        return true;
    }

}
