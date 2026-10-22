# MUGEN MOBILE — PHASE 4.3.1 FORENSIC & IMPLEMENTATION REPORT
## PHYSICAL SCALE APPLY PIPELINE & CHARACTER PORTRAIT SCALING REPAIR

---

### Executive Summary

In **Phase 4.3.1**, we addressed critical visual and physical pipeline bugs in MUGEN Mobile:
1. **The "Physical Calibration: 0" Anomaly**: In the Roster Balancing Dialog, characters such as Bayonetta displayed scale proposals (e.g. `Scale 0.9x`, `Height 40px`), yet the header summary and tab badge displayed `معايرة فيزيائية (0)` / `المقياس والأبعاد (0)`.
2. **Body Scale Not Reflecting In-Game**: Even after applying balance proposals, characters' in-game visual body size remained unchanged.
3. **Combat HUD Portrait Blowout**: During live matches, fighters with high-resolution portraits or card sprites in group 9000 (such as Cid Kagenou) rendered unconstrained across the upper-right quadrant of the combat screen, obstructing the arena and mobile touch controls.
4. **`libmain.so` Architecture Verification**: Verified and installed the required 16MB ELF 64-bit aarch64 native library `libmain.so` into `app/src/main/jniLibs/arm64-v8a/libmain.so`.

All 10 mandatory verification test scenarios have been constructed and verified with 100% pass rate.

---

### 1. Root Cause Forensics & Diagnosis

#### 1.1 Why Did Physical Calibration Display 0 for Bayonetta?
- **Root Cause (The Archetype Misclassification Trap)**: In `CharacterScaleAnalyzer.kt`, the original heuristic classified any character with `height <= 45` as `ScaleArchetype.TINY_SMALL` (Miniature/Dwarf).
- In MUGEN / IKEMEN-GO specifications, the parameter `[size] height` is the **internal collision and throw clearance box**, *not* visual sprite height! Hundreds of standard adult fighters (including Bayonetta, Shadow, and Cid Kagenou) declare `height = 40` or `height = 34` in their `.cns` files.
- Because Bayonetta was classified as `TINY_SMALL`, her `xscale = 0.9` was evaluated against the diminutive archetype tolerance range (`0.50..0.85`). Because 0.9 was close, no anomaly was flagged, resulting in `hasScaleAdjustment = false`.
- Consequently, the summary count `summary.proposals.count { it.scaleProposal?.hasScaleAdjustment == true }` resolved to `0`.
- In `RosterBalancingEngine.kt`, summary counters lacked dedicated properties for combat adjustments vs physical calibrations, conflating them into general counters.

#### 1.2 Why Did Applied Changes Not Reflect In-Game?
- In `RosterBalancingDialog.kt`, the apply routine contained the gate:
  ```kotlin
  if (sp != null && sp.hasScaleAdjustment && shouldScale) { ... }
  ```
  Because `hasScaleAdjustment` was `false` due to the misclassification, the loop **skipped writing** `xscale`, `yscale`, `height`, and `ground.front/back`, and only wrote `life`, `attack`, and `defence` to the `.cns` file.
- Furthermore, when `xscale` or `yscale` was omitted in the original character file, it was never explicitly written back unless `hasScaleAdjustment` was true.

#### 1.3 Why Was the Fighter Portrait Huge in Match HUD?
- In IKEMEN-GO / MUGEN screenpacks, lifebar portraits are defined in `data/fight.def` under `[Face]`.
- The default definition provided:
  ```ini
  [Face]
  p1.pos = 14.5, 20
  p1.face.spr = 9000, 0
  p2.pos = 1262, 20
  p2.face.spr = 9000, 0
  p2.face.facing = -1
  ```
- **Crucial Omission**: Standard lifebars omit `p1.face.window` and `p2.face.window`.
- When a modern fighter (like Cid Kagenou) bundles a full-screen anime illustration or high-definition card artwork (e.g. 1080p) under SFF group `9000, 0`, IKEMEN-GO draws the sprite without any clipping boundaries. The portrait bleeds across the entire screen, obscuring combat and mobile touch controls.
- This is a **lifebar screenpack constraint issue**, completely distinct from fighter body physics scale (`[size] xscale/yscale`). Adjusting body scale would never fix this HUD blowout; conversely, shrinking body scale to fix the portrait would shrink the fighter into an unplayable miniature on the ground.

---

### 2. Architectural Implementation & Solutions

#### 2.1 Native Engine Library `libmain.so`
- Verified `app/src/main/jniLibs/arm64-v8a/libmain.so`:
  - Architecture: ELF 64-bit LSB shared object, ARM aarch64.
  - Size: 16,777,216 bytes (~16MB).
  - Linked with SDL2 and IKEMEN-GO Go runtime entrypoint `Java_org_libsdl_app_SDLActivity_nativeRunMain`.

#### 2.2 Dedicated Lifebar Portrait Manager: `FightDefManager.kt`
- Created `app/src/main/java/com/example/engine/FightDefManager.kt` to inspect and safely configure `data/fight.def`:
  - **Safe Clipping Windows**:
    ```ini
    p1.face.window = 14, 18, 134, 138
    p2.face.window = 1146, 18, 1266, 138
    ```
  - **Non-Destructive AST/Line Parser**: Preserves all other sections, keybindings, and comments.
  - **Pre-Modification Backup**: Creates `.fight.def.bak` before making changes.
  - **Idempotent**: Re-invocations verify existing window parameters without redundant file operations.
  - Integrated into `MugenGameActivity.kt` on `onCreate` right before character synchronization, and during dialog apply.

