# Offline verification (restricted sandbox)

The sandbox cannot reach `repo.papermc.io` or `services.gradle.org`, so a real
Gradle build is impossible here. Instead:

- `stubs/` contains APPROXIMATE Bukkit/Paper/Adventure API shapes so `javac`
  can type-check every plugin source. Risky signatures (chat event, craft
  event, death event, broadcast, materials, entity types) were verified against
  PaperMC/Paper GitHub source; the rest are long-stable APIs.
- `VerifyMain.java` executes the Bukkit-free logic (cooldowns, diary,
  validator, payload, keystore AES, counters, bounties, prompts).

Run (needs only JDK 21):

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
rm -rf /tmp/mcverify-classes && mkdir -p /tmp/mcverify-classes
find src/main/java tools/offline-verify -name '*.java' > /tmp/mcsrc.txt
javac -encoding UTF-8 -d /tmp/mcverify-classes @/tmp/mcsrc.txt
java -cp /tmp/mcverify-classes verify.VerifyMain
```

The REAL build must run where Paper's repo is reachable (Lightning AI box):
see project README.
