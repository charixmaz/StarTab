# StarfallTab — rank I-X pixel badges and player list

The rank glyphs are **U+E101 through U+E10A**, exactly matching NovaraRanksTags.
Both the Starfall logo (U+E7F1) and all 10 user-provided transparent
11-pixel-tall Roman numeral badges ship in the **same** default.json.

**Important:** Minecraft resource locations must be lowercase. Source
`rank_I.png`/`rank_II.png`/`rank_III.png` have been included unchanged
as `rank_i.png`/`rank_ii.png`/`rank_iii.png` in
`resourcepack/assets/novara/textures/font/`.

When NovaraRanksTags is installed, StarTab resolves the **actual earned
progression rank** using `progressionRank(UUID)` and
`progressionBadge(UUID)`, not a separately assigned LuckPerms rank1..rank7
TAB prefix. Players are sorted by their rank within staff/role tiers.
`%novara_rank_badge%` renders the glyph before the name.
`%rank%` returns the Roman numeral for the footer.

The prior shipped `name-format: '%prefix%&f%player%'` is migrated on reload.
Obsolete rank1..rank10 prefixes are suppressed when the rank integration is
active; owner/admin/moderator prefixes remain. Custom name templates remain
editable. If no matching NovaraRanksTags version is available, TAB falls
back to text `[I]` until it reconnects.

## Install

1. Install the updated `NovaraRanksTags` and `StarfallTab` JARs.
2. Distribute **one merged resource pack** containing your other glyphs.
   The packaged `resourcepack/` in this repo already combines rank badges
   with StarfallTab's logo; no Nexo font definition is required.
3. If Nexo, ForcePack or another manager already sends a pack, merge the
   contents into that pack; avoid two plugins issuing competing packs.
4. Restart and reconnect with the updated pack (recalculate the URL/hash).
5. Run `/ranks` and `/stab reload`. Verify I, II and X on TAB plus chat.
   Test user and staff accounts to confirm no duplicate `[RANK I]` text.

The bitmap dimensions are their original transparent PNG dimensions:
I/V/X 10×11, II/IV/VI/IX 16×11, III/VII 22×11 and VIII 28×11.
Font height 11 and ascent 9 preserve nearest-neighbor pixel detail.

The pack does **not** replace the separate rank locked/unlocked menu icons
or custom paid/staff badge artwork already managed by your main server pack.

## Development
Java 25, Paper 26.1.2. Run `mvn clean package`.
Use the `resourcepack/` directory as the ZIP root when building a pack.
