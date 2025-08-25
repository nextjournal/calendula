{
  description = "A Nix-flake-based development environment for application.garden";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    garden.url = "github:nextjournal/garden-cli";
  };

  outputs = { self, nixpkgs, garden }:
    let
      # Latest LTS releases as of Nov 2023
      javaVersion = 21;
      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSupportedSystem = f: nixpkgs.lib.genAttrs supportedSystems (system: f {
        pkgs = import nixpkgs {
          inherit system;
          overlays = [
            garden.overlays.default
          ];
        };
      });
    in {
      devShells = forEachSupportedSystem ({ pkgs }: {
        default = pkgs.mkShell {
            buildInputs = [ pkgs.clojure pkgs.garden ];
          };
      });
    };
}
