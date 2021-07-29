## Artifact Description

This artifact is the core algorithm of SmartCommit---an assistant tool to lead and help developers follow the best practice of cohesive and focused commits, which is advocated by many companies (like Google and Facebook) and open source communities (like Git and Angular). A cohesive commit should focus on a development or maintenance activity, such as feature addition, bugfix or refactoring. Cohesive commits form a clear change history that facilitates software maintenance and team collaboration. To help the developer make cohesive commits, SmartCommit can suggest a decomposition (groups of related and self-contained code changes) to their code changes, and allows the developer to interactively adjust the suggested decomposition, until it reaches a state that the developer feels reasonable to submit code change groups as commits.

To evaluate the accuracy and effectiveness of SmartCommit, we have conducted 2 sets of extensive experiments in the paper: 1) Industrial Field Study and 2) Controlled Open Source Experiment. This artifact can be used to replicate the evaluation results in the Controlled Open Source Experiment as well as the visualization of industrial use data in the Industrial Field Study. It consists of the core algorithm,  the dataset, the evaluation program, and the statistics and visualization program to generate results and figures presented in the paper. 

By properly configuring the running environment, one can easily replicate the results in our paper by re-running the evaluation and visualization program. Furthermore, the code is heavily documented to be self-explained and consistent with the paper. We hope that the artifact could be used or extended by researchers and developers to advance further progress in this field.



## Open Access

The artifact is publicly available on GitHub:

1. The core algorithm: https://github.com/Symbolk/SmartCommitCore
2. The dataset and visualization scripts: https://github.com/Symbolk/SmartCommitEvaluation-Viz



## Installation

1. Clone the SmartCommitCore project from GitHub:

   ```
   git clone https://github.com/Symbolk/SmartCommitCore
   ```

2. Configure the environment as described in [REQUIREMENTS];

[REQUIREMENTS]: /docs/REQUIREMENTS.md

3. Open SmartCommitCore in IntelliJ IDEA, set the Gradle runner to use in `Preferences->Build,Execution,Deployment->Build Tools->Gradle`:

   ![image-20210602165205875](/docs/imgs/gradle_setting.png?raw=true)

4. Resolve dependencies by clicking the `Reload All Gradle Projects` on the Gradle tool window:

   ![image-20210602165205875](/docs/imgs/dependency_resolution.png?raw=true)

5. After the dependency resolution finishes, build the project with `Build->Build Project` of IDEA, expected output:

   ![image-20210602113556766](/docs/imgs/build_output.png?raw=true)

6. Right click on `src/main/java/com/github/smartcommit/client/CLI.java` and click `Run 'CLI.main()'`to verify the correct setup, expected output in the terminal:
```sh
   Please at least specify the Git repository to analyze with -r.
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
       -o, -output
         Specify the path to output the result.
         Default: /Users/USERNAME/.smartcommit/repos
       -ref, --detect-refactoring
         Whether to enable refactoring detection.
         Default: true
       -md, --max-distance
         Set the maximum distance (default: 0).
         Default: 0
       -ms, --min-similarity
         Set the minimum similarity (default: 0.8).
         Default: 0.8
       -nj, --process-non-java
         Whether to further process non-java changes.
         Default: true
       -wt, --weight-threshold
         Set the threshold for weight filtering (default: 0.6).
         Default: 0.6
```



## Small Example

1. Right click on `src/main/java/com/github/smartcommit/client/Main.java`and run `Main.main()` to analyze a historical commit of SmartCommitCore itself (that is, dog-food itself);
2. The expected Run output:

![image-20210602175747210](/docs/imgs/toy_example.png?raw=true)



## Evaluation Replication

### Controlled Open Source Experiment

1. Create the following directories under home directory of the local machine;

   `````
   mkdir ~/smartcommit
   cd ~/smartcommit
   `````

2. Clone the repo containing the dataset (dumped database) and the visualization scripts:

   ```
   git clone https://github.com/Symbolk/SmartCommitEvaluation-Viz
   ```

3. Unzip the dataset.zip under the repo root (i.e., the atomic commits that will be used to generate composite commits) under `~/smartcommt`:

   ```
   unzip -d ~/smartcommit SmartCommitEvaluation-Viz/dataset.zip
   ```

