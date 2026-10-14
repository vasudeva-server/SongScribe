# Lyrics Concepts

## Script

A writing system in which a song's lyrics are written. A song holds the same lyrics in several
scripts and shows exactly one, the active script, at a time; scripts are languages, not stanzas.
It is not the note-attached script of [line layout](line-layout.md). Modeled by `Song.Script`.

## Syllabic chain

The run of syllables that make up one word, joined by hyphens. Modeled by `Lyric.Syllabic`.

## Melisma chain

A start followed by text-less carriers and a close, which draws the extender under a syllable
sung across several notes. Modeled by `Lyric.Extend`.

## Bearing syllable text

Being a place where a syllable can be sung. Only a note or a grace note bears one, and a note
with a paired grace note in front of it does not, because that syllable is the grace note's.
Modeled by `ElementType`.

## Carrying a chain onward

Letting a word or a melisma run past an element. Every element except a repeat carries a chain,
so a rest, a barline, a breath mark or a key change carries one without taking a syllable.
Modeled by `ElementType`.
