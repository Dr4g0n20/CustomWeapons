# ChaosWeapons (Spigot 1.12.2)

24 chaotic weapons and support items. Get them with `/weapon give <id>`.

All files are in one folder (no sub-folders). pom.xml points Maven at the root folder.

## Build
Requires Java 8+ and Maven:  mvn clean package
The jar is created at target/ChaosWeapons.jar.

## On GitHub
Upload every file in this folder to the root of a repo. Then create the file
.github/workflows/build.yml in the GitHub web editor and paste in the contents of build.yml.
Open the Actions tab to watch the build and download the jar.

## Commands
/weapon give <id> [player], list, info <id>, guide, reload
