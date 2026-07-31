# Tuplets in the ABC corpus that fall outside the convention

Convention under test:

> `V = S/N`, N taken from the printed number. M = the largest conventional regular span strictly below N; if none exists, the smallest conventional span above N, but only for compound-beat duplets.

Conventional regular spans are binary divisions of the beat (of the beat's third, for a compound beat) together with binary multiples of the beat. Durations are in PPQ 96 ticks: quarter = 96, 8th = 48, 16th = 24, 32nd = 12; a trailing `.` means dotted.

## Summary

| | count | share |
|---|---|---|
| tuplets analysed | 7500 | |
| **A.** `V = S/N` not a note value | 44 | 0.59% |
| **B.** no conventional M | 83 | 1.11% |
| conform | 7373 | 98.31% |

### B, broken down

| N | V | beat | count | reason |
|---|---|---|---|---|
| 3 | 8th | dotted quarter | 35 | no conventional span below N (spans start at 3) |
| 2 | quarter | quarter | 28 | no conventional span below N (spans start at 2) |
| 2 | 8th | quarter | 13 | no conventional span below N (spans start at 2) |
| 3 | quarter | dotted quarter | 4 | no conventional span below N (spans start at 3) |
| 2 | 16th. | quarter | 1 | no conventional span for this V under this beat |
| 2 | 8th. | quarter | 1 | no conventional span for this V under this beat |
| 3 | 8th. | quarter | 1 | no conventional span for this V under this beat |

### Where the file states an M and the convention differs

97 of the 897 tuplets that state an M explicitly.

| N | file M | convention M | V | beat | count |
|---|---|---|---|---|---|
| 3 | 6 | 2 | 8th | quarter | 22 |
| 3 | 5 | 2 | 8th | quarter | 17 |
| 3 | 4 | 2 | 8th | quarter | 11 |
| 3 | 4 | 2 | quarter | quarter | 8 |
| 3 | 3 | 2 | 8th | quarter | 7 |
| 3 | 6 | 2 | quarter | quarter | 5 |
| 6 | 5 | 4 | 16th | quarter | 5 |
| 5 | 2 | 4 | 8th | quarter | 4 |
| 4 | 2 | 3 | 8th | dotted quarter | 4 |
| 3 | 5 | 2 | quarter | quarter | 3 |
| 3 | 3 | 2 | 16th | quarter | 3 |
| 5 | 6 | 4 | 16th | quarter | 1 |
| 6 | 5 | 4 | 8th | quarter | 1 |
| 3 | 5 | 2 | 8th | 8th | 1 |
| 2 | 4 | 3 | 8th | dotted quarter | 1 |
| 2 | 2 | 3 | 8th | dotted quarter | 1 |
| 3 | 3 | 2 | quarter | quarter | 1 |
| 2 | 2 | 3 | quarter | dotted quarter | 1 |
| 4 | 3 | 2 | 8th | half | 1 |

## A. `V = S/N` is not a note value (44)

These cannot be represented at all: the written durations do not divide into N equal notatable units.

### N=3, durations quarter 8th half -> V = 336/3 = 112 ticks (6 occurrences)

- `8000/7134.abc:16` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = quarter 8th half (96+48+192 = 336t) &nbsp; beat = quarter
  ```
  ((3:2:2D2E)F2F2F4F2F6((3E2F)G4G2G4G2G6|
  ```
- `1000/726.abc:13` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = quarter 8th half (96+48+192 = 336t) &nbsp; beat = quarter
  ```
  ((3G2G)B4B4{/B}dd!~(!d2!~)!A|
  ```
- `22000/21059.abc:20` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = quarter 8th half (96+48+192 = 336t) &nbsp; beat = quarter
  ```
  ((3DDB,) ((3DDE) ((3F F2) ((3G F2) F2 ((3A2 G) F4 ((3A A2)((3B2 A) G4 G2 ((3A2 G) F4
  ```
- `22000/21059.abc:20` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = quarter 8th half (96+48+192 = 336t) &nbsp; beat = quarter
  ```
  ((3DDB,) ((3DDE) ((3F F2) ((3G F2) F2 ((3A2 G) F4 ((3A A2)((3B2 A) G4 G2 ((3A2 G) F4
  ```
- `22000/21059.abc:20` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = quarter 8th half (96+48+192 = 336t) &nbsp; beat = quarter
  ```
  ((3DDB,) ((3DDE) ((3F F2) ((3G F2) F2 ((3A2 G) F4 ((3A A2)((3B2 A) G4 G2 ((3A2 G) F4
  ```
- `17000/16617.abc:11` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = quarter 8th half (96+48+192 = 336t) &nbsp; beat = quarter
  ```
  DEF2>>F2F4FA- A>GG4F2((3F2E)E4((3D/E/F/)GEF4|
  ```

### N=3, durations quarter quarter -> V = 192/3 = 64 ticks (5 occurrences)

- `10000/9543.abc:12` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter quarter (96+96 = 192t) &nbsp; beat = quarter
  ```
  {/A}(3:2:2d2B (3:2:2d2c2 d=c d2|
  ```
- `14000/13357.abc:25` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter quarter (96+96 = 192t) &nbsp; beat = quarter
  ```
  DD_c4c2c2c8((3:2:2c2d2)e8d2_c2B8|
  ```
- `6000/5751.abc:17` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter quarter (96+96 = 192t) &nbsp; beat = dotted quarter
  ```
  {/A}c2cc2c BcB/A/G2G EFF ((3:2:2F2D2)C6:|
  ```
- `16000/15750.abc:18` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter quarter (96+96 = 192t) &nbsp; beat = quarter
  ```
  ((3:2:2e2d) ((3:2:2e2d2) ((3:2:2e2d) ((3edc)-c2|
  ```
- `17000/16873.abc:17` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter quarter (96+96 = 192t) &nbsp; beat = dotted quarter
  ```
  c3((3:2:2 B2B2) A3G3E2DE2DC6|
  ```

### N=3, durations 16th 16th 8th -> V = 96/3 = 32 ticks (5 occurrences)

- `14000/13720.abc:14` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 16th 16th 8th (24+24+48 = 96t) &nbsp; beat = quarter
  ```
  DF2!~(!A!~)!=c2d3((3d/e/d)=c2d6:|
  ```
- `19000/18109.abc:17` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 16th 16th 8th (24+24+48 = 96t) &nbsp; beat = quarter
  ```
  _c4!-(!B2!-)!G2A2A2A2__B2(3B/c/__BA-A6=C3C!>!D4
  ```
- `22000/21509.abc:14` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 16th 16th 8th (24+24+48 = 96t) &nbsp; beat = quarter
  ```
  z G {/!~(!G}y!~)!B2 B>B {/!~(!B}y!~)!c4 Bc3 A2 ((3A/B/A) G3 G!~(!A!~)! E2 EF ((3GAF) G6:|
  ```
- `23000/22243.abc:14` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 16th 16th 8th (24+24+48 = 96t) &nbsp; beat = quarter
  ```
  G!~(!G!~)! d>dd2d<d- dd {/d}e>B cB/A/ BA/G/ (3(A/__B/A/)(3(__B/A/__B)A6:|
  ```
- `23000/22462.abc:12` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 16th 16th 8th (24+24+48 = 96t) &nbsp; beat = quarter
  ```
  zD/E/F2-(3(FFF)F3(3(E/F/G)A6G2F2E6|
  ```

### N=3, durations 8th. 16th -> V = 96/3 = 32 ticks (4 occurrences)

- `21000/20014.abc:17` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = 8th. 16th (72+24 = 96t) &nbsp; beat = quarter
  ```
  E4 (3:2:2(E>F)F-F6 {/!~(!F}y!~)!G G2 G {/!~(!G}y!~)!A2 A6
  ```
- `2000/1248.abc:17` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = 8th. 16th (72+24 = 96t) &nbsp; beat = quarter
  ```
  !~(!G!~)!B2 ((3:2:2_c>B) A ((3G/A/B/) c/A/ {/A}B4y:|
  ```
- `12000/11658.abc:15` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = 8th. 16th (72+24 = 96t) &nbsp; beat = 8th
  ```
  =G2G/G/ !~(!G/!~)!!~(!A/!~)!!~(!=G/!~)!!~(!F/!~)! ((3:2:2E>=G)A2-A/=G/A3 A/{/!~(!A}y!~)!B/-!~(!B2!~)!_GE FF {/!~(!F}y!~)!!~(!G4!~)!!~(!F!~)!Ez2:|
  ```
- `13000/12350.abc:13` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = 8th. 16th (72+24 = 96t) &nbsp; beat = quarter
  ```
  =A {/A} c (3:2:2(c>c) !~(!c3!~)! B z B/c/ {/Bc} =d>c B6 z A2!~(!B3!~)!G !~(!A2!~)! !breath!F2 G/A/G/A/ G/A/G/A/ G4:|
  ```

### N=3, durations 8th 8th 16th -> V = 120/3 = 40 ticks (3 occurrences)

- `21000/20383.abc:34` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 16th (48+48+24 = 120t) &nbsp; beat = quarter
  ```
  |:(3:2:2A2A (3AAA/B/ A4 A/c/B B((3B/c/B/) A2 |
  ```
- `2000/1735.abc:17` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 16th (48+48+24 = 120t) &nbsp; beat = quarter
  ```
  {/B}d2(3ddc/d/!~(!e4!~)!c(c2B)B4|
  ```
- `19000/18050.abc:11` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 16th (48+48+24 = 120t) &nbsp; beat = quarter
  ```
  G4G2 (3(GGG/2A/2)G4 F2F/G/F/E/D2!~(!D!~)!E !~(!D!~)!E3E!~(!F2!~)!!~(!D!~)!EE4:|
  ```

### N=3, durations 8th quarter 8th -> V = 192/3 = 64 ticks (2 occurrences)

- `10000/9462.abc:15` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th quarter 8th (48+96+48 = 192t) &nbsp; beat = quarter
  ```
  ((3Gcc) ((3cd2)-dd{/d}e8|
  ```
- `7000/6159.abc:13` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th quarter 8th (48+96+48 = 192t) &nbsp; beat = quarter
  ```
  Ac3c((3:2:2c2B) ((3cc2) {/c}ed-d4-dd {/d}!-d!e8"^fine"|]
  ```

### N=3, durations 8th quarter quarter -> V = 240/3 = 80 ticks (2 occurrences)

- `2000/1498.abc:20` &nbsp; `(3:2:` &nbsp; N=3 &nbsp; durations = 8th quarter quarter (48+96+96 = 240t) &nbsp; beat = quarter
  ```
  ((3:2:@DE2)G2-G/G/G-G4G>FG3F4E2-E<D-D6|
  ```
- `22000/21059.abc:20` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th quarter quarter (48+96+96 = 240t) &nbsp; beat = quarter
  ```
  ((3DDB,) ((3DDE) ((3F F2) ((3G F2) F2 ((3A2 G) F4 ((3A A2)((3B2 A) G4 G2 ((3A2 G) F4
  ```

### N=3, durations quarter. 8th -> V = 192/3 = 64 ticks (2 occurrences)

- `18000/17786.abc:11` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter. 8th (144+48 = 192t) &nbsp; beat = quarter
  ```
  AA AA GG GG E2 ((3:2:2B3A)G4|
  ```
- `13000/12803.abc:13` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter. 8th (144+48 = 192t) &nbsp; beat = quarter
  ```
  (3:2:2D3D DDE4 DE AGF4 E2E2 D2D2 =CD FED4
  ```

### N=3, durations 16th 16th 16th 16th -> V = 96/3 = 32 ticks (2 occurrences)

- `12000/11528.abc:13` &nbsp; `(3:4:4` &nbsp; N=3 &nbsp; durations = 16th 16th 16th 16th (24+24+24+24 = 96t) &nbsp; beat = quarter
  ```
  =GG2G (3:4:4G/A/G/F/E A2A=GA2 AB A/_G/G E2=G3A6:|]
  ```
- `17000/16701.abc:13` &nbsp; `(3:4:4` &nbsp; N=3 &nbsp; durations = 16th 16th 16th 16th (24+24+24+24 = 96t) &nbsp; beat = quarter
  ```
  {/G}B>B B>B-B2zA/B/ !~(!d2!~)!c>cc2z B>B A2 ((3:4:4G/A/B/c/A)B4|
  ```

### N=5, durations 16th 16th 16th 8th 8th -> V = 168/5 = 33.6 ticks (2 occurrences)

- `19000/18218.abc:11` &nbsp; `(5` &nbsp; N=5 &nbsp; durations = 16th 16th 16th 8th 8th (24+24+24+48+48 = 168t) &nbsp; beat = quarter
  ```
  zD {/!~(!D}y!~)!F>F (F2F)E/F/ !~(!A2!~)!G4 F<F E<E (5D/E/F/GE F3
  ```
- `19000/18218.abc:13` &nbsp; `(5` &nbsp; N=5 &nbsp; durations = 16th 16th 16th 8th 8th (24+24+24+48+48 = 168t) &nbsp; beat = quarter
  ```
  F A>A (A2A)G/A/ B>GG4 F<F E<E (5D/E/F/GE F3
  ```

### N=3, durations quarter 8th quarter -> V = 240/3 = 80 ticks (1 occurrence)

- `14000/13759.abc:17` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = quarter 8th quarter (96+48+96 = 240t) &nbsp; beat = quarter
  ```
  ((3cd/)ee2ee3d//e//f3e2dd2d2|
  ```

### N=3, durations half quarter whole -> V = 672/3 = 224 ticks (1 occurrence)

- `20000/19283.abc:11` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = half quarter whole (192+96+384 = 672t) &nbsp; beat = quarter
  ```
  c4B4A4G4G4 (3(A4B2) c8
  ```

### N=3, durations 8th quarter. -> V = 192/3 = 64 ticks (1 occurrence)

- `7000/6154.abc:13` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = 8th quarter. (48+144 = 192t) &nbsp; beat = quarter
  ```
  G3D GA3{/A}c2((3:2:2cc3) {/c}(d2c2)c8"^fine"y|]
  ```

### N=3, durations 16th 16th 32nd -> V = 60/3 = 20 ticks (1 occurrence)

- `18000/17779.abc:12` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 16th 16th 32nd (24+24+12 = 60t) &nbsp; beat = quarter
  ```
  EE2E D4 DE FE ((3E/F/E//)D-D6:|
  ```

### N=3, durations 16th 16th -> V = 48/3 = 16 ticks (1 occurrence)

- `19000/18657.abc:14` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = 16th 16th (24+24 = 48t) &nbsp; beat = quarter
  ```
  EE2EG4=AA2cB4 =A3AA2A2FG =Ac (3:2:2(c/2d/2c/2)B-B6 :|
  ```

### N=3, durations 16th 8th. -> V = 96/3 = 32 ticks (1 occurrence)

- `12000/11740.abc:12` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = 16th 8th. (24+72 = 96t) &nbsp; beat = quarter
  ```
  DE EE {/!~(!E}y!~)!G2 {/!~(!G}y!~)!E !~(!E2!~)!DD.D DEF AB!~(!B!~)! G/A/ ((3G/A/B/) ((3:2:2c<A)B3
  ```

### N=3, durations half quarter 8th -> V = 336/3 = 112 ticks (1 occurrence)

- `13000/12389.abc:14` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = half quarter 8th (192+96+48 = 336t) &nbsp; beat = quarter
  ```
  c2!~(!d3!~)!B!~(!c3!~)!=AB2-B/c/B/c/((3B4B2c) dcB2c dc!breath!B6|
  ```

### N=3, durations 8th -> V = 48/3 = 16 ticks (1 occurrence)

- `23000/22880.abc:17` &nbsp; `(3:2:1` &nbsp; N=3 &nbsp; durations = 8th (48 = 48t) &nbsp; beat = quarter
  ```
  {/D}A!~(!A!~)! G>F (3(FG"<\u266e "F) (3(G"<\u266e "FG) ((3:2:1"<\u266e "FG2)-G4| )
  ```

### N=6, durations 16th 16th 16th 16th 16th 8th -> V = 168/6 = 28 ticks (1 occurrence)

- `15000/14599.abc:11` &nbsp; `(6` &nbsp; N=6 &nbsp; durations = 16th 16th 16th 16th 16th 8th (24+24+24+24+24+48 = 168t) &nbsp; beat = quarter
  ```
  {/A}cc- (3:2:2c2B A/B/4c/4d c<d-d2z (3d/e/d/e- ed/e/ (6d/e/d/e/d/e d/e/ d_c- !~(!cy!~)!e- !-d!e yz
  ```

### N=7, durations 16th 16th 16th 16th 16th 8th half -> V = 360/7 = 51.4286 ticks (1 occurrence)

- `12000/11584.abc:17` &nbsp; `(7` &nbsp; N=7 &nbsp; durations = 16th 16th 16th 16th 16th 8th half (24+24+24+24+24+48+192 = 360t) &nbsp; beat = quarter
  ```
  BB !~(!B2!~)! A4 ((7G/A/B/c/B/A) !~(!B4!~)! ((7G/A/B/c/B/A) B8:|
  ```

### N=7, durations 16th 16th 16th 16th 16th 8th whole -> V = 552/7 = 78.8571 ticks (1 occurrence)

- `12000/11584.abc:17` &nbsp; `(7` &nbsp; N=7 &nbsp; durations = 16th 16th 16th 16th 16th 8th whole (24+24+24+24+24+48+384 = 552t) &nbsp; beat = quarter
  ```
  BB !~(!B2!~)! A4 ((7G/A/B/c/B/A) !~(!B4!~)! ((7G/A/B/c/B/A) B8:|
  ```


## C. Three 8th-units under a dotted-quarter beat (35)

The tuplet's unit equals the beat's third, so N=3 spans exactly one beat and no conventional span sits below it.

- `9000/8402.abc:26` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  AB2((3Bd_c)B8:|
  ```
- `10000/9433.abc:17` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  =c2c c2c cz d2((3ddd) d2|
  ```
- `10000/9745.abc:18` &nbsp; `(3:` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  AF B=cd8 =cde6 ((3:=cde) _f8 e=cd4 =cBA6 FGA8|
  ```
- `10000/9283.abc:14` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter 8th (96+48 = 144t) &nbsp; beat = dotted quarter
  ```
  (3:2:2D2E G>GG2(3:2:2E2G A>AA2ed_c6|
  ```
- `10000/9283.abc:14` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter 8th (96+48 = 144t) &nbsp; beat = dotted quarter
  ```
  (3:2:2D2E G>GG2(3:2:2E2G A>AA2ed_c6|
  ```
- `10000/9283.abc:18` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter 8th (96+48 = 144t) &nbsp; beat = dotted quarter
  ```
  (3:2:2D2E G>GG3(E/G/A2) AA2ed_c8:|
  ```
- `10000/9202.abc:16` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  ABd8-d2>>d2d2>>d2d8DD2((3CDE)_F2ED6|
  ```
- `14000/13840.abc:20` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  |:((3CDE)F6((3DEF)G6AF EGF8:|
  ```
- `14000/13840.abc:20` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  |:((3CDE)F6((3DEF)G6AF EGF8:|
  ```
- `14000/13121.abc:26` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  ((3BAG)F4E/D/E/F/G6B,2C2E2D2C8|]
  ```
- `21000/20509.abc:15` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  BBB2B2!-(!c2!-)!AA AAA2B2F2F2F2F2DE FA (3ABAG4:|
  ```
- `21000/20728.abc:17` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  Ad ddd4BB BBB4 AA2A A4 ((3GAB)c6B8:|
  ```
- `11000/10384.abc:13` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  dd/e/ dc AB GA ((3BAG)F3|
  ```
- `6000/5126.abc:40` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  ((3A2A2A2) ((3G2G2G2) ((3F2F2F2) ((3E2E2E2)-E6{/!~(!E}y!~)!c6 dcd6((3ced)c6
  ```
- `6000/5470.abc:23` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  E2E2{/D}A4GF-F4EE4FED4((3CDE)G2F8|]
  ```
- `6000/5458.abc:26` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  GA Bdc4((3cde)_f4gfed cd6|]
  ```
- `6000/5160.abc:14` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter 8th (96+48 = 144t) &nbsp; beat = dotted quarter
  ```
  c2BAGFEDC4C((3:2:2DD/)EF>F-F4 (FA4)GFGFG4
  ```
- `6000/5160.abc:20` &nbsp; `(3:2:2` &nbsp; N=3 &nbsp; durations = quarter 8th (96+48 = 144t) &nbsp; beat = dotted quarter
  ```
  E/E/E3DD/D/-D2G2F6((3:2:2AA/)-(A2G)GG2EE2B,6
  ```
- `1000/672.abc:11` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  D2DF2FA2A c2c ed cB AG FE ((3DEF)G4F8|
  ```
- `18000/17875.abc:27` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  G2 G2 FF F2 C=D FG E4 B2 =A2 B2 c2 ((3BcB) _A6:|
  ```
- `18000/17191.abc:14` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  (ce)e/e/ (e3c/)c/-c2 L(B2 G4) E/G/-G2 A/B/-B2 ((3BcB)A2-A3:|
  ```
- `19000/19000.abc:15` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  B {/!~(!B}y!~)!d2d !~(!d2!~)!!~(!B3!~)!AA FF2 A3 ((3BAG)-G3:|
  ```
- `19000/18679.abc:16` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  =GG GA F=G EF =GG2 A=G4 !~(!G!~)!B BB (3(BcB) A2
  ```
- `19000/18828.abc:15` &nbsp; `(3:3:2` &nbsp; N=3 &nbsp; durations = quarter 8th (96+48 = 144t) &nbsp; beat = dotted quarter
  ```
  !~(!E!~)!ccc2c !~(!c2!~)!B Bc2c2AG3 EFF (3:3:2(F2_D) C6 :|
  ```
- `4000/3108.abc:19` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  E3EF2GFF2((3FAG)F4:|
  ```
- `4000/3690.abc:11` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  d!~(!d2!~)!cB2A!~(!B2!~)!F!breath!F2DF2GA2c2A (3ABA !breath!G2
  ```
- `12000/11729.abc:17` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  {/!~(!G}y!~)!A/A3A/G3F/G/ !~(!F//!~)!!~(!G//!~)!!~(!__B3/2!~)! ((3G__BA)-A4:|
  ```
- `12000/11063.abc:17` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  fe dcB4((3FGA)B6cB AG FE ((3DEF)G6F8:|
  ```
- `12000/11063.abc:17` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  fe dcB4((3FGA)B6cB AG FE ((3DEF)G6F8:|
  ```
- `13000/12314.abc:12` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  BB2AGF F{/!~(!F}y!~)!!~(!G2!~)!D!breath!D2A,3DF2((3GAG)F2-!breath!F3|
  ```
- `17000/16680.abc:15` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  GB B=AB4Bd cc cB ((3BcB)_A2GG GF FF !~(!=D!~)!F FE-E4:|
  ```
- `17000/16991.abc:12` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  dd2cB2A(B2G)F2D3F2G ((3ABA)G2-G3|
  ```
- `23000/22836.abc:19` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  [Q:" slower"]{/G/A}B3BB3B/B/{/!~(!B}y!~)!!~(!d6!~)!B2AA2A2((3_FGA)__B6((3GA__B)c6dc__B8:||
  ```
- `23000/22836.abc:19` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  [Q:" slower"]{/G/A}B3BB3B/B/{/!~(!B}y!~)!!~(!d6!~)!B2AA2A2((3_FGA)__B6((3GA__B)c6dc__B8:||
  ```
- `23000/22104.abc:24` &nbsp; `(3` &nbsp; N=3 &nbsp; durations = 8th 8th 8th (48+48+48 = 144t) &nbsp; beat = dotted quarter
  ```
  |:A2AAd4d3d (3(ddd)d8!~(!c2!~)!B2c2B2cBA6|
  ```
