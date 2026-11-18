# Publishing

Maintainer notes. Consumers only need the [install instructions](getting-started.md#install).

One tag publishes one version everywhere:

| Registry | Packages | Secrets |
| :-- | :-- | :-- |
| Maven Central | `liquidglass`, `liquidglass-material3`, `liquidglass-bom`, and the Gradle plugin `dev.shayxo.liquidglass` (artifact `liquidglass-gradle-plugin` plus its plugin marker) | `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_KEY_PASSWORD` (set up since 0.1.0) |
| npm | `@shayxo.dev/liquidglass`, the `<liquid-glass>` web component; `@shayxo.dev/liquidglass-react-native` | none: trusted publishing (from 0.2.2; 0.2.1 used a token) |
| NuGet | `LiquidGlass` (the `LiquidGlass.Skia` project), `LiquidGlass.WinForms`, `LiquidGlass.Wpf`, `LiquidGlass.Avalonia`, `LiquidGlass.Maui` | none: trusted publishing |
| pub.dev | `liquiglass`, the Flutter package (`ports/flutter`) | none: automated publishing (GitHub Actions OIDC), once the owner's first upload exists |
| GitHub | a release with the notes in `.github/release-notes/vX.Y.Z.md` | none (the workflow's token) |

The Gradle plugin is on Maven Central rather than the Gradle Plugin Portal, under the same signing key.
`plugins { id("dev.shayxo.liquidglass") }` finds it through its plugin marker in any build that lists
`mavenCentral()` in `pluginManagement`, which the Android Studio and Kotlin Multiplatform templates
already do. Listing it on the Plugin Portal as well is optional and not set up.

## The version

`VERSION_NAME` in the root `gradle.properties` is the one number. The Gradle builds read it, the
NuGet packages read it (`ports/dotnet/Package.props`), and the npm job stamps it into `package.json`
at publish time. The Flutter package writes it in `ports/flutter/pubspec.yaml`, with `-SNAPSHOT` spelt
`-dev` as Dart does (`0.3.0-SNAPSHOT` is `0.3.0-dev`), and heads `ports/flutter/CHANGELOG.md` with it;
the script does both, and CI fails if the pubspec drifts. The repository keeps both npm packages (`ports/npm`, `ports/react-native`) at
`"private": true` and `0.0.0`, so a stray `npm publish` from a checkout cannot go out. Change it with the script, which also rewrites the
.NET lock files that record it:

```bash
python .github/scripts/set_version.py 0.2.0
```

## Locally

```bash
./gradlew publishToMavenLocal                      # the library, Material 3 and the BOM
./gradlew -p gradle-plugin publishToMavenLocal     # the Gradle plugin and its marker
```

puts every artifact in `~/.m2/repository/dev/shayxo/liquidglass/`. A consuming project adds
`mavenLocal()` to its repositories (and to `pluginManagement` for the plugin), restricted to this
group so that a stale `~/.m2` cannot shadow anything else:

```kotlin
// settings.gradle.kts, in pluginManagement.repositories and dependencyResolutionManagement.repositories
mavenLocal { content { includeGroupAndSubgroups("dev.shayxo") } }
```

No signing key is needed for this. Signing switches on only when a key is present.

## Each release

1. `python .github/scripts/set_version.py X.Y.Z`.
2. Write `.github/release-notes/vX.Y.Z.md` (the GitHub release body) and give `CHANGELOG.md` the
   dated `## X.Y.Z` heading.
3. Update the install snippets that name a version or say "unreleased":
   `grep -rn "unreleased\|SNAPSHOT" README.md docs/*.md`, plus the previous version number (for
   example `1\.0\.0` when releasing 1.1.0), ignoring historical mentions in changelogs and dated entries.
4. Open a pull request. Its `Publish` checks run the whole release as a dry run: every package is
   built, packed and checked, nothing is uploaded.
5. Merge, wait for CI on `main`, then tag the merge commit:
   ```bash
   git tag vX.Y.Z <merge commit> && git push origin vX.Y.Z
   ```
6. `.github/workflows/publish.yml` refuses unless the tag matches `VERSION_NAME`, the version is not a
   SNAPSHOT, the commit is on `main` and passed CI there, and the release notes exist. Then each
   registry is its own job, and the GitHub release follows them. Maven Central shows the artifacts
   within about half an hour.
7. If one job fails (a missing secret or policy, an outage), fix the cause and run
   `gh run rerun <id> --failed`. The others stay published; npm and NuGet skip a version that is
   already there. A rerun runs the workflow and sources as tagged, so a fix to either needs a new
   patch version: 0.2.0 reached Maven Central only (npm refused its package names, and the NuGet
   upload step had a path bug), and 0.2.1 shipped the same code everywhere.
8. `python .github/scripts/set_version.py X.Y+1.0-SNAPSHOT` on `main`.

A version with a pre-release suffix (`0.3.0-beta.1`) goes to npm under the `next` dist-tag and is
marked as a pre-release on GitHub; NuGet and Maven treat it as a pre-release by its suffix.

## npm, once

1. An account at [npmjs.com](https://www.npmjs.com) with two-factor authentication.
2. The packages live under the account's scope, `@shayxo.dev`. The unscoped names `liquidglass` and
   `liquidglass-react-native` were free on 19 October 2026, but npm refused them on publish as too
   similar to `liquid-glass` and `liquid-glass-react-native`. `npm view` cannot see that rule, so a
   new unscoped name is only known to be accepted once it is published.
3. npm configures [trusted publishing](https://docs.npmjs.com/trusted-publishers) per package, so
   only once a package exists. The first publish of a new package therefore needs a granular access
   token (Account, Access Tokens, Generate New Token, Granular: read and write on packages, two-factor
   bypass allowed) in the `NPM_TOKEN` secret, and the npm job's Publish step needs
   `env: NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}` for that one run. 0.2.1 published both packages
   that way.
4. Then, for each package: Settings, Trusted Publisher, GitHub Actions; owner `shayann07`,
   repository `liquidglass`, workflow filename `publish.yml`, no environment; tick **Allow npm
   publish** (without it the publisher may only stage a publish, and the workflow publishes
   directly); Set up connection. Under Publishing access choose **Require two-factor authentication
   and disallow bypass 2fa tokens**. Delete the token on npmjs.com and
   `gh secret delete NPM_TOKEN --repo shayann07/liquidglass`. Both packages were set up this way on
   20 October 2026.
5. npm marks a new trusted publisher *pending validation* until a publish goes through it, within
   about a day. 0.2.2 was released to validate it.

Packages are published with [provenance](https://docs.npmjs.com/generating-provenance-statements):
npm shows that each version was built by this workflow from this repository.

The npm jobs run npm 11 (trusted publishing needs 11.5.1 or later) with `id-token: write`; npm
exchanges the run's OIDC token for a one-time credential, so no npm secret exists.

## pub.dev, once

The Flutter package is `liquiglass`. pub.dev refuses a new name that matches an existing one once
underscores are removed, so `liquidglass` is unavailable (`liquid_glass` exists), and so are
`liquidglass_flutter` and `flutter_liquidglass`.

pub.dev turns on automated publishing per package, so only once the package exists. The first upload
is therefore the owner's, by hand, with their Google account. Until then the `pub-dev` job stands down
with a warning, and the other registries publish as usual.

**First upload** (from `main`, any machine with Flutter 3.35 or later):

1. `cd ports/flutter`
2. `flutter pub publish --dry-run` must end with `Package has 0 warnings.` A dirty git tree counts as
   a warning, so publish from a clean checkout.
3. `flutter pub publish`. It prints a `https://accounts.google.com/...` link and waits.
4. Open the link, choose the Google account that should own the package, and click **Allow** (pub.dev
   asks to see your email address). The browser says *Authorization complete*.
5. Back in the terminal, answer `y` to *Do you want to publish liquiglass ...?*. The package appears at
   <https://pub.dev/packages/liquiglass> within a minute; its score takes a few minutes more.

**Optional: a verified publisher.** A badge with your domain instead of your email:

1. <https://pub.dev/create-publisher>, enter `shayxo.dev`, **Create publisher**.
2. pub.dev sends you to Google Search Console to prove you own the domain: add the DNS TXT record it
   shows at your DNS provider, wait for it to resolve, click **Verify**.
3. <https://pub.dev/packages/liquiglass/admin>, **Transfer to publisher**, choose `shayxo.dev`.

**Automated publishing** (after the first upload):

1. <https://pub.dev/packages/liquiglass/admin>, section **Automated publishing**.
2. Tick **Enable publishing from GitHub Actions**.
3. Repository: `shayann07/liquidglass`. Tag pattern: `v{{version}}`.
4. Leave **Enable publishing with workflow_dispatch events** unticked and **Require GitHub Actions
   environment** empty (the job uses none).
5. **Update**.

From then on a tag `vX.Y.Z` publishes `liquiglass X.Y.Z`. `dart-lang/setup-dart` exchanges the run's
OIDC token (`id-token: write`) for a pub.dev credential, and pub.dev accepts it only for a tag push in
this repository whose tag matches the pattern and the pubspec version. The job skips a version pub.dev
already has.

## NuGet, once

NuGet publishes through [trusted publishing](https://learn.microsoft.com/nuget/nuget-org/trusted-publishing):
no API key is stored. The workflow signs in with `NuGet/login`, which exchanges the run's GitHub OIDC
token for an API key valid for an hour.

1. An account at [nuget.org](https://www.nuget.org) (a Microsoft account); the workflow signs in as
   `shayxo.dev`.
2. Account, Trusted Publishing, Create: package owner `shayxo.dev`, repository owner `shayann07`,
   repository `liquidglass`, workflow file `publish.yml`, no environment (set up on 19 October 2026).
3. The IDs `LiquidGlass`, `LiquidGlass.WinForms`, `LiquidGlass.Wpf` and `LiquidGlass.Avalonia` were
   free on 19 October 2026; the first push claims them. Optionally reserve the `LiquidGlass` prefix
   afterwards (nuget.org ID prefix reservation), which marks the packages as verified.

## Maven Central

Publishing goes through Sonatype's Central Portal, via the
[vanniktech maven-publish plugin](https://github.com/vanniktech/gradle-maven-publish-plugin), for the
library build and for the separate `gradle-plugin` build alike.

### Once (done for 0.1.0)

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

To publish from a machine instead of CI:

```bash
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
./gradlew -p gradle-plugin publishAndReleaseToMavenCentral --no-configuration-cache
```

`publishToMavenCentral` (without `AndRelease`) uploads and stops, leaving the deployment for a
manual release in the portal, which is the safer choice for a first attempt.

## Notes from the first release

Each of these cost time once, on 2026-09-10, and should not cost it again.

- **The signing passphrase must be plain ASCII.** Gradle's signing plugin hands the passphrase
  to BouncyCastle as Latin-1 while GnuPG derives the key from UTF-8, so a passphrase with any
  character outside ASCII fails at `signAndroidPublication` with
  `PGPException: checksum mismatch in checksum of 20 bytes`, even though gpg itself accepts it.
  Letters and digits only. Before pushing a tag, run the exact CI code path locally:
  ```powershell
  $env:ORG_GRADLE_PROJECT_signingInMemoryKey = (gpg --export-secret-keys --armor KEY_ID | Out-String)
  $env:ORG_GRADLE_PROJECT_signingInMemoryKeyPassword = [System.Net.NetworkCredential]::new('', (Read-Host 'passphrase' -AsSecureString)).Password
  .\gradlew.bat :liquidglass:signAndroidPublication --no-configuration-cache
  ```
  and once it passes, feed the secrets from those same variables so nothing is retyped:
  `$env:ORG_GRADLE_PROJECT_signingInMemoryKey | gh secret set SIGNING_KEY --repo shayann07/liquidglass`.
- **Never export a key with PowerShell's `>`.** It writes UTF-16, which gpg cannot read back.
  Use `gpg --export-secret-keys --armor --output key.asc KEY_ID`.
- **On Windows, gpg ships inside Git**: `Set-Alias gpg 'C:\Program Files\Git\usr\bin\gpg.exe'`,
  and `gpg-connect-agent` is in the same folder. GnuPG 2.4's `keyboxd` cannot start when gpg is
  launched from PowerShell, so a fresh `~/.gnupg/common.conf` needs its `use-keyboxd` line
  commented out before the first key can be generated.
- **Namespace verification is a button, not a poll.** Once the TXT record exists, use the
  namespace row's menu, Verify Namespace. Check the record on *both* authoritative servers:
  Dynadot's `ns1` served it within minutes while `ns2` kept the old zone for over an hour on
  some of its anycast nodes, without bumping the zone serial, so any resolver that happened to
  ask `ns2` saw nothing.
- **GitHub started two publish runs for the v0.2.1 tag**, three seconds apart, from one push. The
  concurrency group queued the second until the first had published everything, but the registries
  did not show the new version yet: `npm view` still missed it and Central was still finishing the
  first deployment, so the second run failed (harmlessly) on every upload. Since 0.2.2 the `check`
  job stands down when an earlier run for the same tag has succeeded.
- **A failed publish run can be rerun on the same tag** with `gh run rerun <id> --failed`.
  Nothing reaches Central until signing passes, so a signing failure leaves no partial
  deployment behind.
