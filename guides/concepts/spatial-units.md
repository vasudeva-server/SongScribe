# Spatial Units

## Staff space

The distance between two adjacent staff lines. It is the zoom- and device-independent layout
unit, and the unit the document model is defined in. Modeled by `Ss`.

## Document pixel

A pixel at the fixed document scale, which no zoom changes. Modeled by `DocPx`.

## View pixel

An on-screen pixel at the view's current zoom. Modeled by `ViewPx`.

## Staff position

A discrete step of the pitch grid, in half staff spaces from the middle staff line. Modeled by
`StaffPosition`.

## Pixel regime

Which of the two pixel units, document or view, a pixel value is in. The two differ by the view's
zoom. Modeled by `PixelDistance`, which `DocPx` and `ViewPx` implement.