4. Install MongoDB Community Edition 4.4.3 and start MongoDB instance by importing the dataset:

   ```
   mkdir ~/smartcommit/database
   mongod --dbpath ~/smartcommit/database
   mongorestore -d atomic ~/smartcommit/dataset/atomic
   ```

5. Check the MongoDB for successfully importing:

   ```shell
   mongo
   > show dbs
   admin      0.000GB
   atomic     0.005GB
   config     0.000GB
   local      0.000GB
   > use atomic
   switched to db atomic
   > show collections
   antlr4
   cassandra
   deeplearning4j
   elasticsearch
   glide
   netty
   nomulus
   realm-java
   rocketmq
   storm
   ```

6. Clone the 10 repos under evaluation and checkout to the branch and commit on which we conducted the evaluation:

   ```
   mkdir repos
   cd repos
   git clone https://github.com/netty/netty.git && git checkout 4.1 && git checkout 91a7f49f0d
   git clone https://github.com/google/nomulus.git && git checkout master && git checkout c5aa0125a
   git clone https://github.com/bumptech/glide.git && git checkout master && git checkout 1caeff4bf
   git clone https://github.com/apache/rocketmq.git && git checkout master && git checkout 8ef01a6c
   git clone https://github.com/realm/realm-java.git && git checkout master && git checkout c1c45b46a
   git clone https://github.com/elastic/elasticsearch.git && git checkout master && git checkout b58e95d25cf
   git clone https://github.com/antlr/antlr4.git && git checkout master && git checkout 38b1b9ac7
   git clone https://github.com/eclipse/deeplearning4j.git && git checkout master && git checkout 722d5a052a
   git clone https://github.com/apache/storm.git && git checkout master && git checkout c33ae620e
   git clone https://github.com/apache/cassandra.git && git checkout trunk && git checkout 001deb069f
   ```

7. Edit the main method in `src/main/java/com/github/smartcommit/evaluation/Evaluation.java` to config the data directory, repo name and step (the number of merged atomic commits) to test;

   ```
    private static final String dataDir = homeDir + "/smartcommit/";
   
    public static void main(String[] args) {
       BasicConfigurator.configure();
       org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
   
       String repoDir = dataDir + "repos/";
       String tempDir = dataDir + "results/";
   
       // name of the repo under test
       String repoName = "antlr4";
       // number of merged atomic commits to produce one composite commit 
       int step = 2;
       String repoPath = repoDir + repoName;
       runOpenSrc(repoName, repoPath, tempDir + "/" + repoName, 2);
     }
   ```

8. Right click on `src/main/java/com/github/smartcommit/evaluation/Evaluation.java`  and `Run Evaluation.main()` for evaluation on a specific repo at a specific step;

