# In-game guide

Press **P** while playing to open Electrical Age's guide. The key can be rebound
under **Options → Controls → Key Binds → ElectricalAge → Wiki**.

- Browse the contents or type part of an item name in **Search items**. Registry
  IDs work too: `eln:` lists all Electrical Age items. Search is not limited to
  the first page, and also finds vanilla and companion-mod recipe ingredients.
- Use the wheel, scrollbar, Page Up/Page Down, or Home/End to move through a page.
- Click an item to see crafting and processing recipes. Hover for its tooltip.
- **Previous** or Escape returns to the previous page and restores its scroll
  position. **Contents** returns to the catalogue; **×** closes the guide.

The page adapts to the GUI scale. Machine recipes show input/output counts, all
outputs, energy cost and the machines that process them. Logic-chip operator and
function help remains available. Recipes come from the current world's recipe
manager and ELN's processing lists; this is not a complete external-mod manual.

## Regression checks

Both GitHub client-smoke profiles (standalone and Create) exercise the registered
P-key handler, native search input, catalogue coverage, scrolling, clipped-item
click rejection, recipe navigation, return position and machine/logic-chip pages.
Rendered-frame assertions check slot placement at GUI scales 1, 2 and 3 and
viewport clipping after a custom 3D item render.

Named pass/fail results are in `contracts/wiki-client.json` and JUnit XML.
`smoke-wiki-*.png` images in the smoke artifacts support visual review; they are
not golden-image tests or an automatic judgement of model quality. Missing or
incomplete client reports fail the smoke/release gate.
