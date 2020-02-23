package com.github.smartcommit.intent;

/*
public class MongoExample {
  public static void main(String[] args) {
    String REPO_NAME = "guava";
    try {
      MongoClientURI connectionString = new MongoClientURI("mongodb://localhost:27017");
      MongoClient mongoClient = new MongoClient(connectionString);
      MongoDatabase sampleDB = mongoClient.getDatabase("samples");
      MongoCollection<Document> repoCol = sampleDB.getCollection(REPO_NAME);
      Document commitDoc = new Document("repo_name", REPO_NAME);
      CommitTrainingSample commitTrainningSample = new CommitTrainingSample();
      // key:value
      commitDoc
          .append("intent", commitTrainningSample.getIntent())
          .append("commit_msg", "bugfix: fix a bug introduced in the last version")
          .append("author", "8889")
          .append("email", "990@gmail.com");
      List<Document> actionDocs = new ArrayList<>();
      Document actionDoc =
          new Document("type", "add")
              .append("node_type", "MethodInvocation")
              .append("label", "getID()");
      actionDocs.add(actionDoc);
      commitDoc.append("actions", actionDocs);

      repoCol.insertOne(commitDoc);

      mongoClient.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}


 */