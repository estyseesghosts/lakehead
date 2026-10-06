# Launcher Gesture and Edit Mode Corrections

The launcher now has too many overlapping gesture handlers. In particular, tile shortcut swipes and page-navigation swipes can interfere with edit mode. Simplify the gesture model and restore edit-mode functionality.

## 1. Restrict horizontal page swiping

Horizontal swipes switch between the three launcher surfaces:

**Feed overlay ↔ Tile grid ↔ Apps list**

Change page-swipe handling as follows:

- Only begin a horizontal page swipe when the gesture starts within the **bottom 33% of the screen**.
- Swipes starting in the upper 67% must not trigger page navigation.
- **Disable horizontal page swiping completely while edit mode is active.**
- Edit-mode gestures must take precedence over page-navigation gestures.

Do not allow an already-detected tile interaction to accidentally become a page swipe.

## 2. Remove swipe-to-open app shortcuts

The tile-grid app-shortcut menu is currently opened by swiping on a tile.

Remove this gesture completely.

There should be no swipe gesture on a tile for opening app shortcuts. This conflicts with page navigation, dragging, resizing, and edit mode.

App shortcuts will instead be exposed through edit mode, as described below.

## 3. Change tile resizing controls

Resizing must no longer start from an arbitrary tile drag.

When a tile is selected in edit mode, show explicit controls.

### 1x1 tiles

Keep the existing unpin control:

- `(×)` button over the **top-left corner**.

Add a resize control:

- place it over the **bottom-right corner**;
- pressing and dragging this control starts resizing;
- resizing must continue to support the existing freeform grid resizing behaviour.

The tile itself must not need to interpret a generic swipe as a resize gesture.

### Larger tiles

Keep the existing edit-mode unpin control behaviour unless implementation constraints require repositioning it.

Add the same dedicated resize control.

Dragging the resize control must resize only the selected tile.

The resize operation must continue to obey:

- valid grid dimensions;
- collision rules;
- minimum tile dimensions;
- launcher grid boundaries;
- any existing placement constraints.

## 4. Restore tile movement in edit mode

A regression introduced after Phase 4 prevents tiles from being moved while edit mode is active.

Fix this.

While in edit mode:

- press/drag on the selected tile body → **move tile**;
- press/drag on the resize control → **resize tile**;
- press unpin control → **remove tile**;
- tap another tile → **change selection**.

Moving and resizing must remain separate interactions.

Dragging the tile itself must never accidentally trigger:

- page navigation;
- shortcut menus;
- resizing.

## 5. Show app shortcuts from edit mode

When a tile is selected in edit mode, show its available Android app shortcuts in a menu positioned **above the selected tile**.

Behaviour:

- If the selected app exposes shortcuts, show them.
- If it exposes none, do not show an empty shortcut menu.
- When selection moves to another tile, dismiss the previous menu and show the shortcuts for the newly selected tile.
- The shortcut menu must follow the current selection.
- Leaving edit mode dismisses the shortcut menu.
- Moving or resizing a tile must not accidentally activate a shortcut.

This replaces the removed swipe-to-open-shortcuts interaction.

The menu can remain visually basic for now. Correct gesture behaviour and shortcut discovery are more important than final styling.

## 6. Gesture priority

The intended interaction priority is:

1. Explicit edit controls such as unpin and resize.
2. Tile movement while in edit mode.
3. Tile selection while in edit mode.
4. Shortcut-menu interaction.
5. Normal tile interaction.
6. Horizontal page navigation.

Page navigation must therefore be the lowest-priority competing gesture and is only eligible when:

- edit mode is inactive; and
- the gesture starts within the bottom 33% of the display.

Do not solve conflicts using multiple independent gesture handlers that race against each other. Gesture ownership should be decided deliberately once sufficient movement or intent is known.

## 7. Replace the feed placeholder

The left-side Launcher3 feed-overlay page is currently only an empty placeholder.

Replace it with the real feed integration.

The launcher must support the Launcher3-style feed-provider mechanism so compatible providers can render their actual feed surface. Our intended targets include:

- **Lawnfeed / Google Discover integration**
- **Neo Feed**

The page must therefore stop behaving like an internal fake feed screen and instead act as the launcher-side host for compatible feed-overlay providers.

The final horizontal page structure remains:

**Feed provider ↔ Tile grid ↔ Apps list**

Feed integration should remain isolated from the tile-grid gesture implementation so feed-provider behaviour does not create another competing gesture path inside the grid.

## Required end state

After these changes:

- edit mode can reliably select, move, resize, and unpin tiles;
- app shortcuts are accessed through the selected tile in edit mode;
- tile shortcut swipes no longer exist;
- page swipes work only from the bottom third of the screen;
- page swipes are disabled during edit mode;
- resize has a dedicated drag handle;
- tile movement works again;
- gesture handlers no longer compete unpredictably;
- the feed page hosts actual compatible feed providers instead of a placeholder.
