# Gradle Build Operation Trace Converter

Command-line tool for analysis and conversion of Build Operation traces of Gradle Build Tool into other formats.

## How to install

1. `git clone https://github.com/gradle/gradle-to-trace-converter`
2. `cd gradle-to-trace-converter`
3. `./gradlew install`: this will install a distribution to `gradle-to-trace-converter/distribution`
4. Add alias to a shell startup script (e.g. `.zshrc`, `.bashrc` etc.) to path of the distribution, or add the
   distribution path to `$PATH`

Example of an alias for macOS:

```sh
alias gtc="/Users/user/workspace/gradle-to-trace-converter/distribution/bin/gtc"
```

Note: You can also modify the distribution installation directory with `gtc.install.dir` Gradle property or System
property.

## Usage

Get the build operation trace for your build:

```sh
cd /path/to/project

./gradlew -Dorg.gradle.internal.operations.trace.tree=false -Dorg.gradle.internal.operations.trace=/path/to/project/trace

# Creates trace: /path/to/project/trace-log.txt 
```

Now you can convert the trace to a Chrome trace:

```sh
gtc -o chrome /path/to/project/trace-log.txt 
```

It is also possible to filter the trace.
Include (`-i`) only the operations in the `Run tasks` **subtree**
and exclude (`-e`) any operations starting with `Download` word.

```sh
gtc -o chrome /path/to/project/trace-log.txt -i "Run tasks" -e "Download.*"'
```
