// Nautilus Client download page: always links the newest GitHub release.
(() => {
  // owner/repo: taken from the GitHub Pages URL (owner.github.io/repo), with a fallback
  const FALLBACK = { owner: "dizzle-hd", repo: "Kraken" };
  const host = location.hostname;
  const fromPages = host.endsWith(".github.io")
    ? { owner: host.split(".")[0], repo: location.pathname.split("/").filter(Boolean)[0] }
    : null;
  const { owner, repo } = fromPages && fromPages.repo ? fromPages : FALLBACK;

  const repoUrl = `https://github.com/${owner}/${repo}`;
  const api = `https://api.github.com/repos/${owner}/${repo}`;

  document.querySelectorAll(".repo-link").forEach((a) => (a.href = repoUrl));
  document.querySelectorAll(".repo-issues").forEach((a) => (a.href = `${repoUrl}/issues`));
  const allLink = document.getElementById("dl-all");
  allLink.href = `${repoUrl}/releases`;

  const btn = document.getElementById("dl-button");
  const label = document.getElementById("dl-label");
  const meta = document.getElementById("dl-meta");
  const list = document.getElementById("releases");

  const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
  function ago(iso) {
    const diff = (new Date(iso).getTime() - Date.now()) / 1000;
    const steps = [["year", 31536000], ["month", 2592000], ["week", 604800], ["day", 86400], ["hour", 3600], ["minute", 60]];
    for (const [unit, s] of steps) {
      if (Math.abs(diff) >= s) return rtf.format(Math.round(diff / s), unit);
    }
    return "just now";
  }
  const size = (b) => (b > 1048576 ? (b / 1048576).toFixed(1) + " MB" : Math.max(1, Math.round(b / 1024)) + " KB");
  const jarOf = (release) =>
    (release.assets || []).find((a) => a.name.endsWith(".jar") && !a.name.includes("sources"));
  const buildOf = (r) => {
    const m = /build[.\s-]*(\d+)/i.exec(r.name || r.tag_name || "");
    return m ? `Build ${m[1]}` : (r.name || r.tag_name);
  };
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  function fallback(message) {
    btn.classList.remove("is-loading");
    btn.href = `${repoUrl}/releases`;
    btn.target = "_blank";
    label.textContent = "Go to the releases on GitHub";
    meta.textContent = message;
  }

  async function load() {
    try {
      // the API does not return releases in date order (a new build can end up on position 7+),
      // so load a whole page and sort by publish date ourselves
      const res = await fetch(`${api}/releases?per_page=100`, { cache: "no-cache", headers: { Accept: "application/vnd.github+json" } });
      if (!res.ok) throw new Error(res.status === 403 ? "GitHub limit reached – please try again later." : "HTTP " + res.status);
      const releases = (await res.json())
        .filter((r) => !r.draft)
        .sort((a, b) => new Date(b.published_at || b.created_at) - new Date(a.published_at || a.created_at))
        .slice(0, 6);
      const latest = releases.find((r) => !r.prerelease && jarOf(r)) || releases.find((r) => jarOf(r));
      renderList(releases, latest);
      if (!latest) return fallback("No release yet – the first build is running.");

      const jar = jarOf(latest);
      btn.classList.remove("is-loading");
      btn.href = jar.browser_download_url;
      btn.removeAttribute("target");
      label.textContent = `Download alpha · ${buildOf(latest)}`;
      meta.textContent = `${jar.name} · ${size(jar.size)} · ${ago(latest.published_at)}`;
    } catch (e) {
      renderList([], null);
      fallback(e.message && e.message.startsWith("GitHub") ? e.message : "Could not load the release info.");
    }
  }

  function renderList(releases, latest) {
    if (!releases.length) {
      list.innerHTML = `<div class="empty glass">No versions yet – <a href="${repoUrl}/releases" target="_blank" rel="noopener">releases on GitHub</a>.</div>`;
      return;
    }
    list.innerHTML = releases
      .map((r) => {
        const jar = jarOf(r);
        return `<div class="release glass reveal">
          <span class="name">${esc(r.name || r.tag_name)}</span>
          ${r === latest ? '<span class="badge">Latest</span>' : ""}
          <span class="alpha">Alpha</span>
          <span class="when">${esc(ago(r.published_at))}</span>
          ${jar ? `<a class="get" href="${esc(jar.browser_download_url)}">${esc(jar.name)} ↓</a>`
                : `<a class="get" href="${esc(r.html_url)}" target="_blank" rel="noopener">View →</a>`}
        </div>`;
      })
      .join("");
    observe(list.querySelectorAll(".reveal"));
  }

  // screenshots lightbox
  const box = document.getElementById("lightbox");
  document.querySelectorAll(".shot").forEach((b) =>
    b.addEventListener("click", () => {
      box.querySelector("img").src = b.dataset.src;
      box.querySelector("img").alt = b.querySelector("img").alt;
      box.hidden = false;
    })
  );
  box.addEventListener("click", () => (box.hidden = true));
  document.addEventListener("keydown", (e) => e.key === "Escape" && (box.hidden = true));

  // reveal on scroll
  const io = "IntersectionObserver" in window
    ? new IntersectionObserver((entries) => entries.forEach((en) => en.isIntersecting && en.target.classList.add("in")), { threshold: 0.12 })
    : null;
  function observe(nodes) {
    nodes.forEach((n) => (io ? io.observe(n) : n.classList.add("in")));
  }
  document.querySelectorAll(".card, .mod, .shot, .steps li").forEach((n) => n.classList.add("reveal"));
  observe(document.querySelectorAll(".reveal"));

  load();
})();
