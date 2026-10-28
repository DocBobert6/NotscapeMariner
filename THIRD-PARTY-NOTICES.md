# Third-party notices

Notscape Mariner itself is licensed under the GNU General Public
License v3.0 — see [LICENSE](LICENSE). This file reproduces the
notices required by the third-party code the app ships or downloads
on demand. Short summaries are given for the long licences (GPL,
MPL, Apache); the full licence texts govern, and the app's own
`LICENSE` file carries the full GPLv3 text. The non-commercial
licences are quoted in full where they are short, because their
terms require their notice and conditions to be reproduced in the
distribution materials.

The emulator cores are **not in the APK**. They are built from
pinned source and downloaded on demand from the project's `cores`
release; each core is SHA-256 verified before it is loaded. The
pins below are the exact commits the core builds use.

## Emulator cores

### mGBA — MPL-2.0

- Used for: Game Boy, Game Boy Color, Game Boy Advance.
- Source: `mgba-emu/mgba` at `fd9dc5c2cbf234365068855851107e762fc6211b`.
- Licence: Mozilla Public License 2.0. Source files carry the
  Exhibit A notice: "This Source Code Form is subject to the terms
  of the Mozilla Public License, v. 2.0."
- Compatibility note: MPL-2.0 allows distribution under a
  Secondary Licence (including GPLv3) unless an Exhibit B
  "Incompatible With Secondary Licenses" notice is attached.
  A full-tree grep of the pinned source (2026-10-26) found no
  Exhibit B notice attached to any source file: the phrase
  occurs only inside the MPL-2.0 licence text itself
  (`LICENSE` and the licence text reproduced in
  `tools/debian/copyright`), and no file in `src/` or
  `include/` mentions "Exhibit" or "Secondary Licenses" at all.

### FCEUmm — GPLv2-or-later

- Used for: NES.
- Source: `libretro/libretro-fceumm` at
  `ac0d00a7fc01166f84c7bac01d1084ebfd2b865e`.
- Licence: the `Copying` file carries the GPLv2 text, and the
  source headers grant redistribution "under the terms of the
  GNU General Public License as published by the Free Software
  Foundation; either version 2 of the License, or (at your
  option) any later version."

### Snes9x — non-commercial custom licence

- Used for: Super NES.
- Source: `snes9xgit/snes9x` at
  `1bcc369e89f08243e0a462882fb1f3e42e51de3a`.
