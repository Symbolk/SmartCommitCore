/*
package com.github.smartcommit.intent;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

// https://github.com/Symbolk/IntelliMerge/blob/f4b5166abbd7dffc2040b819670dad31a6b89ae0/src/main/java/edu/pku/intellimerge/evaluation/Evaluator.java#L49
public class ToMonogoDB {
    public static void main(String[] args) {
        try {
            MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
            MongoClient mongoClient = new MongoClient(connectionString);
            MongoDatabase intelliDB = mongoClient.getDatabase("IntelliVSManual");
            MongoDatabase gitDB = mongoClient.getDatabase("GitVSManual");
            MongoDatabase jfstDB = mongoClient.getDatabase("JFSTVSManual");
            MongoCollection<Document> intelliDBCollection = intelliDB.getCollection(REPO_NAME);
            MongoCollection<Document> gitDBCollection = gitDB.getCollection(REPO_NAME);
            MongoCollection<Document> jfstDBCollection = jfstDB.getCollection(REPO_NAME);
            // drop the existing collection
            intelliDBCollection.drop();
            gitDBCollection.drop();
            jfstDBCollection.drop();



        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

 */