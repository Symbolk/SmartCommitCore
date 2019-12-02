package com.github.smartcommit.model;

import java.util.List;

/**
 * The output result, one group one commit
 */
public class Group {
    private List<DiffHunk> generatedDiffHunks;
    private List<DiffHunk> manualDiffHunks;
    private String generatedCommitMsg;
    private String manualCommitMsg;
}
