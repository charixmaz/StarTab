# StarTab — Starfall custom player list

Custom TAB header, player rank prefixes/sorting, player counts, ping, and footer for **Paper 26.1.2** (Java 25, Maven).

**Nexo is not required.** StarfallTab has its own complete Minecraft resource pack in `resourcepack/`. GitHub Actions publishes **two artifacts**:
- `StarfallTab-jar` — Paper plugin
- `StarfallTab-ResourcePack` — standalone Minecraft Java 26.1.2 resource-pack ZIP

## Installation
1. Download the plugin JAR and place it in `plugins/`. Restart your Paper server.
2. Distribute the standalone resource pack:
   - **If Nexo already manages your server's pack:** Copy `StarfallTab-ResourcePack.zip` to `plugins/Nexo/pack/external_packs/` and rebuild/resend your Nexo pack. No Nexo glyph configuration is necessary.
   - **Without Nexo:** Host the ZIP over HTTPS and set `resource-pack=<url>` in `server.properties` (optionally `require-resource-pack=true`).
   - Alternatively, load it with your existing multipack delivery plugin. Avoid pack managers overriding each other's resource-pack requests.
3. The default `config.yml` logo is `logo: '&f'` (character **U+E7F1**, supplied by the pack). Change it to `logo: '&b&lSTARFALL'` for text-only display.
4. Edit header/footer/ranks at `plugins/StarfallTab/config.yml`, then run `/stab reload`.

## Custom logo PNG
- Replace `resourcepack/assets/starfall/textures/font/starfall_logo.png` with your transparent **256 × 72 PNG** from Aseprite.
- Font mapping and display dimensions: `resourcepack/assets/minecraft/font/default.json` — `height: 30`, `ascent: 25`.
- Image is approximately **107 × 30 GUI pixels** at that height. GUI Scale changes the size in physical screen pixels.
- Rebuild and resend the resource pack after editing the image.
- Choose a different Unicode codepoint if U+E7F1 collides with another resource-pack glyph.

## Dev/build
Run `mvn clean package` with JDK 25. The GitHub Actions workflow also packages the resource pack with ZIP root containing `pack.mcmeta` and `assets/`.

Use the vanilla TAB player list (column count varies with GUI scale/player count). Do not run another player-list format/header plugin at the same time.

There is no Nexo-specific content or dependency in this plugin. The standalone `resourcepack/` is the only image asset source. You may still use Nexo as an optional delivery system for that resource pack. The project builds in GitHub Actions; it still requires live-client testing.
