package com.github.smartcommit.core;

import org.neo4j.driver.v1.*;

import java.util.HashMap;
import java.util.Map;

import static org.neo4j.driver.v1.Values.parameters;

public class DiffAnalyzer {
  public static void main(String[] args) {
    // 2. process collected old/new content from agent
    Driver driver =
        GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "smart"));
    Session session = driver.session();

    //        List<ActionCluster> actionClusters =
    // RepoAnalyzer.generateActionClusters(filePair.getOldContent(), filePair.getNewContent());
    Map<String, Object> params = new HashMap<>();
    //        params.put("operation", rootAction.getName());
    //        params.put("nodeType", rootAction.getNode().getType().name);
    //        params.put("label", rootAction.getNode().getLabel());
    //        params.put("startLine", rootAction.getNode().getStartLine());
    //        params.put("endLine", rootAction.getNode().getEndLine());
    session.run(
        "CREATE (a:Action {operation: $operation, nodeType: $nodeType, label:$label,"
            + " startLine:$startLine, endLine: $endLine})",
        params);

    session.run(
        "CREATE (a:Person {name: {name}, title: {title}})",
        parameters("name", "Arthur001", "title", "King001"));
    StatementResult result =
        session.run(
            "CREATE (baeldung:Company {name:$name}) "
                + "-[:owns]-> (tesla:Car {make: $make, model: $model})"
                + "RETURN baeldung, tesla",
            params);

    //                StatementResult result = session.run( "MATCH (a:Person) WHERE a.name = {name}
    // " +
    //                                "RETURN a.name AS name, a.title AS title",
    //                        parameters( "name", "Arthur001" ) );
    while (result.hasNext()) {
      Record record = result.next();
      System.out.println(record.get("title").asString() + " " + record.get("name").asString());
    }
    session.close();
    driver.close();
  }
}
