## Custom floating tab bar specification

Treat this as a **floating navigation capsule**, not a standard bottom navigation bar.

- The root container is a wide, translucent or dark **pill-shaped surface** floating above the content.
- It must not touch screen edges.
- Content can remain visible behind and around it.
- The pill has a fixed height and a large corner radius, ideally `height / 2`, so the ends are true semicircles.

### Tab geometry

Each tab has an equal interaction region. The visual icon is centered inside that region.

The selected tab adds a **filled circular selection surface** behind the icon:

```text
[      ○       ○       ●       ○      ]
                   selected
```

The selection circle is independent from the pill. It should not stretch into an oval or use a tab-shaped background.

The icon remains centered in the selection circle.

### Critical edge-tab rule

For the first and last tabs, preserve geometric concentricity between the selection circle and the rounded end of the parent pill.

Conceptually:

```text
LEFT EDGE

      outer pill radius
           ↓
     ┌────────────
   ╭─┤
  │  │   ●
   ╰─┤
     └────────────
        ↑
 selection circle
```

More precisely, if:

- pill height = `H`
- outer radius = `H / 2`
- selection circle diameter = `S`

then the center of the first selected circle should be positioned so that its center shares the same horizontal centerline as the pill's left semicircular cap.

The same applies symmetrically to the rightmost tab.

This means the end padding should derive from geometry, not from an arbitrary padding constant:

```text
edge tab center X = pill outer radius
```

or, if the design includes a defined inset:

```text
edge tab center X = pill outer radius + deliberate inset
```

Use the exact same inset at both ends.

Do **not** position four evenly spaced button centers first and then simply place a circle behind them if that causes the edge circles to look offset from the pill curvature.

### Radii relationship

The important visual relationship is:

```text
pill outer radius
    concentric with
edge selection-circle radius
```

The two circles do not need the same radius. They need the same **center point**.

For example:

```text
Pill height:              96 dp
Pill end radius:          48 dp
Selection diameter:       72 dp
Selection radius:         36 dp
```

At the edge:

```text
outer cap center = selection circle center
```

This creates nested/concentric curves:

```text
     ____________
   ╭──────────────
  (    ( ● )       ...
   ╰──────────────
```

rather than:

```text
     ____________
   ╭──────────────
  (       ( ● )    ...
   ╰──────────────
          ^
      too far inward
```

### Component structure

I would implement it as custom components similar to:

```text
FloatingTabBar
 ├─ FloatingTab
 │   ├─ SelectionCircle
 │   └─ Icon
 ├─ FloatingTab
 ├─ FloatingTab
 └─ FloatingTab
```

`FloatingTabBar` should own:

- pill geometry
- tab-center calculations
- edge spacing
- selected-index state
- selection animation

`FloatingTab` should own:

- interaction
- icon rendering
- pressed state
- accessibility semantics

The selection circle should preferably be rendered by the parent rather than separately inside every tab. This makes animated movement and precise geometry easier.

### Selection animation

When changing tabs, animate the circle between tab centers rather than replacing one background with another.

The effect should be:

1. User selects another tab.
2. Existing selection circle moves horizontally toward the destination.
3. Icon state transitions at the appropriate point.
4. Circle settles exactly on the destination tab center.

You can optionally animate:

- circle scale slightly during movement;
- selected icon scale;
- icon opacity;
- pill tint or blur.

But the circle's geometry should remain stable.

### Layering

Use this order:

```text
foreground
    icons
    selected icon
    selection circle
    pill surface
    underlying launcher/content
background
```

The selection circle must clearly sit **inside/on top of the pill**, not appear to cut a hole through it.

### Interaction regions

The visual circles should not define touch targets.

Each tab should retain a sufficiently large invisible hit region:

```text
┌───────────────┐
│   hit area    │
│      ●        │
│     icon      │
└───────────────┘
```

This permits compact visual spacing without making the navigation difficult to use.

### Main implementation constraint

Do not model this as:

> `NavigationBar` + four standard `NavigationBarItem`s.

Model it as a **single custom geometric component containing explicitly positioned tab centers and one movable circular selection indicator**.

That gives precise control over the property that matters most here: when the first or last item is selected, the selection circle and the capsule end must form clean, concentric nested curves rather than looking like a circle placed approximately near the edge.
