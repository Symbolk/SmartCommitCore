package com.github.smartcommit.model.graph;

/**
 * Nodes in the DiffViewGraph, which stand for one diff hunk
 */
public class DiffNode {
    private String fileIndex;
    private String hunkIndex;

    public DiffNode(String fileIndex, String hunkIndex) {
        this.fileIndex = fileIndex;
        this.hunkIndex = hunkIndex;
    }
}
