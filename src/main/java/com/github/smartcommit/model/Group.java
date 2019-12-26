package com.github.smartcommit.model;

import java.util.List;

/** The output result, one group for one commit */
public class Group {
  private List<DiffHunk> diffHunks;
  private String commitMsg;
  private String templateCommitMsg;
  private String repoID;
  private String repoName;
  private String groupID;
  private String intentLabel;
  private String commitID;
}
