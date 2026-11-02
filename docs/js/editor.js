// PinSpo site editor. Open the site with ?edit.
// Click text to change it, click a photo to swap it, hover a block to copy or remove it,
// then Publish (commits straight to GitHub) or Download (save index.html + photos yourself).
import { renderVideo } from "./site.js";

const TEXT = "h1, h2, h3, p, li, summary, td, figcaption, .float b, .float span:not(.tile), .btn, .nav nav a, .brand";
const REPEAT = ".row, .steps li, .keys tr, .faq details, .shot";
const SKIP = "[data-editor-ui], .stage, script";
const store = {
  get: (k, d = "") => localStorage.getItem("pinspo-editor-" + k) ?? d,
  set: (k, v) => localStorage.setItem("pinspo-editor-" + k, v),
};
const newImages = new Map(); // path -> data URL
let dirty = false;

document.body.classList.add("editing");
injectStyle();
makeEditable(document);
const bar = buildBar();
const hoverTools = buildHoverTools();
window.addEventListener("beforeunload", (e) => { if (dirty) e.preventDefault(); });
console.info("[PinSpo editor] ready");

function makeEditable(root) {
  root.querySelectorAll(TEXT).forEach((el) => {
    if (el.closest(SKIP) || el.querySelector(TEXT)) return;
    el.contentEditable = "true";
    el.spellcheck = true;
    el.addEventListener("input", markDirty);
  });
  root.querySelectorAll("main img, footer img").forEach((img) => {
    if (img.closest(SKIP)) return;
    img.dataset.swappable = "";
  });
}

// Links and buttons shouldn't navigate while you edit them.
document.addEventListener("click", (e) => {
  if (e.target.closest("[data-editor-ui]")) return;
  const img = e.target.closest("img[data-swappable]");
  if (img) { e.preventDefault(); swapImage(img); return; }
  const a = e.target.closest("a");
  if (a) e.preventDefault();
}, true);

function swapImage(img) {
  const input = Object.assign(document.createElement("input"), { type: "file", accept: "image/*" });
  input.onchange = async () => {
    const file = input.files[0];
    if (!file) return;
    const { dataUrl, width, height, ext } = await shrink(file);
    const base = file.name.replace(/\.[^.]+$/, "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "photo";
    const path = `img/${base}-${Date.now().toString(36)}.${ext}`;
    newImages.set(path, dataUrl);
    img.src = dataUrl;
    img.dataset.path = path;
    img.width = width;
    img.height = height;
    markDirty();
  };
  input.click();
}

// Big photos are scaled to 1600px wide so the page stays fast.
async function shrink(file) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, 1600 / bitmap.width);
  const c = document.createElement("canvas");
  c.width = Math.round(bitmap.width * scale);
  c.height = Math.round(bitmap.height * scale);
  c.getContext("2d").drawImage(bitmap, 0, 0, c.width, c.height);
  const png = file.type === "image/png" && file.size < 400_000;
  return {
    dataUrl: c.toDataURL(png ? "image/png" : "image/jpeg", 0.88),
    width: c.width, height: c.height, ext: png ? "png" : "jpg",
  };
}

function buildHoverTools() {
  const box = ui("div", "pe-hover");
  box.innerHTML = `<button title="Copy this block">+ Copy</button><button title="Remove this block">× Remove</button>`;
  document.body.append(box);
  let target = null;
  document.addEventListener("mouseover", (e) => {
    if (e.target.closest("[data-editor-ui]")) return;
    const block = e.target.closest(REPEAT);
    if (!block || block === target) return;
    target = block;
    const r = block.getBoundingClientRect();
    box.style.top = `${r.top + scrollY + 6}px`;
    box.style.left = `${r.right + scrollX - 160}px`;
    box.style.display = "flex";
  });
  const [copy, remove] = box.querySelectorAll("button");
  copy.onclick = () => {
    if (!target) return;
    const clone = target.cloneNode(true);
    target.after(clone);
    clone.querySelectorAll("[contenteditable]").forEach((el) => el.addEventListener("input", markDirty));
    markDirty();
  };
  remove.onclick = () => {
    if (!target) return;
    if (!confirm("Remove this block?")) return;
    target.remove();
    target = null;
    box.style.display = "none";
    markDirty();
  };
  return box;
}

