---
name: gildam-architecture
description: Architectural rules, local-only photo privacy policy, and design guidelines for Gildam (길담 - 길이야기) Android app. Based on https://github.com/android/skills open SKILL.md format.
---
# Gildam (길담) Android Architecture & Privacy Skill

When extending or modifying the Gildam Android project in Android Studio (via Gemini in Android Studio, Claude Code, Cursor, or Android CLI), strictly enforce the following rules:

## 1. Module Dependency Boundaries
- `:app` -> Entry point, Hilt DI graph assembly, `MainActivity` (Edge-to-Edge), Manifest declarations.
- `:ui` -> Jetpack Compose screens, ViewModels, Type-Safe Navigation routes, M3 Warm Minimal theme (`#2E4F3E` Forest Green, `#FAF7F2` Warm Ivory, `#D95D39` Terracotta Route, `#101211` Dark OLED). Tabular numerals (`fontFeatureSettings = "tnum"`) MUST be used on all live counters.
- `:recording` -> `LocationTrackingService` (`foregroundServiceType="location"`), Haversine distance calculator, adaptive drift filter, ongoing notification with Pause/Resume/Add Memo actions.
- `:data` -> Single Source of Truth (SSOT). Room Database with WAL enabled, Local WebP thumbnail generator (`filesDir/thumbnails`), MediaStore URI validator, and Whitelist GPX 1.1 Exporter.
- `:sync` -> `GildamSyncWorker` (`CoroutineWorker`), Retrofit API with Gzip compression for track points, idempotent PUT upserts, and conflict resolution.
- `:auth` -> NUNI ID (`https://id.nuni.co.kr`) OAuth 2.0 + PKCE (`S256`) via Chrome Custom Tabs and Android Keystore encrypted token storage.
- `:map` -> MapLibre Native SDK Compose wrapper, GeoJSON polyline rendering, and waypoint symbol layers.

## 2. Hard Privacy & Photo Rules (NEVER VIOLATE)
1. **Never Upload Photos or Thumbnails**: Photo binaries, MediaStore URIs, and local thumbnails MUST NEVER be sent to the OVH server or included in cloud sync payloads.
2. **MediaStore Reference Only**: Store only the `content://media/...` URI in local Room `photos` table. Never copy full-resolution originals into app storage.
3. **Graceful Broken URI Fallback**: If a user deletes a photo in the system gallery (`UriStatus.MISSING_IN_GALLERY`) or revokes permission (`UriStatus.PERMISSION_REVOKED`), preserve the waypoint memo text 100% and display the recovery UI.
4. **Cross-Device Placeholder**: When `waypoint.hasPhoto == true` and `waypoint.photoLocalOnly == true` but no local `PhotoEntity` exists on the current device, display `"사진은 촬영한 기기(${capturedDeviceName})에만 있어요"`.
5. **Safe Public Export & Privacy Zone Clipping**: Default visibility is `PRIVATE`. When exporting public GPX, strip photos/captions/absolute timestamps by default and apply start/end radius clipping (`privacyRadiusMeters`) to protect the user's home location.

## 3. Official Android Skills Alignment (`github.com/android/skills`)
- **Edge-to-Edge (Target SDK 35)**: Always call `enableEdgeToEdge()` in `MainActivity` and consume `WindowInsets` (`systemBars`, `navigationBars`, `ime`) in Compose Scaffolds and floating dashboards.
- **Type-Safe Navigation**: Use `@Serializable` route objects/data classes with `androidx.navigation.compose`.
- **R8 & ProGuard**: Maintain explicit `@Serializable` annotations and module `consumer-rules.pro` for Retrofit/Kotlinx Serialization DTOs.
