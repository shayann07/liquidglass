# Publishing

Maintainer notes. Consumers only need the coordinates:

```kotlin
implementation("dev.shayxo.liquidglass:liquidglass:0.1.0")
```

## Coordinates

`GROUP`, `POM_ARTIFACT_ID` and `VERSION_NAME` in the root `gradle.properties` are the whole of
it. The published artifacts are the Kotlin Multiplatform root module `liquidglass`, which
carries the Gradle module metadata a KMP consumer resolves through, plus `liquidglass-android`
and `liquidglass-jvm`. A consumer declares the root and Gradle picks the variant.

## Locally

```bash
./gradlew publishToMavenLocal
```

puts every artifact in `~/.m2/repository/dev/shayxo/liquidglass/`. A consuming project adds
`mavenLocal()` to its repositories, restricted to this group so that a stale `~/.m2` cannot
shadow anything else:

```kotlin
// settings.gradle.kts, dependencyResolutionManagement.repositories
mavenLocal { content { includeGroup("dev.shayxo.liquidglass") } }
```

No signing key is needed for this. Signing switches on only when a key is present.

## Maven Central

Publishing goes through Sonatype's Central Portal, via the
[vanniktech maven-publish plugin](https://github.com/vanniktech/gradle-maven-publish-plugin).

### Once

1. **An account** at [central.sonatype.com](https://central.sonatype.com).
2. **A verified namespace.** The group is `dev.shayxo.liquidglass`, under the namespace
   `dev.shayxo`, which is verified once by proving ownership of `shayxo.dev`: in the portal,
   Namespaces, Add Namespace, enter `dev.shayxo`; it hands you a verification key to publish as
   a DNS TXT record on the apex domain, then a Verify button. Every group under a verified
   namespace is covered, so future artifacts need nothing more. The Kotlin package remains
   `com.wexpa.liquidglass`; Central checks the group, not the package. A group cannot be
   renamed after the first release without orphaning consumers, which is why this was settled
   first.
3. **A user token** from the portal (Account, then Generate User Token). This is the username
   and password the plugin uses, not the account login.
4. **A GPG key.** `gpg --full-generate-key`, then publish it:
   `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`. Export the secret key in the
   form the plugin reads: `gpg --export-secret-keys --armor <KEY_ID>`.

### Credentials

Never in the repository. Locally, in `~/.gradle/gradle.properties`:

```
mavenCentralUsername=<token username>
mavenCentralPassword=<token password>
signingInMemoryKey=<the armored secret key, newlines as \n>
signingInMemoryKeyPassword=<key passphrase>
```

In GitHub Actions, the same four as repository secrets named `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` and `SIGNING_KEY_PASSWORD`; the publish workflow maps
them onto `ORG_GRADLE_PROJECT_*` variables, which Gradle reads as project properties.

### Each release

1. Set `VERSION_NAME` in `gradle.properties` and add the version to `CHANGELOG.md`.
2. Commit, then tag and push:
   ```bash
   git tag v0.1.0 && git push origin main v0.1.0
   ```
3. `.github/workflows/publish.yml` checks the tag against `VERSION_NAME`, runs the tests, and
   runs `publishAndReleaseToMavenCentral`, which uploads, validates and releases in one step.
   The artifacts appear on Central within about half an hour, and in search a little later.

To publish from a machine instead:

```bash
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

`publishToMavenCentral` (without `AndRelease`) uploads and stops, leaving the deployment for a
manual release in the portal, which is the safer choice for a first attempt.
