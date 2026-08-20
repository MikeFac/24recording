# Server upload pilot

The recording client keeps the finalized M4A and its SHA-256 in the local
SQLite outbox. It does not denoise or transcribe completed chunks on the phone.

## Configure

Open **Configure server upload** in the app and enter:

- Server URL: `https://e-agent.4you.uno`
- API key: the pilot `AGENT_API_KEY` from the server's private
  `/opt/executiveassistant/.env.production` file.

The key is stored in the app's private preferences for this personal pilot. Do
not distribute the APK or key.

## Build

Use the existing Gradle installation and JDK 17:

```sh
JAVA_HOME=/home/michael/.local/quietnote-jdk-17/usr/lib/jvm/java-17-openjdk-amd64 \
  PATH="$JAVA_HOME/bin:$PATH" \
  /home/michael/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle assembleDebug
```

The APK is produced under `app/build/outputs/apk/debug/`.

Each completed chunk is uploaded in order over HTTPS. The server verifies byte
length and checksum before returning success. If the network is unavailable,
the chunk remains local and is retried when connectivity returns. Original
audio is never deleted or replaced by the client.
