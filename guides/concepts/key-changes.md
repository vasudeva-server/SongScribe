# Key Changes: Concepts

## Key

The value that a line runs in, identified by its position on the circle of fifths: negative for
flats, positive for sharps, zero for none. Every key is major, and a key is named for what it
holds, never for a tonic. Modeled by `Key`.

## Key signature

Only what is drawn from a key: the positioned box of accidentals in a staff header, and its
painting. The notator changes the key, and the signature follows. Modeled by `KeySignatureExtent`.

## Own key

The key a line establishes at its start. A line holds one only where it changes the key it would
inherit. Modeled by `Line`.

## Inherited key

The key in effect at the end of the line before, which a line without an own key runs in. The first
line always has an own key. Modeled by `KeyInheritance`.

## Running key

The key a line starts in, whether own or inherited. Modeled by `Line`.