9. Check the output in console for results, expected output example (P.S. the numbers in results could be different as the sampling&merging depends on the visiting order of MongoCursor):

   ```
   Open Source Repo: nomulus Step: 5
   0 [main] INFO org.mongodb.driver.cluster  - Cluster created with settings {hosts=[localhost:27017], mode=SINGLE, requiredClusterType=UNKNOWN, serverSelectionTimeout='30000 ms', maxWaitQueueSize=500}
   54 [main] INFO org.mongodb.driver.cluster  - Cluster description not yet available. Waiting for 30000 ms before timing out
   56 [cluster-ClusterId{value='60b72bfd036d35300bd8c6a6', description='null'}-localhost:27017] INFO org.mongodb.driver.connection  - Opened connection [connectionId{localValue:1, serverValue:6}] to localhost:27017
   59 [cluster-ClusterId{value='60b72bfd036d35300bd8c6a6', description='null'}-localhost:27017] INFO org.mongodb.driver.cluster  - Monitor thread successfully connected to server with description ServerDescription{address=localhost:27017, type=STANDALONE, state=CONNECTED, ok=true, version=ServerVersion{versionList=[4, 4, 3]}, minWireVersion=0, maxWireVersion=9, maxDocumentSize=16777216, logicalSessionTimeoutMinutes=30, roundTripTimeNanos=2002212}
   69 [main] INFO org.mongodb.driver.connection  - Opened connection [connectionId{localValue:2, serverValue:7}] to localhost:27017
   97 [main] INFO org.mongodb.driver.connection  - Closed connection [connectionId{localValue:2, serverValue:7}] to localhost:27017 because the pool has been closed.
   [Batch 0:fd1c68f_f1ad34b] Time=310ms #diff_hunks=21 #LOC=80
   SmartCommit: 	#groups=6 (#Incorrect/#Total)=8/21 Reordering=2 Accuracy=71.9%
   ClusterChanges: 	#groups=4 (#Incorrect/#Total)=8/21 Reordering=1 Accuracy=69.52%
   All in one Group: 	#groups=1 (#Incorrect/#Total)=8/21 Reordering=0 Accuracy=50.48%
   One file one Group: 	#groups=9 (#Incorrect/#Total)=12/21 Reordering=3 Accuracy=61.43%
   One hunk one Group: 	#groups=21 (#Incorrect/#Total)=19/21 Reordering=9 Accuracy=49.52%
   ....
   [Batch 99:27b9244_01bb3a3] Time=100ms #diff_hunks=8 #LOC=16
   SmartCommit: 	#groups=2 (#Incorrect/#Total)=0/8 Reordering=0 Accuracy=100.0%
   ClusterChanges: 	#groups=2 (#Incorrect/#Total)=0/8 Reordering=0 Accuracy=100.0%
   All in one Group: 	#groups=1 (#Incorrect/#Total)=2/8 Reordering=0 Accuracy=57.14%
   One file one Group: 	#groups=3 (#Incorrect/#Total)=3/8 Reordering=0 Accuracy=67.86%
   One hunk one Group: 	#groups=8 (#Incorrect/#Total)=6/8 Reordering=3 Accuracy=42.86%
   SmartCommit: Median Accuracy: 85.60%
   ClusterChanges: Median Accuracy: 68.61%
   All in one Group: Median Accuracy: 64.44%
   One File One Group: Median Accuracy: 57.17%
   One Hunk One Group: Median Accuracy: 35.56%
   ```

10. The detailed intermediate data and results are generated under `~\smartcommit\results` and `~\smartcommit\viz`, including:

   - results: the intermediate data that will be consumed by the evaluation script and the GUI, including the base/current versions of code (i.e., relevant source files before and after the change), detailed code changes (diffs), and the suggested code change groups, etc.
   - viz: the statistics data as input to the visualization script to draw figures, including the number of diff hunks, lines of code, the computed operation number, the accuracy, and the runtime, etc.

11. Repeat step 5-6 for each repo and step (10*3 combinations in total, expected to cost about 3 hours to run all the experiments).

    

### Statistics and Visualization

1. Clone the visualization project (if not cloned yet):

   ```
   git clone https://github.com/Symbolk/SmartCommitEvaluation-Viz
   ```

2. Open the project in PyCharm, setup the venv as Python 3.7.0, and install dependencies:

   ```
   pip install -r requirements.txt
   ```

3. Edit `SmartCommitEvaluation-Viz/config.py` to change the raw results directory:

   ```
   # root dir of raw data
   root_path = str(Path.home()) + '/smartcommit/viz/'
   ```

4. For Controlled Open Source Experiment:

   - Run `rq1/rq1-boxplot-project_method.py` to generate `rq1-baselines.pdf`:  Accuracy of SmartCommit and 3 baseline methods (Figure 7(a) in the paper).
   - Run `rq1/rq1-boxplot-project_length.py` to generate `rq1-length.pdf`:  Accuracy of SmartCommit for different numbers of merged atomic commits (Figure 7(b) in the paper).
   - Run `rq2/rq2-adjustments.py` to generate `reassign_frequency.txt` and `reorder_frequency.txt`: Number of reassign and reorder steps and proportions (Figure 8 in the paper).

5. For Industrial Field Study

   - Run `rq3/rq3-scatterplot.py` to generate `rq3.pdf`: the runtime cost with different input sizes;
   - Run `rq4/preprocess.py` first, and other python files then to generate usage distributions (Figure 9 in the paper).

