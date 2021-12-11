<div align="center">
  <a href="" target="_blank">
    <img width="160" src="https://github.com/Symbolk/SmartCommit/blob/master/assets/icon.png" alt="logo">
  </a>
  <h1 id="smartcommit"><a href="https://github.com/Symbolk/SmartCommitCore/" target="repo">SmartCommit</a></h1>

</div>

#### SmartCommit aims at making "code commit" an elegant and decent daily work for developers.

As advocated by many communities (e.g., [Git official doc], [Angular contribution instructions]) and companies (e.g., [Google's engineering practices]), developers are encouraged to submit cohesive and self-contained commits, accompanying with clear and informative commit messages.

SmartCommit is the assistant for you to follow this best practice.

Specifically, it helps you to:

- Organize your local changes into groups, each of which is expected to focus on one specific task.
- Review and stage fine-grained code changes within an intuitive GUI, in the forms of code hunks or files.
- Commit and push multiple commits with one single click, saving the effort to type `git-status`, `git-diff`, `git-add`, `git-commit` and `git-push` commands.

[Git official doc]: https://git-scm.com/docs/gitworkflows#_separate_changes
[Angular contribution instructions]:  https://github.com/angular/angular/blob/master/CONTRIBUTING.md
[Google's engineering practices]: https://github.com/google/eng-practices/blob/master/review/developer/small-cls.md

> Note: This repo is the change decomposition&suggestion algorithm of SmartCommit, some additional features (like intent classification and message suggestion) are excluded in the public version.
> Check the [SmartCommit] repo for the demo GUI client.

[SmartCommit]: https://github.com/Symbolk/SmartCommit

## As a User

## Requirements

- macOS/Windows/Linux
- Java JRE 8+
- Git ^2.20.0

## Usage

### 1. JAR Usage

Download the latest jar from [releases], or build one with `gradle fatJar -x test`, and run the following command:

[releases]: https://github.com/Symbolk/SmartCommitCore/releases

```sh
java -jar SmartCommit-xxx.jar [OPTIONS]
```

### Options

```sh
Usage: SmartCommit [options]
  Options:
    -r, --repo
      Absolute root path of the target Git repository.
      Default: <empty string>
    -w, --working
      Analyze the current working tree (default).
      Default: true
    -c, --commit
      Analyze a specific commit by providing its ID.
      Default: <empty string>
    -o, --output
      Specify the path to output the result.
      Default: /Users/symbolk/.smartcommit/repos
    -ref, --detect-refactoring
      Whether to enable refactoring detection, true/false.
      Default: true
    -gr, --granularity
      Set the atomic unit/granularity of change: {hunk: 0 (default), member: 
      1, class: 2, package: 3}.
      Default: 0
    -ms, --min-similarity
      Set the minimal similarity between change, [0.0, 1.0].
      Default: 0.8
    -nj, --process-non-java
      Whether to further process non-java changes,  true/false.
      Default: true
    -wt, --weight-threshold
      Set the threshold for partitioning (if not specified or 0.0, use the 
      max-gap splitter), [0.0, 1.0].
      Default: 0.0
```

### 2. API Usage

The entry class SmartCommit provides the following APIs to use programmatically:

#### API for initialization:

```java
/**
* Initial setup for analysis
*
* @param repoName repo name
* @param repoPath absolute local repo path
* @param tempDir temporary directory path for intermediate and final result
*/
SmartCommit(String repoName, String repoPath, String tempDir)
```

#### API for analyzing changes:

```java

/**
* Analyze the current working directory of the repository
*
* @return suggested groups <id:group>
* @throws Exception
*/
Map<String, Group> analyzeWorkingTree()

/**
* Analyze a specific commit of the repository for decomposition
*
* @param commitID the target commit hash id
* @return suggested groups <id:group>
* @throws Exception
*/
Map<String, Group> analyzeCommit(String commitID)
```

#### API for exporting the result:

```java

/**
* Save meta information of each group, including diff hunk ids, commit msgs, etc. 
*
* @param generatedGroups generated groups <id:group>
* @param outputDir output directory path
*/
void exportGroupResults(Map<String, Group> generatedGroups, String outputDir)

/**
* Generate and save the detailed content of diff hunks for each group
*
* @param results generated groups <id:group>
* @param outputDir output directory path
*/
void exportGroupDetails(Map<String, Group> results, String outputDir)
```

### Options & Arguments:

```java
// whether to enable refactoring detection
void setDetectRefactorings(boolean detectRefactorings)

// whether to group changes in non-java textual files by type
void setProcessNonJavaChanges(boolean processNonJavaChanges)

// override the threshold for edge weight filtering (default: 0.0, use the dynamic threshold)
void setWeightThreshold(Double weightThreshold) 

// override the minimum similarity considered (default: 0.8, high-similarity)
void setMinSimilarity(double minSimilarity) 

// override the maximum distance between diff hunks (default: 0, hunk-level)
void setMaxDistance(int maxDistance)
```

## As a Developer

### Requirements

- maxOS/Windows/Linux
- Java JDK 8+
- Git ^2.20.0
- Gradle ^6.2.1
- IntelliJ IDEA 2020 (with Gradle integration)

### Environment Setup

1. Open the cloned repository as a project with IntelliJ IDEA;

2. Resolve dependencies by clicking the refresh button in the Gradle tab, or use `gradle build -x test`;

### Test

Run `CLI.main()` to see CLI options, or modify `Config.java` and run `Main.main()` for API usage.

### Build

Run the following command under the root of the cloned repository to build an executable jar from source with all dependencies packaged:

```sh
gradle fatJar
```

Packaged jar file will be generated in `build\libs`, with the name `SmartCommit-xxx.jar`.

### Project Structure
```
SmartCommit
   ├─client // interface
   │  ├─SmartCommit     // entry class
   ├─core   // core algorithms
   │  ├─visitor         // tree visitors 
   │  ├─RepoAnalyzer    // analyzing the repository to collect change info
   │  ├─GraphBuilder    // building entity reference graph from source code
   │  └─GroupGenerator  // building and generate groups from diff hunk graph
   ├─model  // data structure
   │  ├─constant    // enum constants
   │     ├─ChangeType   // types of textual change actions
   │     ├─ContentType  // types of change content
   │     ├─FileStatus   // status of changed files
   │     ├─FileType     // types of changed files
   │     ├─GroupLabel   // intent label of groups
   │     ├─Operation    // types of semantic change operations 
   │     └─Version      // old and new version tags
   │  ├─diffgraph   // diff hunk graph definition
   │     ├─DiffNode     // node definition
   │     ├─DiffEdge     // edge definition
   │     └─DiffEdgeType // edge types
   │  ├─entity  // program entities
   │  ├─graph   // entity reference graph
   │     ├─Node         // node definition
   │     ├─Edge         // edge definition
   │     ├─NodeType     // node types
   │     └─EdgeType     // edge types
   │  ├─Action  // semantic change action
   │  ├─Hunk            // single-side hunk model
   │  ├─DiffFile        // changed file model
   │  ├─DiffHunk        // diff hunk model
   │  ├─EntityPool      // pool of all involved entities
   │  └─Group           // generated group model
   ├─io         // input and output
   │  ├─DataCollector   // collect data from repository 
   │  ├─TypeProvider    // label provider for visualizing entity reference graph
   │  ├─DiffTypeProvider// label provider for visualizing of diff hunk graph 
   │  └─GraphExporter   // graph visualization in dot format
   └─util       // general utils
       ├─diffparser     // parsing and splitting diff hunks from GNU diff 
       ├─GitService     // wrapping git functions
       ├─JDTService     // delegating jdt parser
       └─Utils          // other utils
          
```
