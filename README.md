# ledger-wallet-daemon &middot; [![CircleCI](https://circleci.com/gh/LedgerHQ/ledger-wallet-daemon.svg?style=shield)](https://circleci.com/gh/LedgerHQ/ledger-wallet-daemon)

## Updating the libcore

When a new version of the libcore is available, we need to update our bindings.

| Prerequisite                                   | Remarks |
| ============================================== | ========================================= |
| [docker](https://www.docker.com/get-started)   | Only needed if you plan to build in local |
| [sbt](https://www.scala-sbt.org/download.html) |                                           |

1. Check out [lib core project](https://github.com/LedgerHQ/lib-ledger-core)
   ```bash
   git clone --recurse-submodules --depth 1 --branch <branch_or_version_tag> https://github.com/LedgerHQ/lib-ledger-core
   ```

2. Copy `build-jar.sh` and `build-jar-linux.sh` to the lib core project folder
   ```bash
   cp $WALLET_DAEMON_FOLDER/build-jar.sh $LIB_CORE_FOLDER
   cp $WALLET_DAEMON_FOLDER/build-jar-linux.sh $LIB_CORE_FOLDER
   ```

3. `cd` to lib core folder
   ```bash
   cd $LIB_CORE_FOLDER
   ```

4. Two possible situations: either you want a fresh build from your local machine, or you want to
   grab the official lib core artifacts.
   1. For building on your machine, run the script `build-jar.sh` with command: `mac`, `linux` or `all`.
      MacOS can build both `mac` and `linux`. Linux can only build `linux`.
      ```bash
      # Build for mac only. You may want to do this when developing,
      # it's much faster than build for both mac and linux
      bash build-jar.sh mac

      # Build for linux. Linux build is using docker.
      bash build-jar.sh linux

      # Build for both mac and linux
      bash build-jar.sh all
      ```
   2. For getting the official release, pass an extra argument `dl` to the command invocation.
      ```bash
      # Get only the mac release artifacts.
      ./build-jar.sh mac dl

      # Get only the linux artifacts.
      ./build-jar.sh linux dl

      # Get both.
      ./build-jar.sh all dl
      ```

5. Then, you find the jar file in `$LIB_CORE_FOLDER/../build-jar/target/scala-<version>/build-jar-<version>.jar`.
   Replace the `ledger-lib-core.jar` in the lib folder with this file.
   ```bash
   mv $LIB_CORE_FOLDER/../build-jar/target/scala-<version>/build-jar-<version>.jar $WALLET_DAEMON_FOLDER/lib/ledger-lib-core.jar
   ```

6. Push the changes to upstream

7. Add a tag on the new commit with the version of the ledger-core-lib, or the commit
hash if no version was tagged

## Configuring the daemon when running on Docker

An example file of env file is available under `sample/docker.env`. You can then write your env file and use it to configure
the daemon.
