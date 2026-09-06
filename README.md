# Electrical Age — full-source Minecraft 1.21.1 migration

Branch: `port/1.21.1-full-source`.

**This is the compiler-driven port of the entire Re-Wired source, NOT a playable release.**

Open `ports/1.21.1-full/` to edit the original machine and item code. The untouched baseline is beside it in `reference/rewired-1.12.2/`. See `ports/1.21.1-full/MIGRATION.md` and the full-source workflow's compiler reports.

`ports/1.21.1/` remains the older five-block prototype for historical reference. Its successful tests do not validate the full-source port. The root legacy build is the original 1.7 branch reference and is not the target build either.

The full-source build intentionally stays red while real API errors remain. No original machines are removed or replaced with demo items to make it green.
