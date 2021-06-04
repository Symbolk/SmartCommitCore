## Target Badge

Artifacts Evaluated - Reusable



## Supporting Reasons

We have the following reasons to support the badge: 

- The project is established with a well-designed architecture, and is developed trying to follow the best practice of cohesive commits by dog-fooding itself.
- The code is carefully documented to be self-explained, with friendly API that allows it to be used as the backend of custom frontends (we implemented a cross-platform desktop electron app and a plugin of IntelliJ IDEA).
- The tool provides a series of configurable options for extending and customizing (see Appendix below).
- The industrial version of the tool has stood the test of a long-term (>1 year) and large-scale (>100 engineers on >10 industrial projects) practical use.

Therefore, we believe that the artifact can not only be used by developers to improve their *commit style*, but can also be reused by researchers to support the further research by modifying and extending the software.





## Appendix: API, Configurations, and Architecture

### API Usage

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

// override the threshold for edge weight filtering (default: 0.6)
void setWeightThreshold(Double weightThreshold) 

// override the minimum similarity considered (default: 0.8)
void setMinSimilarity(double minSimilarity) 

// override the maximum distance between diff hunks (default: 0)
void setMaxDistance(int maxDistance)
```

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
