# Offline verification (restricted sandbox)

The sandbox cannot reach `repo.papermc.io` or `services.gradle.org`, so a real
Gradle build is impossible here. Instead:

- `stubs/` contains APPROXIMATE Bukkit/Paper/Adventure API shapes so `javac`
  can type-check every plugin source. Risky signatures (chat event, craft
  event, death event, broadcast, materials, entity types) were verified against
  PaperMC/Paper GitHub source; the rest are long-stable APIs.
- `VerifyMain.java` executes the Bukkit-free logic (cooldowns, diary,
  validator, payload, keystore AES, counters, bounties, prompts): 47 checks.
- `IntegrationTest.java` drives the REAL listeners -> dispatcher -> validator
  -> diary -> broadcast path with fake players/events, an inline scheduler,
  and a canned AI gateway: 17 checks (craft threshold, opt-out, mentions,
  OP-gated keys, murder bounty + caps, bounty claim, dimension, milk
  backpressure).
- `ServerTest.java` boots the REAL `McRoastBotPlugin` against a fake server:
  real `config.yml`, real `system-prompt.txt`, SnakeYAML-backed persistence,
  14 checks (boot, keyadd, aitest pipeline, graceful AI failure, file saves
  with no plaintext secrets, cross-reboot restore of keys/diary/name).

Run (needs only JDK 21 + SnakeYAML, fetched once from Maven Central):

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
mkdir -p tools/offline-verify/lib
curl -sL -o tools/offline-verify/lib/snakeyaml-2.2.jar \
  https://repo.maven.apache.org/maven2/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar
rm -rf /tmp/mcverify-classes && mkdir -p /tmp/mcverify-classes
find src/main/java tools/offline-verify -name '*.java' > /tmp/mcsrc.txt
javac -encoding UTF-8 -cp tools/offline-verify/lib/snakeyaml-2.2.jar \
  -d /tmp/mcverify-classes @/tmp/mcsrc.txt
CP=/tmp/mcverify-classes:tools/offline-verify/lib/snakeyaml-2.2.jar
java -cp $CP verify.VerifyMain
java -cp $CP verify.IntegrationTest
java -cp $CP verify.ServerTest
```

Assemble the shippable jar offline (plugin classes + resources, no stubs):

```
rm -rf /tmp/jarclasses && mkdir -p /tmp/jarclasses
find src/main/java -name '*.java' > /tmp/pluginsrc.txt
javac -encoding UTF-8 -implicit:none \
  --source-path tools/offline-verify/stubs -d /tmp/jarclasses @/tmp/pluginsrc.txt
cp -r src/main/resources/* /tmp/jarclasses/
(cd /tmp/jarclasses && jar cf /tmp/McRoastBot-0.1.0.jar .)
jar tf /tmp/McRoastBot-0.1.0.jar | grep -c -E '^(org|net|io)/' # expect 0
```

This jar is structurally complete but compiled against APPROXIMATE stubs, so
it is a packaging check, not a shippable artifact.

The REAL build must run where Paper's repo is reachable (Lightning AI box):
see project README.
