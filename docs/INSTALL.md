## Installation

1. Clone the SmartCommitCore project from GitHub:

   `````
   git clone https://github.com/Symbolk/SmartCommitCore
   `````

2. Configure the environment as described in [REQUIREMENTS];

[REQUIREMENTS]: /docs/REQUIREMENTS.md

3. Open SmartCommitCore in IntelliJ IDEA, set the Gradle version to use in `Preferences->Build,Execution,Deployment->Build Tools->Gradle`:
   
   ![image-20210602165205875](/docs/imgs/gradle_setting.png?raw=true)

4. Resolve dependencies by clicking the `Reload All Gradle Projects` on the Gradle tool window:

   ![image-20210602165205875](/docs/imgs/dependency_resolution.png?raw=true)

5. After the dependency resolution finishes, build the project with `Build->Build Project` of IDEA, expected output:

   ![image-20210602113556766](/docs/imgs/build_output.png?raw=true)

6. Right click on `src/main/java/com/github/smartcommit/client/CLI.java` and click `Run 'CLI.main()'`to verify the correct setup, expected output:

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

