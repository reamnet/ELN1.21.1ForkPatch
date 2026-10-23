# Embedded ProjectE value calculator

Source: https://github.com/sinkillerj/ProjectE/tree/f432b0c66837759fb0731c9144dc53176b949c5d

The nine Java files under `mods.eln.transparentnode.fusion.value` are adapted from ProjectE’s MIT-licensed graph mapper, collectors, interfaces and fraction arithmetic. The MIT notice ships in `assets/eln/licenses/ProjectE.txt`. Packages are relocated; logging, the free-value constant and the denominator overflow check are self-contained. No ProjectE runtime, registry, transmutation feature or network API is included.

Only ELN’s fusion matter reactor uses this private calculator. The recipe adapter, electrical operation and balance curve are ELN code.
