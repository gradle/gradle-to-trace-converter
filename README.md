# Gradle Build Operation Trace Converter

Command-line tool for analysis and conversion of Build Operation traces of Gradle Build Tool into other formats.

## Usage

Here is an example of how to produce a Chrome trace out of the build operation trace.
We include (`-i`) only the operations in the `Run tasks` subtree and exclude (`-e`) any operations starting with `Download` word.

```
./gradlew :app:run --args='<trace file> -i "Run tasks" -e "Download.*"'
```

Getting a transform summary CSV is possible with the following command:

```
./gradlew :app:run --args='<trace file> -o transform-summary'
```
