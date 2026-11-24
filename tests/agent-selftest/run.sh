#!/usr/bin/env bash
# Self test for the Java agent: simulates Quarkus' FacadeClassLoader @Disabled check.
# Needs only a JDK (17+). Usage: tests/agent-selftest/run.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/tests/agent-selftest/src"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

# 1. agent classes (exactly the classes the plugin packs into the agent jar)
mkdir -p "$OUT/agent"
javac --release 17 -d "$OUT/agent" "$ROOT"/plugin/src/io/github/christianlinzatti/rundisabled/agent/*.java
printf 'Premain-Class: io.github.christianlinzatti.rundisabled.agent.RunDisabledAgent\nAgent-Class: io.github.christianlinzatti.rundisabled.agent.RunDisabledAgent\n' > "$OUT/manifest.txt"
jar --create --file "$OUT/agent.jar" --manifest "$OUT/manifest.txt" -C "$OUT/agent" .

# 2. fake JUnit/Quarkus on the class path, test classes only visible to the "peeking" loader
mkdir -p "$OUT/cp" "$OUT/testclasses"
javac --release 17 -d "$OUT/cp" $(find "$SRC/fake" "$SRC/selftest" -name '*.java')
javac --release 17 -cp "$OUT/cp" -d "$OUT/testclasses" $(find "$SRC/testclasses" -name '*.java')

# 3. without agent: Quarkus would NOT load @Disabled classes as QuarkusTest (the reported error)
java -cp "$OUT/cp" SelfTest "$OUT/testclasses" false
# 4. with agent: @Disabled classes are loaded as QuarkusTest
java -javaagent:"$OUT/agent.jar" -cp "$OUT/cp" SelfTest "$OUT/testclasses" true
