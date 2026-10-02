# One development workspace

Use the sibling `liquidglass` and `Vitals` checkouts on `main`. Atlas Studio is the
desktop sample **inside liquidglass**, not another copy of Vitals. The former
Astra, Fable and Antigravity workspaces are retired. Their names in old research
records describe provenance, not instructions to resume those workspaces.

## Build on Windows

Use JDK 21 (`JAVA_HOME`) and an Android SDK (`ANDROID_HOME` or each repository's
`local.properties`). From the liquidglass root:

```powershell
./tools/workspace.ps1 library :desktop:run
./tools/workspace.ps1 library :desktop:compileKotlinDesktop :sample:assembleDebug
./tools/workspace.ps1 library :liquidglass:jvmTest --rerun
./tools/workspace.ps1 library publishToMavenLocal
./tools/workspace.ps1 app :androidApp:assembleDebug :shared:jvmTest
./tools/workspace.ps1 app :desktopApp:run
```

The wrapper gives both repositories the same private Gradle cache and Maven
repository under `liquidglass/.local/`. It also isolates temporary files. It does
not publish remotely, switch branches, or read signing credentials. Optional
`LIQUIDGLASS_WORK_HOME` relocates that local data; use the same value for both
publishing and consuming. Keep Vitals' version catalog aligned with the library's
`VERSION_NAME`. On other systems use the Gradle wrappers with the same
`GRADLE_USER_HOME` and `-Dmaven.repo.local` for both repositories.

## Atlas Studio

`launch_desktop.bat` starts `:desktop:run` through the shared wrapper. Its JVM
window calls `LoupeScreen`, shared with Android under
`sample/src/main/kotlin/shared/`. At 960dp and wider it shows a searchable object
catalog, star canvas, floating glass tools and collapsible inspector. Narrower
windows use the mobile layout. The sky is sample data; this is a material demo,
not a live astronomy instrument.

The library retains the selected Astra interaction and subsequent Claude fixes;
the desktop shell incorporates Antigravity's latest local work. This consolidation
does not retune the material or establish 1:1 iOS parity. The existing r14 APK is a
historical review artifact, not a build of this desktop update. Native desktop
visual and interaction validation remains separate from compilation and tests.

## Recovery and local evidence

[Retired source history](archive/retired-workflows/2026-10-03/README.md) is committed
on GitHub, including the previously uncommitted alternative implementations.
The manifest names every retired branch and its exact commit. Restore an
alternative in a separate clone only when needed; there is one active approach.

On the owner's PC, `.local/consolidation-2026-10-03/` holds exact dirty-file
backups and the cleanup manifest. Original reference material stays in
`research/`; unique retired evidence is preserved locally. Private transcripts,
recordings, caches, SDK configuration and signing files are not GitHub content.

Physical testing still uses the shared atomic lease at sibling
`liquidglass-coordination/DEVICE-LEASE.json`. Read its `COORDINATION.md` before
device operations. Never kill unrelated Java processes or steal a device lease.
