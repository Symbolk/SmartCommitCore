package com.github.smartcommit.model.graph;

public class Node {
    private Integer id;
    private NodeType type;
    private String identifier;
    private String qualifiedName;

    public Node(Integer id, NodeType type, String identifier, String qualifiedName) {
        this.id = id;
        this.type = type;
        this.identifier = identifier;
        this.qualifiedName = qualifiedName;
    }
}
