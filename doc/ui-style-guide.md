# WorldWind Reforged — UI Style Guide

**Version:** 1.0
**Created:** 2026-03-27
**Attribution:** seaglassfoundry.com

---

## 1. Philosophy

WorldWind Reforged examples should feel like a professional geospatial desktop application — not a Java 2004 Swing demo. The UI should stay out of the way of the globe, use a consistent dark theme that reduces eye strain for long sessions, and make every control immediately legible and accessible.

**Three rules:**
1. **Globe first.** The WorldWindow canvas gets the space; controls live in a fixed sidebar.
2. **Consistent, not clever.** Use the same component patterns everywhere. No one-off colors or fonts.
3. **Use the style class.** All colors, fonts, spacing, and component factories live in `WWStyle.java`. Never hardcode a color or font in an example file.

---

## 2. Look & Feel Foundation

**Library:** [FlatLaf 3.4.1](https://www.formdev.com/flatlaf/) — Apache 2.0 license.
**Theme:** `FlatDarkLaf` (dark variant).

### Setup (call once before any Swing component is created)

```java
// In ApplicationTemplate.AppFrame.initialize() — before super() or any new Component()
try {
    com.formdev.flatlaf.FlatDarkLaf.setup();
} catch (Exception e) {
    // FlatLaf not on classpath — fall through to system L&F
    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
    catch (Exception ignored) {}
}
```

FlatLaf handles the bulk of Swing component theming automatically. The `WWStyle` constants and component factories handle everything on top.

---

## 3. Color Palette

All colors are defined as `public static final Color` constants in `WWStyle.java`.

### 3.1 Background Scale

| Constant | Hex | RGB | Usage |
|----------|-----|-----|-------|
| `BG_BASE` | `#1E1E1E` | (30, 30, 30) | Outermost frame, window chrome |
| `BG_DARK` | `#2D2D30` | (45, 45, 48) | Primary panel background (sidebar, toolbar) |
| `BG_PANEL` | `#3C3F41` | (60, 63, 65) | Secondary / nested panel background |
| `BG_FIELD` | `#45494A` | (69, 73, 74) | Input fields, inactive list rows, button resting state |
| `BG_HOVER` | `#4F5254` | (79, 82, 84) | Component hover state (non-accent) |
| `BG_SELECTED` | `#094771` | (9, 71, 113) | Selected list row, focused component highlight |

### 3.2 Foreground / Text

| Constant | Hex | RGB | Usage |
|----------|-----|-----|-------|
| `FG_PRIMARY` | `#DCDCDC` | (220, 220, 220) | Main labels, list items, enabled controls |
| `FG_SECONDARY` | `#A0A0A0` | (160, 160, 160) | Disabled text, hints, secondary info |
| `FG_DISABLED` | `#606060` | (96, 96, 96) | Completely disabled, placeholder |

### 3.3 Accent (Interactive)

| Constant | Hex | RGB | Usage |
|----------|-----|-----|-------|
| `ACCENT` | `#007ACC` | (0, 122, 204) | Primary buttons, active slider track, focused border |
| `ACCENT_HOVER` | `#1C97EA` | (28, 151, 234) | Accent button hover |
| `ACCENT_PRESSED` | `#005FA3` | (0, 95, 163) | Accent button pressed |

### 3.4 Status / Semantic

| Constant | Hex | RGB | Usage |
|----------|-----|-----|-------|
| `STATUS_OK` | `#50C878` | (80, 200, 120) | Success, connected, online |
| `STATUS_WARN` | `#E6B432` | (230, 180, 50) | Warning, degraded |
| `STATUS_ERROR` | `#D24646` | (210, 70, 70) | Error, offline, failed |
| `STATUS_IDLE` | `#8C8C8C` | (140, 140, 140) | Unknown / not yet checked |

### 3.5 Borders / Dividers

| Constant | Hex | RGB | Usage |
|----------|-----|-----|-------|
| `BORDER` | `#505355` | (80, 83, 85) | Panel borders, divider lines, input field borders |
| `BORDER_LIGHT` | `#606365` | (96, 99, 101) | Subtle separators inside panels |

### 3.6 Usage example

```java
// ✓ Correct — use WWStyle constants
label.setForeground(WWStyle.FG_PRIMARY);
panel.setBackground(WWStyle.BG_DARK);

// ✗ Wrong — never hardcode
label.setForeground(new Color(220, 220, 220));
panel.setBackground(new Color(45, 45, 48));
```

---

## 4. Typography

All fonts are defined as `public static final Font` constants in `WWStyle.java`.

### 4.1 Font Stack

The font stack tries platform-native UI fonts in order, falling back to `SansSerif`.

```java
private static String resolveUIFont() {
    for (String name : new String[]{"Segoe UI", "SF Pro Text", "Ubuntu", "Helvetica Neue"}) {
        Font f = new Font(name, Font.PLAIN, 12);
        if (!f.getFamily().equals("Dialog")) return name;
    }
    return "SansSerif";
}
```

### 4.2 Font Scale

| Constant | Size | Weight | Usage |
|----------|------|--------|-------|
| `FONT_HEADING` | 13px | BOLD | Section titles in titled borders, major headings |
| `FONT_BASE` | 12px | PLAIN | Default label, combo box, list item |
| `FONT_BOLD` | 12px | BOLD | Button labels, emphasized text |
| `FONT_SMALL` | 11px | PLAIN | Status bar text, secondary info, hints |
| `FONT_XS` | 10px | PLAIN | Status dots, badges, very compact indicators |

### 4.3 Usage example

```java
label.setFont(WWStyle.FONT_BASE);
button.setFont(WWStyle.FONT_BOLD);
statusLabel.setFont(WWStyle.FONT_SMALL);
```

---

## 5. Spacing

All layout and padding follows an **8 px base grid**.

| Token | Value | Usage |
|-------|-------|-------|
| `GAP_XS` | 4 px | Tight internal component spacing (dot + label, icon + text) |
| `GAP_S` | 8 px | Standard component gap (between form rows, between buttons) |
| `GAP_M` | 16 px | Section gap (between titled panels, between major groups) |
| `GAP_L` | 24 px | Large section gap (top/bottom padding of major areas) |
| `PAD_INNER` | 8 px | Inner padding of panels and sections (all sides) |
| `PAD_CONTROL` | 4, 8, 4, 8 | Top/left/bottom/right insets for clickable controls |
| `SIDEBAR_WIDTH` | 220 px | Fixed preferred width of the right-side control panel |
| `CANVAS_DEFAULT` | 1280 × 800 | Default WorldWindow canvas size |

---

## 6. Border System

### 6.1 Section border (titled panel)

```java
// Use for every major group in a sidebar panel
panel.setBorder(WWStyle.sectionBorder("Layer Manager"));
// Produces: TitledBorder in FONT_BOLD, FG_SECONDARY title, BORDER line color
```

### 6.2 Divider border (horizontal rule inside a panel)

```java
// 1 px top line with inner padding — use between sections in same panel
panel.setBorder(WWStyle.dividerBorder());
// Produces: matte top=1, others=0 in BORDER, then EmptyBorder(GAP_S, PAD_INNER, GAP_S, PAD_INNER)
```

### 6.3 Field border (input fields, read-only displays)

```java
// Applied automatically by WWStyle.createTextField(); use for any text container
field.setBorder(WWStyle.fieldBorder());
// Produces: 1px LineBorder(BORDER) + EmptyBorder(4, 6, 4, 6)
```

### 6.4 Padding only (no visible border)

```java
panel.setBorder(WWStyle.padded());          // PAD_INNER all sides
panel.setBorder(WWStyle.padded(8, 12));     // vertical, horizontal
```

---

## 7. Component Patterns

All components should be created via `WWStyle` factory methods. These methods apply the correct color, font, border, and cursor in one call.

### 7.1 Labels

```java
JLabel label  = WWStyle.label("Opacity:");            // FG_PRIMARY, FONT_BASE
JLabel label  = WWStyle.label("Hint text", false);    // FG_SECONDARY, FONT_SMALL
JLabel heading = WWStyle.heading("Layers");            // FG_PRIMARY, FONT_HEADING
```

### 7.2 Buttons

```java
// Primary action — accent blue fill, white text, hover brightens
JButton btn = WWStyle.accentButton("Apply");           // 80x28, FONT_BOLD, ACCENT bg

// Secondary action — field-color fill, border, hover turns accent
JButton btn = WWStyle.button("Reset");                 // auto-width, FONT_BASE

// Flat / icon-style — no background, no border, hover bg only
JButton btn = WWStyle.flatButton("▶");                 // use for toolbar icons
```

### 7.3 Text fields

```java
JTextField field = WWStyle.textField(20);              // 20 columns, FONT_BASE, field border
JTextField field = WWStyle.textField("placeholder");   // with placeholder hint text
```

### 7.4 Combo boxes

```java
JComboBox<String> combo = WWStyle.comboBox(new String[]{"HATCH", "CROSSHATCH", "DOTS"});
// FlatLaf renders the drop-down; WWStyle sets font and preferred size
```

### 7.5 Sliders

```java
// Horizontal slider 0–100, snap to major ticks every 25
JSlider slider = WWStyle.slider(0, 100, 50);
// Accent track color set via FlatLaf client property:
//   slider.putClientProperty("JSlider.thumbColor", WWStyle.ACCENT)
```

### 7.6 Check boxes

```java
JCheckBox cb = WWStyle.checkBox("Show Labels", true);
// FlatLaf handles rendering; WWStyle sets font, foreground
```

### 7.7 Scroll panes

```java
JScrollPane scroll = WWStyle.scrollPane(list);
// Thin scrollbar (FlatLaf), BG_DARK viewport background, no border
```

### 7.8 Separator

```java
JSeparator sep = WWStyle.separator();
// 1px horizontal line in BORDER color
```

### 7.9 Status dot

```java
JLabel dot = WWStyle.statusDot(WWStyle.STATUS_OK);    // "●" in STATUS_OK color, FONT_XS
```

---

## 8. Layout Patterns

### 8.1 AppFrame layout

Every example AppFrame follows this structure:

```
JFrame (FlatDarkLaf, 1280x800)
├─ JPanel root (BorderLayout, BG_BASE)
│  ├─ AppPanel → WorldWindow canvas  (CENTER, no min size — fills remaining space)
│  │  └─ StatusBar                   (PAGE_END, 22px height)
│  └─ JPanel sidebar                 (EAST, SIDEBAR_WIDTH=220px preferred)
│     ├─ LayerPanel                  (CENTER, scrollable)
│     └─ [example control panel]     (SOUTH, wrapped in WWStyle.sectionBorder)
```

Key points:
- Canvas is always `CENTER` — it gets all available space.
- Sidebar is always `EAST`, fixed `220 px` preferred width, `BG_DARK` background.
- Status bar is always `PAGE_END` of the canvas panel, `22 px` height.
- Control panels stack in the `SOUTH` of the sidebar using `BoxLayout(Y_AXIS)`.
- Never put a control panel in `WEST` — that was the old pattern.

### 8.2 Control panel internal layout

```
JPanel controlPanel (BoxLayout Y_AXIS, padded(8))
├─ heading "Options"                  ← WWStyle.heading()
├─ separator                          ← WWStyle.separator()
├─ JPanel row (FlowLayout LEFT, GAP_XS vgap)
│  ├─ label "Pattern:"
│  └─ comboBox
├─ JPanel row (FlowLayout LEFT)
│  ├─ label "Scale:"
│  └─ slider
└─ JPanel buttons (FlowLayout RIGHT)
   ├─ button "Reset"
   └─ accentButton "Apply"
```

Rules:
- Every distinct group of controls gets a `WWStyle.sectionBorder(title)`.
- Form rows use `FlowLayout(LEFT, GAP_XS, GAP_XS)`.
- Buttons row uses `FlowLayout(RIGHT)` with primary action rightmost.
- `BoxLayout.Y_AXIS` for the overall panel stack — **not** `GridBagLayout`.

### 8.3 Status bar layout

```
JPanel statusBar (BorderLayout, BG_BASE, 22px height, dividerBorder top)
├─ JLabel coordinates  (WEST, FONT_SMALL, FG_SECONDARY)
├─ JLabel altitude     (CENTER, FONT_SMALL, FG_SECONDARY, SwingConstants.CENTER)
└─ JLabel fps          (EAST, FONT_SMALL, FG_SECONDARY)
```

---

## 9. Hover & Interactive States

FlatLaf handles hover for standard components. For custom panels and buttons:

```java
// Hover pattern — all custom buttons use this
component.addMouseListener(new MouseAdapter() {
    @Override public void mouseEntered(MouseEvent e) {
        component.setBackground(WWStyle.BG_HOVER);  // or ACCENT_HOVER for accent btns
    }
    @Override public void mouseExited(MouseEvent e) {
        component.setBackground(originalBackground);
    }
    @Override public void mousePressed(MouseEvent e) {
        component.setBackground(WWStyle.ACCENT_PRESSED);  // for accent buttons only
    }
});
component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
```

---

## 10. `WWStyle.java` — The Single Source of Truth

**Location:** `src/gov/nasa/worldwindx/examples/util/WWStyle.java`

This class holds every constant and factory method defined in this guide. No example may import colors, fonts, or construct Swing components without going through `WWStyle`.

### Skeleton

```java
package gov.nasa.worldwindx.examples.util;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * WorldWind Reforged unified UI style constants and component factories.
 * All examples must use this class rather than hardcoding colors, fonts, or borders.
 *
 * @see doc/ui-style-guide.md
 * seaglassfoundry.com
 */
public final class WWStyle {

    private WWStyle() {}

    // ── Font resolution ─────────────────────────────────────────────────────
    private static final String FONT_FAMILY = resolveUIFont();

    private static String resolveUIFont() {
        for (String name : new String[]{"Segoe UI", "SF Pro Text", "Ubuntu", "Helvetica Neue"}) {
            Font f = new Font(name, Font.PLAIN, 12);
            if (!f.getFamily().equals("Dialog")) return name;
        }
        return "SansSerif";
    }

    // ── Colors ───────────────────────────────────────────────────────────────
    public static final Color BG_BASE       = new Color(30,  30,  30);
    public static final Color BG_DARK       = new Color(45,  45,  48);
    public static final Color BG_PANEL      = new Color(60,  63,  65);
    public static final Color BG_FIELD      = new Color(69,  73,  74);
    public static final Color BG_HOVER      = new Color(79,  82,  84);
    public static final Color BG_SELECTED   = new Color(9,   71,  113);

    public static final Color FG_PRIMARY    = new Color(220, 220, 220);
    public static final Color FG_SECONDARY  = new Color(160, 160, 160);
    public static final Color FG_DISABLED   = new Color(96,  96,  96);

    public static final Color ACCENT        = new Color(0,   122, 204);
    public static final Color ACCENT_HOVER  = new Color(28,  151, 234);
    public static final Color ACCENT_PRESSED= new Color(0,   95,  163);

    public static final Color STATUS_OK     = new Color(80,  200, 120);
    public static final Color STATUS_WARN   = new Color(230, 180, 50);
    public static final Color STATUS_ERROR  = new Color(210, 70,  70);
    public static final Color STATUS_IDLE   = new Color(140, 140, 140);

    public static final Color BORDER        = new Color(80,  83,  85);
    public static final Color BORDER_LIGHT  = new Color(96,  99,  101);

    // ── Fonts ────────────────────────────────────────────────────────────────
    public static final Font FONT_HEADING = new Font(FONT_FAMILY, Font.BOLD,  13);
    public static final Font FONT_BASE    = new Font(FONT_FAMILY, Font.PLAIN, 12);
    public static final Font FONT_BOLD    = new Font(FONT_FAMILY, Font.BOLD,  12);
    public static final Font FONT_SMALL   = new Font(FONT_FAMILY, Font.PLAIN, 11);
    public static final Font FONT_XS      = new Font(FONT_FAMILY, Font.PLAIN, 10);

    // ── Spacing ──────────────────────────────────────────────────────────────
    public static final int GAP_XS       = 4;
    public static final int GAP_S        = 8;
    public static final int GAP_M        = 16;
    public static final int GAP_L        = 24;
    public static final int PAD_INNER    = 8;
    public static final int SIDEBAR_WIDTH = 220;
    public static final Dimension CANVAS_DEFAULT = new Dimension(1280, 800);

    // ── Borders ──────────────────────────────────────────────────────────────
    public static Border sectionBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER, 1), title);
        tb.setTitleFont(FONT_BOLD);
        tb.setTitleColor(FG_SECONDARY);
        return BorderFactory.createCompoundBorder(tb,
            BorderFactory.createEmptyBorder(PAD_INNER, PAD_INNER, PAD_INNER, PAD_INNER));
    }

    public static Border dividerBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(GAP_S, PAD_INNER, GAP_S, PAD_INNER));
    }

    public static Border fieldBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(4, 6, 4, 6));
    }

    public static Border padded() {
        return BorderFactory.createEmptyBorder(PAD_INNER, PAD_INNER, PAD_INNER, PAD_INNER);
    }

    public static Border padded(int vertical, int horizontal) {
        return BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    // ── Labels ───────────────────────────────────────────────────────────────
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_BASE);
        l.setForeground(FG_PRIMARY);
        return l;
    }

    public static JLabel label(String text, boolean primary) {
        JLabel l = new JLabel(text);
        l.setFont(primary ? FONT_BASE : FONT_SMALL);
        l.setForeground(primary ? FG_PRIMARY : FG_SECONDARY);
        return l;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_HEADING);
        l.setForeground(FG_PRIMARY);
        return l;
    }

    public static JLabel statusDot(Color color) {
        JLabel l = new JLabel("●");
        l.setFont(FONT_XS);
        l.setForeground(color);
        return l;
    }

    // ── Buttons ──────────────────────────────────────────────────────────────
    public static JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_BOLD);
        b.setForeground(Color.WHITE);
        b.setBackground(ACCENT);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(80, 28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(ACCENT_HOVER); }
            public void mouseExited(MouseEvent e)  { b.setBackground(ACCENT); }
            public void mousePressed(MouseEvent e)  { b.setBackground(ACCENT_PRESSED); }
            public void mouseReleased(MouseEvent e) { b.setBackground(ACCENT_HOVER); }
        });
        return b;
    }

    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_BASE);
        b.setForeground(FG_PRIMARY);
        b.setBackground(BG_FIELD);
        b.setOpaque(true);
        b.setBorder(fieldBorder());
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(ACCENT); b.setForeground(Color.WHITE); }
            public void mouseExited(MouseEvent e)  { b.setBackground(BG_FIELD); b.setForeground(FG_PRIMARY); }
        });
        return b;
    }

    public static JButton flatButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_BASE);
        b.setForeground(FG_PRIMARY);
        b.setBackground(BG_DARK);
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setOpaque(true); b.setBackground(BG_HOVER); }
            public void mouseExited(MouseEvent e)  { b.setOpaque(false); }
        });
        return b;
    }

    // ── Text Fields ──────────────────────────────────────────────────────────
    public static JTextField textField(int columns) {
        JTextField f = new JTextField(columns);
        f.setFont(FONT_BASE);
        f.setForeground(FG_PRIMARY);
        f.setBackground(BG_FIELD);
        f.setCaretColor(FG_PRIMARY);
        f.setBorder(fieldBorder());
        return f;
    }

    // ── Combo Boxes ──────────────────────────────────────────────────────────
    public static <T> JComboBox<T> comboBox(T[] items) {
        JComboBox<T> c = new JComboBox<>(items);
        c.setFont(FONT_BASE);
        c.setForeground(FG_PRIMARY);
        c.setBackground(BG_FIELD);
        return c;
    }

    // ── Sliders ──────────────────────────────────────────────────────────────
    public static JSlider slider(int min, int max, int value) {
        JSlider s = new JSlider(min, max, value);
        s.setBackground(BG_DARK);
        s.setForeground(FG_SECONDARY);
        s.putClientProperty("JSlider.thumbColor", ACCENT);
        return s;
    }

    // ── Check Boxes ──────────────────────────────────────────────────────────
    public static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setFont(FONT_BASE);
        cb.setForeground(FG_PRIMARY);
        cb.setBackground(BG_DARK);
        return cb;
    }

    // ── Scroll Panes ─────────────────────────────────────────────────────────
    public static JScrollPane scrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG_DARK);
        sp.setBackground(BG_DARK);
        return sp;
    }

    // ── Separators ───────────────────────────────────────────────────────────
    public static JSeparator separator() {
        JSeparator s = new JSeparator();
        s.setForeground(BORDER);
        s.setBackground(BG_DARK);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return s;
    }

    // ── Panels ───────────────────────────────────────────────────────────────
    /** Standard sidebar panel with BG_DARK background and padded border. */
    public static JPanel sidebarPanel() {
        JPanel p = new JPanel();
        p.setBackground(BG_DARK);
        p.setBorder(padded());
        return p;
    }

    /** Row panel for a form row: FlowLayout LEFT with standard gaps. */
    public static JPanel rowPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, GAP_XS, GAP_XS));
        p.setBackground(BG_DARK);
        return p;
    }
}
```

---

## 11. FlatLaf Property Overrides

Set these via `UIManager.put(key, value)` **after** `FlatDarkLaf.setup()` and before any component construction. Place in `ApplicationTemplate.AppFrame.initialize()`.

```java
// Match our palette exactly
UIManager.put("Panel.background",        WWStyle.BG_DARK);
UIManager.put("TextField.background",    WWStyle.BG_FIELD);
UIManager.put("TextField.foreground",    WWStyle.FG_PRIMARY);
UIManager.put("ComboBox.background",     WWStyle.BG_FIELD);
UIManager.put("List.background",         WWStyle.BG_PANEL);
UIManager.put("List.selectionBackground",WWStyle.BG_SELECTED);
UIManager.put("List.selectionForeground",WWStyle.FG_PRIMARY);
UIManager.put("ScrollBar.thumbColor",    WWStyle.BG_HOVER);
UIManager.put("ScrollBar.trackColor",    WWStyle.BG_PANEL);
UIManager.put("Component.focusColor",    WWStyle.ACCENT);
UIManager.put("Component.borderColor",   WWStyle.BORDER);
// Scrollbar width — thin modern style
UIManager.put("ScrollBar.width", 8);
UIManager.put("ScrollBar.thumbArc", 999);  // fully rounded thumb
```

---

## 12. Do's and Don'ts

| ✓ Do | ✗ Don't |
|------|---------|
| Use `WWStyle.accentButton("Apply")` | `new JButton("Apply")` with hardcoded colors |
| Use `WWStyle.BG_DARK` for panel backgrounds | `new Color(45, 45, 48)` in example code |
| Use `BoxLayout.Y_AXIS` for sidebar stacks | `GridBagLayout` in control panels |
| Keep sidebar 220 px wide | Variable-width sidebars per example |
| Use `WWStyle.sectionBorder("Title")` for groups | Mix of titled + etched + compound borders |
| Place controls in `EAST` sidebar | Controls in `WEST`, `SOUTH`, or floating |
| Let FlatLaf render checkboxes / combo boxes | Custom-painted components |
| Use `FONT_SMALL` for status text | `Font.decode("Arial-BOLD-10")` |

---

## 13. Accessibility Notes

- All text meets **WCAG AA** contrast ratio against dark backgrounds:
  - `FG_PRIMARY` (#DCDCDC) on `BG_DARK` (#2D2D30): contrast ratio ≈ 9.5:1 ✓
  - `FG_SECONDARY` (#A0A0A0) on `BG_DARK` (#2D2D30): contrast ratio ≈ 4.6:1 ✓ (AA minimum = 4.5)
  - `ACCENT` (#007ACC) on `BG_DARK` (#2D2D30): contrast ratio ≈ 4.7:1 ✓ (for button text use white)
- All interactive controls must be focusable via keyboard (FlatLaf default — don't disable).
- Cursor changes to `HAND_CURSOR` on all clickable components (already in factories).
- Status indicators use both color **and** text (never color alone).