- Licence: Snes9x is freeware for personal, non-commercial use
  only. The licence grant, quoted from `LICENSE` at the pin:

  > "Permission to use, copy, modify and/or distribute Snes9x in
  > both binary and source form, for non-commercial purposes,
  > is hereby granted without fee, providing that this license
  > information and copyright notice appear with all copies and
  > any derived work."

  > "Snes9x is freeware for PERSONAL USE only. Commercial users
  > should seek permission of the copyright holders first.
  > Commercial use includes, but is not limited to, charging
  > money for Snes9x or software derived from Snes9x, including
  > Snes9x or derivatives in commercial game bundles, and/or
  > using Snes9x as a promotion for your commercial product."

  > "(Under no circumstances will commercial rights be given)"
  > (attached to two copyright holders' lines in the file.)

- Copyright notice, from `LICENSE` at the pin (abridged to the
  project-level holders; the file also lists per-component
  authors for the BS-X, C4, DSP, Super FX, sound, and filter
  code): Gary Henderson, Jerremy Koot, Matthew Kendora, Peter
  Bortas, Joel Yliluoma, John Weidman, funkyass, Kris Bleakley,
  Brad Jorsch, Nach, zones, nitsuja, BearOso, OV2, qwertymodo,
  Hans-Kristian Arntzen, Daniel De Matteis (libretro port).
- Sub-parts: JMA (GPL/LGPL), snes_ntsc (LGPL), xBRZ (GPLv3),
  per the `LICENSE` tail.

### Genesis Plus GX — non-commercial licence

- Used for: Genesis / Mega Drive, Master System, Game Gear,
  Sega CD.
- Source: `libretro/Genesis-Plus-GX` at
  `aa74d79634cd230f65cce4180e92688e53883d2f`.
- Licence: `LICENSE.txt` at the pin, quoted in full (the
  operative conditions and disclaimer):

  > "Copyright (c) 1998-2003 Charles MacDonald
  > Copyright (c) 2007-2026 Eke-Eke
  > Some portions copyright Nicola Salmoria and the MAME team
  > All rights reserved.
  >
  > Redistribution and use of this code or any derivative
  > works are permitted provided that the following conditions
  > are met:
  >
  > * Redistributions may not be sold, nor may they be used
  >   in a commercial product or activity.
  >
  > * Redistributions that are modified from the original
  >   source must include the complete source code, including
  >   the source code for all components used by a binary
  >   built from the modified sources. However, as a special
  >   exception, the source code distributed need not include
  >   anything that is normally distributed (in either source
  >   or binary form) with the major components (compiler,
  >   kernel, and so on) of the operating system on which the
  >   executable runs, unless that component itself accompanies
  >   the executable.
  >
  > * Redistributions must reproduce the above copyright
  >   notice, this list of conditions and the following
  >   disclaimer in the documentation and/or other materials
  >   provided with the distribution.
  >
  > THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
  > CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
  > WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  > WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  > PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
  > OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
  > INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  > (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
  > GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
  > BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
  > OF LIABILITY, WHETHER IN CONTRACT, TORT (INCLUDING
  > NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
  > OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
  > SUCH DAMAGE."

- Sub-component licences in the tree at the pin (each carries
  its own licence file): Nuked OPN2 core (LGPL-2.1-or-later);
  libchdr (BSD-3-clause); libFLAC (BSD-style, Xiph);
  ntsc filter (LGPL-2.1); minimp3 (CC0 1.0); tremor
  (BSD-style, Xiph); wiidrc utility (MIT); LZMA SDK (public
  domain); zstd (BSD, with a GPLv2 copy also present);
  blip_buf (LGPL-2.1); eq.c (public domain); ym2413/ym2612
  (under the GX licence above); z80 core (non-commercial
  freeware, credit required).

### SwanStation — GPL-3.0

- Used for: PlayStation.
- Source: `libretro/swanstation` at
  `631f0fb31b809e0f889d7c551fe695788e39c1d3`.
- Licence: the `LICENSE` file carries the GPLv3 text. A
  full-tree header sweep of the pinned source (2026-10-26)
  found that 239 of 241 source files under `src/` carry no
  per-file licence header at all; the two exceptions,
  `src/core/pgxp.cpp` and `src/core/pgxp.h`, carry a
  GPLv2-or-later header ("either version 2 of the License,
  or (at your option) any later version"), which may be taken
  under GPLv3.
- Vendored third-party code under `dep/` carries its own
  licences: libchdr (BSD-3-clause), openbios (MIT), xxhash
  (BSD-2-Clause).

### melonDS — GPLv3-or-later

- Used for: Nintendo DS.
- Source: `libretro/melonds` at
  `66b5d2634cd0a79030562811e6e05f5532f800ba`.
- Licence: the `LICENSE` file carries the GPLv3 text, and the
  source headers grant redistribution "under the terms of the
  GNU General Public License as published by the Free Software
  Foundation, either version 3 of the License, or (at your
  option) any later version."

### PicoDrive — non-commercial licence

- Used for: Sega 32X.
- Source: `libretro/picodrive` at
  `1890c2932234c9d30f4cd3851d02228baae8f09e`.
- Licence: `COPYING` at the pin (the file carries no separate
  copyright line; the conditions and disclaimer below are the
  whole operative text), quoted in full:

  > "Redistribution and use of this code or any derivative
  > works are permitted provided that the following conditions
  > are met:
  >
  > * Redistributions may not be sold, nor may they be used
  >   in a commercial product or activity.
  >
  > * Redistributions that are modified from the original
  >   source must include the complete source code, including
  >   the source code for all components used by a binary
  >   built from the modified sources. However, as a special
  >   exception, the source code distributed need not include
  >   anything that is normally distributed (in either source
  >   or binary form) with the major components (compiler,
  >   kernel, and so on) of the operating system on which the
  >   executable runs, unless that component itself accompanies
  >   the executable.
  >
  > * Redistributions must reproduce the above copyright
  >   notice, this list of conditions and the following
  >   disclaimer in the documentation and/or other materials
  >   provided with the distribution.
  >
  > THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
  > CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
  > WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  > WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  > PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
  > OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
  > INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  > (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
  > GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
  > BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
  > OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
  > TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
  > WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
  > THE POSSIBILITY OF SUCH DAMAGE."

## Libraries

### oboe 1.9.0 — Apache-2.0

- Used for: low-latency audio output.
- Licence: Apache License 2.0. The full text is at
  https://www.apache.org/licenses/LICENSE-2.0 ; the standard
  appendix notice applies: "Licensed under the Apache License,
  Version 2.0 (the "License"); you may not use this file except
  in compliance with the License."

### sherpa-onnx 1.13.8 — Apache-2.0

- Used for: on-device text-to-speech (prebuilt AAR, SHA-256
  pinned in CI).
- Licence: Apache License 2.0, same appendix notice as above.

### Media3 1.9.4 — Apache-2.0

- Used for: media playback and the merge/transcode pipeline
  (including media3-transformer).
- Licence: Apache License 2.0, same appendix notice as above.

## Shaders

### lcd3x — public domain

- Used for: handheld LCD shading.
- Author: Gigaherz. The upstream header reads "License:
  Public domain". No licence obligation attaches; the credit
  is kept as good practice.

### crt-guest-advanced-fast — GPLv2-or-later

- Used for: CRT shading.
- Author: guest(r) (2018–2025). The upstream shader files
  carry the GPL header granting redistribution "under the
  terms of the GNU General Public License ... either version
  2 of the License, or (at your option) any later version."
  The port in `app/src/main/cpp/crt_chain.cpp` carries the
  same attribution and grant in its header.
- Note: lcd-grid-v2 is not used anywhere in the app, because
  no licence grant for it could be found; it is not shipped.

## Standing rule

While the app loads Snes9x, Genesis Plus GX, or PicoDrive, it
stays free: no price, no ads, no paid tier, no commercial
bundling. Those three cores' terms permit non-commercial use
only, and commercial use would breach them immediately.
