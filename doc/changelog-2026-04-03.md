# Changelog — 2026-04-03

All changes by seaglassfoundry.com — WorldWind Reforged project.

## WMS Explorer UI Improvements

### Server selection highlight
- **File:** `WMSExplorer.java`
- Added `BG_SELECTED` and `BG_HOVER` theme colors for interactive feedback.
- Preset server buttons now stay highlighted (blue background + accent border) when
  the corresponding WMS server is active.  Previously, the only visual cue was a
  momentary hover color that disappeared when the mouse moved to the layer list.
- Layer checkboxes gain a blue background tint and a left accent bar when checked,
  plus a subtle hover highlight when unselected.

### Active-layer auto-refresh toggle
- **File:** `WMSExplorer.java`
- Every active layer now shows an **"Auto 5m"** button that toggles periodic tile
  refresh (every 5 minutes).  The button turns green when active.
- Layers whose WMS capabilities advertise `current="true"` on a time dimension
  display a green **LIVE** badge and `[LIVE]` tag in the browse list; auto-refresh
  is enabled automatically for these layers.
- A status label below the opacity slider shows the last-refreshed time.
- The toggle delegates to `WMSTiledImageLayer.setAutoRefresh()` (see below), so
  refresh continues even if the Explorer panel is closed.

## WMSTiledImageLayer — built-in auto-refresh

### New public API
- **File:** `WMSTiledImageLayer.java`
- `isLiveData()` — returns `true` if the layer was constructed from WMS capabilities
  that advertised `current="true"` on a time dimension.
- `setAutoRefresh(boolean)` / `getAutoRefresh()` — start or stop a periodic timer
  that expires all cached tiles and fires a property-change event to trigger a globe
  redraw.
- `setAutoRefreshInterval(long ms)` / `getAutoRefreshInterval()` — configurable
  interval (default 5 minutes, minimum 30 seconds).

### Live-data detection at construction
- The `WMSTiledImageLayer(WMSCapabilities, AVList)` constructor now inspects the
  layer's dimensions for `current="true"`.  When detected, `setAutoRefresh(true)` is
  called automatically.

### Lifecycle management
- A shared static daemon `ScheduledExecutorService` (`WMS-AutoRefresh` thread) is
  used across all auto-refreshing layers — no per-layer threads, and the JVM can
  exit cleanly.
- `setEnabled(false)` pauses the refresh timer; `setEnabled(true)` resumes it.

## ExamplesIndex — clean shutdown

- **File:** `ExamplesIndex.java`
- Changed `setDefaultCloseOperation` from `DISPOSE_ON_CLOSE` to `EXIT_ON_CLOSE` so
  that closing the launcher terminates the JVM.  Child examples still use
  `DISPOSE_ON_CLOSE` to avoid killing the launcher when they are closed individually.

## Texture rendering — sharp magnification

- **File:** `TextureTile.java`
- Changed the texture magnification filter from `GL_LINEAR` to `GL_NEAREST`.  This
  produces crisp, pixel-accurate edges on tiled image layers (e.g., radar data)
  when zoomed in, instead of the blurred interpolation previously applied.
- Minification filters (`GL_LINEAR_MIPMAP_LINEAR`, anisotropic filtering) are
  unchanged to preserve smooth appearance at distance.