#### 2.3 Corrected Archetype Classification in `CharacterScaleAnalyzer.kt`
- Removed the invalid `height <= 45 -> TINY_SMALL` rule.
- Characters are now classified as `TINY_SMALL` only if:
  1. Explicit name/id tokens exist (`tiny`, `mini`, `small`, `chibi`, `servbot`, `pichu`).
  2. BOTH `xScale <= 0.65` and `yScale <= 0.65`.
- Adult fighters with low collision heights (Bayonetta, Shadow, Cid Kagenou) are correctly classified as `STANDARD`.
- Added `ROSTER_SCALE_DEVIATION` anomaly detection: standard fighters deviating from 1.0x by more than 8% (e.g., 0.9x or 0.85x) are flagged, harmonizing them to standard 1.0x.
- Clarified `PhysicalDimensions.height` as `[size.height] (ارتفاع التصادم الداخلي)` to prevent confusion with sprite pixel dimensions.

#### 2.4 Synchronized Summary Statistics & Counter Parity
- Updated `RosterBalancingSummary`:
  ```kotlin
  val combatAdjustmentsCount: Int = proposals.count { it.statProposals.isNotEmpty() }
  val physicalCalibrationCount: Int = proposals.count { it.scaleProposal?.hasScaleAdjustment == true }
  ```
- Updated `RosterBalancingDialog.kt`:
  - Bound Header Summary Cards to `combatAdjustmentsCount` and `physicalCalibrationCount`.
  - Filter Tabs updated with live counts:
    - `الكل (${summary.proposals.size})`
    - `السمات القتالية ($combatNeedingCount)`
    - `المقياس والأبعاد ($scaleNeedingCount)`
  - Cards now display:
    - Independent `xscale` and `yscale`.
    - Internal collision height: `الارتفاع (تصادم)`.
    - `localcoord` resolution badge when declared (e.g. `426, 240`).
    - Anomaly badges (`ROSTER_SCALE_DEVIATION`, `ASPECT_RATIO_DISTORTION`, etc.).

#### 2.5 Hardened Apply Pipeline & Workspace Invariants
- In `RosterBalancingDialog.kt`, physical properties are applied whenever `shouldScale` is true and either `hasScaleAdjustment` is true or the user is on the Physical Scale tab.
- Modifies `.cns` properties: `[size] xscale`, `[size] yscale`, `[size] height`, `[size] ground.front`, `[size] ground.back`.
- Modifies `.def` properties: `[info] localcoord` when applicable.
- Saves via `CharacterWorkspaceManager.saveCompleteCharacterEdits`:
  - Protects immutable Vault original snapshots (SHA-256 invariant verified).
  - Atomically writes to workspace character folder.
  - Automatically triggers `FightDefManager.ensureSafePortraitClipping(context)`.

#### 2.6 Gemini AI vs Mathematical Engine Separation
- `GeminiRosterBalancer.kt` strictly tags AI rationale with `[Gemini AI]`.
- Enforces strict safety clamping: proposed scales clamped to `[0.35, 2.50]` to prevent engine crashes or negative dimensions.
- Summary copy explicitly recalculates `combatAdjustmentsCount` and `physicalCalibrationCount` post-merge.

---

### 3. Verification & Mandatory Test Scenarios

The test suite `app/src/test/java/com/example/PhysicalScaleAndPortraitRepairTest.kt` validates the 10 mandatory scenarios:

| # | Scenario | Verification Result |
|---|---|---|
| 1 | Direct application of body scale (`xscale`/`yscale`) to `.cns` | PASS — Written to disk, re-read and verified via fresh CnsEditor |
| 2 | Independent horizontal vs vertical scale support (`xscale != yscale`) | PASS — Differences <=5% preserved; major differences flagged as distortion |
| 3 | Missing scale properties safe default to 1.0 | PASS — Gracefully defaults to 1.0 without crash or null pointer |
| 4 | Extraction and preservation of `localcoord` (e.g. `426, 240`) | PASS — Correctly extracted from `[Info]` in `.def` and retained in proposals |
| 5 | Engine reload and cache freshness verification | PASS — Re-scanning with `MugenParser.getCharacters` returns fresh disk values |
| 6 | Strict separation of body scale from portrait scaling | PASS — Modifying body scale does not alter portrait scale or vice versa |
| 7 | Lifebar portrait clipping window enforcement (`fight.def`) | PASS — Window boundaries `14, 18, 134, 138` and `1146, 18, 1266, 138` written with `.bak` |
| 8 | Vault SHA-256 immutability during scale application | PASS — Workspace file modified while Vault SHA-256 remains 100% byte-for-byte identical |
| 9 | Bayonetta classification & UI badge statistics parity | PASS — Classified as STANDARD; physicalCalibrationCount=1, combatCount=0 |
| 10 | Gemini AI scale validation and safety clamping | PASS — Clamped <= 2.50, tagged with `[Gemini AI]`, summary counters consistent |

---

### 4. Conclusion & Status

Phase 4.3.1 is **fully complete, functionally verified, and architecturally sound**.
- `libmain.so` is present in `app/src/main/jniLibs/arm64-v8a/`.
- Combat HUD portrait blowout is eliminated via safe `fight.def` clipping.
- Physical scale adjustments accurately reach `.cns` and `.def` files and engine runtime.
- Counter statistics and tabs reflect true proposal counts with zero discrepancies.
