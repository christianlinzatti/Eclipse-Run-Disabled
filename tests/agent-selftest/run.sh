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

# 5. exit watchdog: a blocking shutdown hook must not keep the test JVM alive ...
start=$(date +%s)
java -Drundisabled.exitTimeout=2 -javaagent:"$OUT/agent.jar" -cp "$OUT/cp" ExitTest hang 2> "$OUT/hang.err" || true
took=$(( $(date +%s) - start ))
grep -q "blocking-shutdown-hook" "$OUT/hang.err" || { echo "FAIL: no thread dump"; cat "$OUT/hang.err"; exit 1; }
[ "$took" -lt 15 ] || { echo "FAIL: hanging JVM took ${took}s"; exit 1; }
echo "  ok: hanging shutdown hook ended by watchdog after ${took}s"
# ... and must not delay a normal exit
start=$(date +%s)
java -javaagent:"$OUT/agent.jar" -cp "$OUT/cp" ExitTest normal
took=$(( $(date +%s) - start ))
[ "$took" -lt 5 ] || { echo "FAIL: normal exit took ${took}s"; exit 1; }
echo "  ok: normal exit not delayed (${took}s)"
echo "SELFTEST OK (exit watchdog)"
