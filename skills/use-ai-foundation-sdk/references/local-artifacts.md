# Inspect installed AI Foundation artifacts

Prefer installed artifacts because they match the target project's resolved SDK version. Use the
repository checkout only when the artifacts do not contain the required documentation or
implementation.

## Browser and Vue SDK

Resolve the package from the target frontend project:

```bash
sdk_package_json=$(node -p \
  "require.resolve('@halo-dev/ai-foundation-sdk/package.json')")
sdk_package_dir=$(cd "$(dirname "$sdk_package_json")" && pwd -P)
```

Then inspect:

- `package.json` for the exact installed version and export map.
- `dist/index.d.ts` for the public root exports.
- Other `dist/*.d.ts` files reached from the root declarations for signatures and types.
- `dist/*.js` only when runtime behavior is required.

Do not treat a declaration that is absent from the package's root exports as public. If the package
is declared but `node_modules` is absent, read its version from the active lockfile. Do not add or
upgrade the dependency merely to inspect it.

## Java SDK

Resolve the actual version using the target module and configuration when it is not obvious from
the build:

```bash
./gradlew :<module>:dependencyInsight \
  --dependency run.halo.aifoundation:api \
  --configuration compileClasspath
```

Locate the resolved artifacts in the Gradle user home:

```bash
gradle_cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
artifact_dir="$gradle_cache/run.halo.aifoundation/api/<version>"
find "$artifact_dir" -type f \
  \( -name '*-sources.jar' -o -name 'api-*.jar' \) -print
```

For a dependency published with `publishToMavenLocal`, also check the default Maven local path:

```bash
artifact_dir="$HOME/.m2/repository/run/halo/aifoundation/api/<version>"
find "$artifact_dir" -type f \
  \( -name '*-sources.jar' -o -name 'api-*.jar' \) -print
```

List or read source files without modifying the Gradle cache:

```bash
jar tf <sources.jar>
unzip -p <sources.jar> \
  run/halo/aifoundation/chat/GenerateTextRequest.java
```

For searches across many Java sources, extract the source JAR into an ignored reference directory:

```bash
mkdir -p .reference/ai-foundation-api-<version>
cd .reference/ai-foundation-api-<version>
jar xf <sources.jar>
rg -n 'GenerateTextRequest|streamText' .
```

If a source JAR is unavailable, inspect a public signature from the binary JAR:

```bash
javap -classpath <api.jar> -public \
  run.halo.aifoundation.chat.GenerateTextRequest
```

The Java artifact describes the public SDK contract, but it does not contain the plugin
implementation or the complete developer documentation.
