package com.github.smartcommit.model.graph;

public class Node {
    private Integer id;
    private NodeType type;
    private String identifier;
    private String qualifiedName;
    public Boolean isInDiffHunk;

    public Node(Integer id, NodeType type, String identifier, String qualifiedName) {
        this.id = id;
        this.type = type;
        this.identifier = identifier;
        this.qualifiedName = qualifiedName;
        this.isInDiffHunk = false;
    }

    public Integer getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }
}
