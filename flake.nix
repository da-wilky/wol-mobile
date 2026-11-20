{
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = { allowUnfree = true; android_sdk.accept_license = true; };
      };
      buildTools = "37.0.0";
      # The emulator and its system image are a multi-GB download, so they sit in a separate shell.
      composeAndroid = { withEmulator }: pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37.0" ];
        buildToolsVersions = [ buildTools ];
        includeEmulator = withEmulator;
        includeSystemImages = withEmulator;
        systemImageTypes = [ "google_apis_playstore" ];
        abiVersions = [ "x86_64" ];
      };
      # nixpkgs androidenv stops at 37.0; current androidx needs >= 37.1.
      platform = pkgs.fetchzip {
        url = "https://dl.google.com/android/repository/platform-37.2_r02.zip";
        hash = "sha256-5LfTou40+i1SY/d6G9UNOxNunxv0JxXqxyG51wEkKuQ=";
      };
      sdkFor = android: pkgs.runCommand "android-sdk" { } ''
        mkdir -p $out/platforms
        for entry in ${android.androidsdk}/libexec/android-sdk/*; do
          [ "$(basename "$entry")" = platforms ] || ln -s "$entry" $out/
        done
        dir=$out/platforms/android-37.2
        mkdir $dir
        ln -s ${platform}/* $dir/
        # sdkmanager only recognises a platform with package.xml; derive it from the 37.0 one.
        sed -e "s/37\.0/37.2/g" -e "s/<extension-level>22</<extension-level>24</" -e "s/<major>1</<major>2</" \
          ${android.androidsdk}/libexec/android-sdk/platforms/android-37.0/package.xml > $dir/package.xml
      '';
      shellFor = android:
        let sdk = sdkFor android;
        in pkgs.mkShell {
          packages = [ pkgs.jdk21 android.platform-tools ];
          ANDROID_HOME = sdk;
          # aapt2 that AGP downloads from Maven is dynamically linked and won't run on NixOS.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildTools}/aapt2";
        };
      emulatorAndroid = composeAndroid { withEmulator = true; };
      emulatorSdk = sdkFor emulatorAndroid;
      # Headless, for working over SSH: `nix run .#emulator [-- stop]`.
      emulatorApp = pkgs.writeShellApplication {
        name = "wolmobile-emulator";
        runtimeInputs = [ emulatorAndroid.androidsdk pkgs.jdk21 pkgs.procps pkgs.util-linux pkgs.gawk pkgs.gnugrep ];
        text = ''
          export ANDROID_HOME=${emulatorSdk} ANDROID_SDK_ROOT=${emulatorSdk} JAVA_HOME=${pkgs.jdk21.home}
          export ANDROID_AVD_HOME="$HOME/.android/avd"
          avd=wolmobile
          log="''${XDG_STATE_HOME:-$HOME/.local/state}/wolmobile-emulator.log"

          case "''${1:-start}" in
            start)
              if ! pgrep -u "$USER" -f "qemu-system.*-avd $avd" > /dev/null; then
                if ! avdmanager list avd -c | grep -qx "$avd"; then
                  echo no | avdmanager create avd -n "$avd" -k "system-images;android-37.0;google_apis_playstore;x86_64" -d pixel_7
                fi
                mkdir -p "$(dirname "$log")"
                # setsid: keeps running after the SSH session ends.
                setsid emulator -avd "$avd" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect \
                  > "$log" 2>&1 < /dev/null &
                echo "booting, log: $log"
              fi
              # shellcheck disable=SC2016 # expanded on the device
              adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 1; done'
              host=$(< /proc/sys/kernel/hostname)
              cat <<EOF

          Emulator running.

          On this machine:
            nix develop -c ./gradlew installDebug

          On another machine, to see and use it:
            ssh -N -L 5555:localhost:5555 $USER@$host
            # second terminal:
            nix shell nixpkgs#android-tools nixpkgs#scrcpy -c sh -c 'adb connect localhost:5555 && scrcpy -s localhost:5555'

          Stop with: nix run .#emulator -- stop
          EOF
              ;;
            stop)
              for serial in $(adb devices | awk '/^emulator-/ {print $1}'); do
                adb -s "$serial" emu kill
              done
              ;;
            *)
              echo "usage: wolmobile-emulator [start|stop]" >&2
              exit 1
              ;;
          esac
        '';
      };
    in {
      devShells.${system} = {
        default = shellFor (composeAndroid { withEmulator = false; });
        emulator = shellFor emulatorAndroid;
      };
      apps.${system}.emulator = {
        type = "app";
        program = "${emulatorApp}/bin/wolmobile-emulator";
        meta.description = "Headless Android emulator for testing over SSH";
      };
    };
}