function buildBar() {
  const el = ui("div", "pe-bar");
  el.innerHTML = `
    <span class="pe-title">Editing <i>· click text to type, click a photo to swap it</i></span>
    <button data-act="links">Links</button>
    <button data-act="setup">GitHub</button>
    <button data-act="download">Download</button>
    <button data-act="publish" class="pe-main">Publish</button>
    <a href="${location.pathname}" class="pe-exit">Exit</a>
    <div class="pe-panel" data-panel="links" hidden>
      <label>Download link <input name="download" placeholder="https://…"></label>
      <label>Discord invite <input name="discord" placeholder="https://discord.gg/…"></label>
      <label>YouTube video <input name="youtube" placeholder="https://youtu.be/…"></label>
      <button data-act="apply-links" class="pe-main">Apply</button>
    </div>
    <div class="pe-panel" data-panel="setup" hidden>
      <p>Publish saves straight to GitHub. Your token stays in this browser only.
         Never paste it in chat or anywhere else.</p>
      <label>Repo <input name="repo" placeholder="Koiduu/pinspo"></label>
      <label>Branch <input name="branch" placeholder="main"></label>
      <label>Site folder <input name="folder" placeholder="docs"></label>
      <label>Token <input name="token" type="password" placeholder="github_pat_…"></label>
      <button data-act="save-setup" class="pe-main">Save</button>
    </div>
    <div class="pe-status" aria-live="polite"></div>`;
  document.body.append(el);
  const field = (n) => el.querySelector(`[name=${n}]`);
  const panel = (n) => el.querySelector(`[data-panel=${n}]`);
  const toggle = (n) => el.querySelectorAll(".pe-panel").forEach((p) => p.hidden = p.dataset.panel !== n || !p.hidden);

  field("download").value = document.querySelector("[data-download]")?.getAttribute("href") || "";
  field("discord").value = document.querySelector("[data-discord]")?.getAttribute("href") || "";
  field("youtube").value = document.querySelector(".video")?.dataset.youtube || "";
  field("repo").value = store.get("repo", "Koiduu/PinterestMod");
  field("branch").value = store.get("branch", "devin/1785365383-pinspo-mod");
  field("folder").value = store.get("folder", "docs");
  field("token").value = store.get("token");

  el.addEventListener("click", async (e) => {
    const act = e.target.closest("[data-act]")?.dataset.act;
    if (act === "links" || act === "setup") toggle(act);
    if (act === "apply-links") {
      document.querySelectorAll("[data-download]").forEach((a) => a.setAttribute("href", field("download").value.trim()));
      document.querySelectorAll("[data-discord]").forEach((a) => a.setAttribute("href", field("discord").value.trim()));
      document.querySelectorAll(".video").forEach((v) => { v.dataset.youtube = field("youtube").value.trim(); renderVideo(v); });
      panel("links").hidden = true;
      markDirty();
      status("Links updated. Publish to make them live.");
    }
    if (act === "save-setup") {
      ["repo", "branch", "folder", "token"].forEach((n) => store.set(n, field(n).value.trim()));
      panel("setup").hidden = true;
      status("Saved.");
    }
    if (act === "download") download();
    if (act === "publish") publish().catch((err) => {
      console.error("[PinSpo editor] publish failed", err);
      status(`Publish failed: ${err.message}`, true);
    });
  });
  return el;
}

function status(text, bad = false) {
  const s = bar.querySelector(".pe-status");
  s.textContent = text;
  s.classList.toggle("bad", bad);
}

function markDirty() {
  dirty = true;
  bar.querySelector(".pe-main[data-act=publish]").textContent = "Publish •";
}

// Clean copy of the page as it should be saved: no editor bits, no runtime state.
function cleanHtml() {
  const doc = document.documentElement.cloneNode(true);
  doc.querySelectorAll("[data-editor-ui], [data-generated]").forEach((el) => el.remove());
  doc.querySelectorAll("[contenteditable]").forEach((el) => { el.removeAttribute("contenteditable"); el.removeAttribute("spellcheck"); });
  doc.querySelectorAll("[data-swappable]").forEach((el) => el.removeAttribute("data-swappable"));
  doc.querySelectorAll("img[data-path]").forEach((img) => { img.setAttribute("src", img.dataset.path); img.removeAttribute("data-path"); });
  doc.querySelectorAll(".stage").forEach((s) => s.classList.remove("ready", "fallback"));
  const body = doc.querySelector("body");
  body.classList.remove("editing", "capture");
  if (!body.classList.length) body.removeAttribute("class");
  doc.querySelectorAll("[class='']").forEach((el) => el.removeAttribute("class"));
  return "<!doctype html>\n" + doc.outerHTML + "\n";
}

function download() {
  save("index.html", new Blob([cleanHtml()], { type: "text/html" }));
  for (const [path, dataUrl] of newImages) save(path.split("/").pop(), dataUrlBlob(dataUrl));
  status(newImages.size
    ? `Downloaded index.html and ${newImages.size} photo(s). Put index.html in the site folder and the photos in its img folder.`
    : "Downloaded index.html. Put it in the site folder.");
}

