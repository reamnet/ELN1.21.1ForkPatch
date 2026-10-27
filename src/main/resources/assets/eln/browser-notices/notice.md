# Bundled browser bridge

ELN includes the unmodified CinemaMod MCEF NeoForge 2.1.6-1.21.1 jar as a nested dependency.
MCEF is copyright CinemaMod Group, LGPL-2.1-or-later. See `mcef-license.txt`.
The jar contains the CinemaMod Java CEF API; see `java-cef-license.txt`.

- MCEF source/build: https://github.com/CinemaMod/mcef/tree/1.21.1
- Original release: https://modrinth.com/mod/mcef/version/euXLgkSB
- Maven coordinate: `com.cinemamod:mcef-neoforge:2.1.6-1.21.1`
- Java CEF revision recorded by the bridge:
  https://github.com/CinemaMod/java-cef/tree/a78e832f9f13c2c688caea3d04d8b84fcd238d94

Chromium native libraries are not inside ELN's jar. MCEF's normal client installer retrieves
the matching platform runtime from its configured publisher mirror, and checks its checksum
on startup. Its default mirror is https://mcef-download.cinemamod.com . Native Chromium/CEF
license notices accompany that separate runtime. Dedicated servers do not install Chromium.

ELN's original browser integration does not copy or modify the bridge's source. Replacing a
compatible MCEF bridge remains possible through NeoForge's normal mod/dependency resolution.
