# Modrinth publishing

`.modrinth/project.json` holds Action Assist's Modrinth project metadata: its categories and client-only side support.

The release workflow (`.github/workflows/release.yml`) publishes only the GitHub Release. The shared FabricModdingConventions release workflow uploads one Fabric JAR and one NeoForge JAR per release, while Action Assist ships a Fabric and a NeoForge JAR for each of its eight Minecraft versions. Upload the JARs from the GitHub Release to Modrinth by hand, one Modrinth version per Minecraft version and loader, using the release's tag notes as the changelog.

Each JAR's file name gives its Minecraft version and loader: `actionassist-<minecraft>-<loader>-<version>.jar`. Mark every version client-side only (`client_side=required`, `server_side=unsupported`).