function save(name, blob) {
  const a = Object.assign(document.createElement("a"), { href: URL.createObjectURL(blob), download: name });
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

function dataUrlBlob(dataUrl) {
  const [head, b64] = dataUrl.split(",");
  const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  return new Blob([bytes], { type: head.slice(5, head.indexOf(";")) });
}

async function publish() {
  const repo = store.get("repo"), branch = store.get("branch"), token = store.get("token");
  const folder = store.get("folder", "docs").replace(/^\/|\/$/g, "");
  if (!repo || !branch || !token) { status("Set up GitHub first (GitHub button).", true); return; }
  const api = async (path, opts = {}) => {
    const res = await fetch(`https://api.github.com/repos/${repo}/contents/${path}`, {
      ...opts,
      headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", ...opts.headers },
    });
    if (res.status === 404 && !opts.method) return null;
    if (!res.ok) throw new Error(`${res.status} ${(await res.json().catch(() => ({}))).message || res.statusText} (${path})`);
    return res.json();
  };
  const put = async (path, b64, message) => {
    const full = folder ? `${folder}/${path}` : path;
    const existing = await api(`${full}?ref=${encodeURIComponent(branch)}`);
    await api(full, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message, content: b64, branch, ...(existing ? { sha: existing.sha } : {}) }),
    });
  };
  for (const [path, dataUrl] of newImages) {
    status(`Uploading ${path}…`);
    await put(path, dataUrl.split(",")[1], `site: add ${path}`);
  }
  status("Saving page…");
  const bytes = new TextEncoder().encode(cleanHtml());
  let bin = "";
  bytes.forEach((b) => bin += String.fromCharCode(b));
  await put("index.html", btoa(bin), "site: edit page");
  newImages.clear();
  dirty = false;
  bar.querySelector(".pe-main[data-act=publish]").textContent = "Publish";
  status("Published. The live site updates in about a minute.");
  console.info("[PinSpo editor] published to", repo, branch);
}

function ui(tag, cls) {
  const el = document.createElement(tag);
  el.className = cls;
  el.dataset.editorUi = "";
  return el;
}

function injectStyle() {
  const s = ui("style", "");
  s.textContent = `
  body.editing { padding-bottom: 90px; }
  body.editing [contenteditable]:hover { outline: 1px dashed #ff1f42; outline-offset: 3px; }
  body.editing [contenteditable]:focus { outline: 2px solid #ff1f42; outline-offset: 3px; }
  body.editing img[data-swappable] { cursor: copy; }
  body.editing img[data-swappable]:hover { outline: 3px solid #ff1f42; }
  .pe-bar { position: fixed; left: 50%; bottom: 18px; transform: translateX(-50%); z-index: 99;
    display: flex; gap: 6px; align-items: center; padding: 8px 8px 8px 18px; border-radius: 999px;
    background: #141416; color: #f4f2ee; font: 500 13px Inter, system-ui, sans-serif;
    box-shadow: 0 10px 30px rgba(0,0,0,.25); white-space: nowrap; }
  .pe-title i { color: #8d8c92; font-style: normal; margin-right: 8px; }
  .pe-bar button, .pe-exit { font: inherit; color: inherit; background: #2a2a2e; border: 0;
    border-radius: 999px; padding: 8px 14px; cursor: pointer; text-decoration: none; }
  .pe-bar button:hover, .pe-exit:hover { background: #3a3a40; }
  .pe-bar .pe-main { background: #ff1f42; color: #fff; }
  .pe-bar .pe-main:hover { background: #c70020; }
  .pe-panel { position: absolute; bottom: 58px; right: 0; width: 340px; padding: 16px; border-radius: 16px;
    background: #141416; display: grid; gap: 10px; white-space: normal; }
  .pe-panel[hidden] { display: none; }
  .pe-panel p { margin: 0; color: #a3a3aa; font-size: 12.5px; line-height: 1.5; }
  .pe-panel label { display: grid; gap: 4px; color: #a3a3aa; font-size: 12px; }
  .pe-panel input { font: inherit; color: #fff; background: #2a2a2e; border: 1px solid #3a3a40;
    border-radius: 8px; padding: 8px 10px; }
  .pe-status { position: absolute; bottom: 58px; left: 0; max-width: 520px; white-space: normal;
    color: #141416; font-size: 12.5px; }
  .pe-status.bad { color: #c70020; }
  .pe-hover { position: absolute; z-index: 98; display: none; gap: 4px; }
  .pe-hover button { font: 500 11.5px Inter, sans-serif; border: 0; border-radius: 999px; padding: 5px 10px;
    background: #141416; color: #fff; cursor: pointer; }
  .pe-hover button:last-child { background: #ff1f42; }
  @media (max-width: 860px) { .pe-title i { display: none; } .pe-bar { gap: 4px; padding-left: 12px; } }`;
  document.head.append(s);
}
