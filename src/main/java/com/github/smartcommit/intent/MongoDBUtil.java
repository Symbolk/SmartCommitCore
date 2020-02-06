package com.github.smartcommit.intent;

import com.mongodb.*;
import com.mongodb.client.MongoDatabase;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import java.util.ArrayList;
import java.util.List;

public class MongoDBUtil {

    public static MongoDatabase getConnection(String ipAddress, String port, String dbName) {
        //连接到 mongodb 服务
        MongoClientURI connectionString = new MongoClientURI("mongodb://" + ipAddress + ":" + port);
        MongoClient mongoClient = new MongoClient(connectionString);
        //连接到数据库
        MongoDatabase mongoDatabase = mongoClient.getDatabase(dbName);
        //返回连接数据库对象
        return mongoDatabase;
    }

    //需要密码认证方式连接
    public static MongoDatabase getConnectionWithCredentials() {
        List<ServerAddress> adds = new ArrayList<>();
        //ServerAddress()两个参数分别为 服务器地址 和 端口
        ServerAddress serverAddress = new ServerAddress("localhost", 27017);
        adds.add(serverAddress);
        List<MongoCredential> credentials = new ArrayList<>();
        //MongoCredential.createScramSha1Credential()三个参数分别为 用户名 数据库名称 密码
        MongoCredential mongoCredential = MongoCredential.createScramSha1Credential("username", "databaseName", "password".toCharArray());
        credentials.add(mongoCredential);
        //通过连接认证获取MongoDB连接
        MongoClient mongoClient = new MongoClient(adds, credentials);
        //连接到数据库
        MongoDatabase mongoDatabase = mongoClient.getDatabase("test");
        //返回连接数据库对象
        return mongoDatabase;
    }

    public DBCollection getCollection(MongoDatabase connection, String collectionName) {
        DBCollection collection = (DBCollection) connection.getCollection(collectionName);
        return collection;
    }


}
