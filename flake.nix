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
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37.0" ];
        buildToolsVersions = [ buildTools ];
      };
      # nixpkgs androidenv stops at 37.0; current androidx needs >= 37.1.
      platform = pkgs.fetchzip {
        url = "https://dl.google.com/android/repository/platform-37.2_r02.zip";
        hash = "sha256-5LfTou40+i1SY/d6G9UNOxNunxv0JxXqxyG51wEkKuQ=";
      };
      sdk = pkgs.runCommand "android-sdk" { } ''
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
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [ pkgs.jdk21 android.platform-tools ];
        ANDROID_HOME = sdk;
        # aapt2 that AGP downloads from Maven is dynamically linked and won't run on NixOS.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildTools}/aapt2";
      };
    };
}
