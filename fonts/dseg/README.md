# DSEG (subset)

Bundled for ChargeClock themes Classic Digital / 14SEG Digital.
Source: https://github.com/keshikan/DSEG (SIL OFL 1.1) — see DSEG-LICENSE.txt

- `dseg7_classic_mini_bold_italic_file.ttf` — subset of DSEG7 Classic Mini Bold Italic
- `dseg14_classic_mini_italic_file.ttf` — subset of DSEG14 Classic Mini Italic

Runtime packaging (`app/src/main/res/font/`):
- `*_file.ttf` — the font binary resources (license cannot sit in that folder)
- `dseg7_classic_mini_bold_italic.xml` / `dseg14_classic_mini_italic.xml` — font-family
  XML that aliases all style/weight combos to the matching `*_file` TTF so widget
  RemoteViews never fall back to system sans. Layouts reference these family names.
