// Module guide: every module is clickable and opens a panel with what it does, its presets and all
// settings. Settings, presets and the in-game text come from modules.json, which is generated from
// the Java sources (tools/gen_modules.py) on every deploy – so the page always matches the client.
(() => {
  // short English summary + highlights per module (keyed by the Java class name)
  const INFO = {
    TriggerBot: ["Attacks as soon as your crosshair is on a valid target – with a human reaction time, a delay after the weapon is charged and a random reach per hit.",
      ["Players, monsters, animals and invisible targets selectable", "Reaction time and extra delay as min/max ranges", "Random reach per hit and optional skipped opportunities", "Only-crits mode, pauses while eating or blocking"]],
    SilentAim: ["Clicks that would miss are redirected to the best target in a cone around your crosshair. Only the server sees the rotation – your camera never moves.",
      ["FOV cone and range", "Optionally through walls", "Only when you would miss, or on every click"]],
    Aimbot: ["Pulls your crosshair smoothly onto the best target in your FOV (per frame, ease-out with a maximum turn speed).",
      ["Aims at the point that needs the least rotation", "Leads moving targets (prediction) and keeps the current target", "Only while clicking or only horizontal, if you like"]],
    AutoCrystal: ["Places end crystals on obsidian/bedrock where they hurt the target most and breaks them – optionally the moment they appear. Damage is calculated like vanilla (distance, cover, armor, protection, resistance).",
      ["Target mode or crosshair mode (hold a crystal, look at obsidian)", "Min. damage, max. self damage, anti-suicide, face place", "Auto switch, silent rotation, only own crystals, render"]],
    AnchorMacro: ["Charges the respawn anchor you look at with glowstone and detonates it with another item – a whole anchor combo in a few ticks.",
      ["Triggers: new anchors, always or a macro key", "Charges, delay + random, detonate item", "Max. self damage and anti-suicide; off in the Nether"]],
    SafeAnchor: ["Places a shield block between you and the anchor right before it explodes. Works alone or together with AnchorMacro.",
      ["Glowstone, obsidian or any block", "Towards you or additionally on top"]],
    AutoTotem: ["Keeps a totem of undying in your off hand – always, or only below a health limit (above it a crystal or golden apple).",
      ["Health limit", "Item above the limit selectable", "Works through the inventory + off-hand key"]],
    Surround: ["Surrounds your feet with obsidian so nobody can place crystals right next to you.",
      ["Centers you on the block", "Support blocks and double height", "Turns off when you walk away"]],
    PopCounter: ["Counts the totems other players pop and tells you how many a player popped before dying. The counts also show up in the target HUD. Only reads what the server sends – it never sends anything.",
      ["Toast for every pop and for deaths after pops", "Your own pops optional", "Only players within 32 blocks, if you like"]],
    SpearSwitch: ["Lunge on repeat: you hold an empty (or other) slot, and for every jab it switches to the spear, lunges and switches straight back in the same tick – only the short cooldown of the empty hand counts.",
      ["Triggers: module on, hold a lunge key or hold attack", "Interval, random ticks, park slot", "Only spears with Lunge, hunger guard"]],
    MaceSwap: ["Keep your sword in hand: as soon as you hit while falling far enough for a smash, the hit goes out with the mace – switched in the same tick – and you are back on your slot right after.",
      ["Works for your own clicks and all combat modules", "Picks the best mace for the target (Density for high falls, Breach against armor) or your preferred enchantment", "Min. fall height, also without a fall, only players"]],
    AutoSmash: ["Hits for you while you fall onto a target – one smash per fall. With timing “Spät” (late) it waits for the last tick before you land, because the smash damage grows with every block.",
      ["Crosshair or silent aim", "Range and min. fall height", "Uses the mace in your hand or the one MaceSwap switches to"]],
    StunSlam: ["The target blocks with a shield? In the same tick it first hits with an axe (shield disabled for 5 s), then your real hit lands – with MaceSwap the mace smash.",
      ["Only while falling, or on every hit", "Checks that the shield really faces you"]],
    WindBoost: ["Jumps and throws a wind charge straight down in the same tick – launches you high into the air for a mace smash. Only the server sees you looking down.",
      ["Trigger “Einmal”: every press of the module key boosts once", "Trigger “Taste”: own boost key while the module stays on", "Uses the off hand first, switches back afterwards"]],
    AutoSprint: ["Holds the sprint key for you while you walk forward.", []],
    AutoEat: ["Eats automatically when your hunger drops below the limit (Lunge costs a lot of hunger) and switches back afterwards.",
      ["Hunger limit", "Golden apples optional", "Not while you hold the attack key"]],
    XRay: ["Hides everything except the ores and containers you choose, optionally with fullbright. (Does not work with Sodium – switches itself off there.)",
      ["Every ore / container selectable", "Fullbright"]],
    BaseFinder: ["Looks for signs of player bases – every hint can be toggled on its own.",
      ["Amethyst growth: geodes with many fully grown clusters", "Sound blocks: marks through walls where the server plays sounds (pistons, doors, chests, mobs …)", "Storage, player blocks, active portals, grown kelp, entities", "Chat messages and tracers"]],
    Hud: ["Shows the HUD elements as glass pills: logo, module list, coordinates, FPS, ping, TPS, speed, direction, clock, notifications, target HUD, mace smash preview and armor durability.",
      ["HUD colour: orange, red, pink, purple, blue, turquoise, green, gold, white or a slowly cycling rainbow (also in the first-start setup)", "Target HUD: name, distance, health bar with absorption, armor and totem pops of your target", "Smash preview: fall height and the damage a mace smash would do right now", "Liquid glass on/off, wheel size"]],
    Notifications: ["Shows a glass toast in the HUD when a module is switched on or off and when BaseFinder finds something.", []],
    HudEditor: ["Opens the HUD editor: drag elements (they snap to edges, the center and each other), right click hides one, “Reset” restores the default layout.", []],
    Setup: ["Opens the first-start setup again: wheel key, wheel size, liquid glass, notes.", []],
    Zoom: ["Zooms in on its own key (default C) – hold or toggle, 1.5–50×, scroll to zoom further, smooth in/out and a slower mouse.", []],
    Freecam: ["Detaches the camera from your player and lets it fly freely, even through blocks. Your player stays where it is and is visible.", []],
  };

  const RISK = [["low", "Low ban risk"], ["mid", "Medium ban risk"], ["high", "High ban risk"]];
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const fmt = (v) => (typeof v === "number" ? String(v).replace(".", ",") : v);

  const grid = document.getElementById("module-grid");
  const dialog = document.getElementById("module-dialog");
  if (!grid || !dialog) return;
  const body = dialog.querySelector(".md-body");
  let data = null, index = -1;

  function value(s) {
    switch (s.type) {
      case "bool": return `<span class="val ${s.default ? "on" : "off"}">${s.default ? "On" : "Off"}</span>`;
      case "number": return `<span class="val">${esc(fmt(s.default))}</span><span class="range">${esc(fmt(s.min))} – ${esc(fmt(s.max))}</span>`;
      case "mode": return s.modes.map((m) => `<span class="val${m === s.default ? " on" : " opt"}">${esc(m)}</span>`).join("");
      case "key": return `<span class="val off">not bound</span>`;
      default: return "";
    }
  }

  function render(i) {
    index = (i + data.modules.length) % data.modules.length;
    const m = data.modules[index];
    const cat = data.categories.find((c) => c.id === m.category);
    const [summary, highlights] = INFO[m.id] || [m.description, []];
    body.innerHTML = `
      <div class="md-head">
        <span class="md-cat">${esc(cat ? cat.name : m.category)}${cat && cat.experimental ? " · experimental" : ""}</span>
        <h3 id="md-title">${esc(m.name)}</h3>
      </div>
      <p class="md-summary">${esc(summary)}</p>
      ${highlights.length ? `<ul class="md-points">${highlights.map((h) => `<li>${esc(h)}</li>`).join("")}</ul>` : ""}
      <p class="md-ingame"><span>In game</span>${esc(m.description)}</p>
      ${m.presets.length ? `<h4>Presets</h4><div class="md-presets">${m.presets.map((p) => `
        <div class="preset"><span class="risk ${RISK[p.risk][0]}" title="${RISK[p.risk][1]}"></span>
          <strong>${esc(p.name)}</strong><span>${esc(p.note)}</span></div>`).join("")}</div>` : ""}
      <h4>Settings <small>(${m.settings.length + 1}, names as in the game)</small></h4>
      <div class="md-settings">
        ${m.settings.map((s) => `
          <div class="setting">
            <div class="s-top"><strong>${esc(s.name)}</strong><span class="s-val">${value(s)}</span></div>
            <p>${esc(s.desc)}${s.conditional ? ' <em>(only shown for some options)</em>' : ""}</p>
          </div>`).join("")}
        <div class="setting">
          <div class="s-top"><strong>Taste</strong><span class="s-val"><span class="val off">not bound</span></span></div>
          <p>Key to toggle the module (every module has one).</p>
        </div>
      </div>`;
    body.scrollTop = 0;
    dialog.querySelector(".md-pos").textContent = `${index + 1} / ${data.modules.length}`;
    history.replaceState(null, "", `#module-${m.id}`);
  }

  function open(id) {
    const i = data.modules.findIndex((m) => m.id === id);
    if (i < 0) return;
    render(i);
    if (!dialog.open) dialog.showModal();
  }

  function close() {
    if (dialog.open) dialog.close();
  }
  dialog.addEventListener("close", () => history.replaceState(null, "", "#modules"));
  dialog.querySelector(".md-close").addEventListener("click", close);
  dialog.querySelector(".md-prev").addEventListener("click", () => render(index - 1));
  dialog.querySelector(".md-next").addEventListener("click", () => render(index + 1));
  dialog.addEventListener("click", (e) => e.target === dialog && close()); // click on the backdrop
  dialog.addEventListener("keydown", (e) => {
    if (e.key === "ArrowLeft") render(index - 1);
    if (e.key === "ArrowRight") render(index + 1);
  });

  // the static list in index.html stays as a fallback; with the data every module becomes a button
  function buildGrid() {
    const order = ["COMBAT", "CRYSTAL", "MACE", "PLAYER", "SMP", "RENDER", "VISUALS", "CLIENT"];
    const cats = [...data.categories].sort((a, b) => (order.indexOf(a.id) + 99) % 99 - (order.indexOf(b.id) + 99) % 99);
    grid.innerHTML = cats.map((c) => {
      const mods = data.modules.filter((m) => m.category === c.id);
      if (!mods.length) return "";
      const badge = c.experimental ? ` <span class="badge-exp" title="Experimental – may change, cost FPS or not work everywhere yet">experimental</span>` : "";
      return `<div class="mod glass reveal in${c.experimental ? " mod-exp" : ""}"><h4>${esc(c.name)}${badge}</h4><ul>${mods
        .map((m) => `<li><button type="button" class="mod-btn" data-id="${esc(m.id)}">${esc(m.name)}</button></li>`).join("")}</ul></div>`;
    }).join("");
    grid.querySelectorAll(".mod-btn").forEach((b) => b.addEventListener("click", () => open(b.dataset.id)));
    document.getElementById("module-hint").hidden = false;
  }

  fetch("modules.json", { cache: "no-cache" })
    .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
    .then((json) => {
      data = json;
      buildGrid();
      const fromHash = () => {
        const m = /^#module-(\w+)$/.exec(location.hash);
        if (m) open(m[1]);
      };
      fromHash();
      window.addEventListener("hashchange", fromHash); // links like #module-AutoCrystal on the same page
    })
    .catch(() => { /* keep the static list */ });
})();
