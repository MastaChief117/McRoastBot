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

Run (needs only JDK 21):

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
rm -rf /tmp/mcverify-classes && mkdir -p /tmp/mcverify-classes
find src/main/java tools/offline-verify -name '*.java' > /tmp/mcsrc.txt
javac -encoding UTF-8 -d /tmp/mcverify-classes @/tmp/mcsrc.txt
java -cp /tmp/mcverify-classes verify.VerifyMain
java -cp /tmp/mcverify-classes verify.IntegrationTest
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
