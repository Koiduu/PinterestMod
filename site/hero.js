import * as THREE from "three";
import { SkinObject } from "skinview3d";

const stage = document.getElementById("stage");
const canvas = stage.querySelector("canvas");
const params = new URLSearchParams(location.search);
// ?still=<seconds> freezes one frame; used to render img/hero-still.png.
const still = params.has("still") ? Number(params.get("still")) : null;
if (params.has("capture")) document.body.classList.add("capture");
const reduceMotion = still !== null || matchMedia("(prefers-reduced-motion: reduce)").matches;

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

/** Black and white, like a print. */
function monoSkin(img) {
  const c = document.createElement("canvas");
  c.width = img.width;
  c.height = img.height;
  const g = c.getContext("2d");
  g.drawImage(img, 0, 0);
  const data = g.getImageData(0, 0, c.width, c.height);
  const p = data.data;
  for (let i = 0; i < p.length; i += 4) {
    const [r, gr, b] = [p[i], p[i + 1], p[i + 2]];
    const l = 0.3 * r + 0.59 * gr + 0.11 * b;
    p[i] = p[i + 1] = p[i + 2] = l;
  }
  g.putImageData(data, 0, 0);
  return c;
}

function roundRect(g, x, y, w, h, r) {
  g.beginPath();
  g.roundRect(x, y, w, h, r);
}

const PANEL_W = 640;
const PANEL_H = 400;
const RING_Y = 0.5;

/** A frosted label card: icon tile, title, one line of copy. */
function labelPanel(title, line, icon) {
  const c = document.createElement("canvas");
  c.width = PANEL_W;
  c.height = PANEL_H;
  const g = c.getContext("2d");
  const grad = g.createLinearGradient(0, 0, 0, PANEL_H);
  grad.addColorStop(0, "rgba(255,255,255,0.94)");
  grad.addColorStop(1, "rgba(236,234,230,0.86)");
  roundRect(g, 6, 6, PANEL_W - 12, PANEL_H - 12, 44);
  g.fillStyle = grad;
  g.fill();
  g.lineWidth = 3;
  g.strokeStyle = "rgba(255,255,255,1)";
  g.stroke();

  roundRect(g, 56, 72, 92, 92, 24);
  g.fillStyle = icon === "red" ? "#ff1f42" : "#17171b";
  g.fill();
  g.fillStyle = "#fff";
  g.font = "600 50px Inter, sans-serif";
  g.textAlign = "center";
  g.textBaseline = "middle";
  g.fillText(title[0], 102, 120);

  g.textAlign = "left";
  g.textBaseline = "alphabetic";
  g.fillStyle = "#141416";
  g.font = "600 64px Inter, sans-serif";
  g.fillText(title, 56, 262);
  g.fillStyle = "#77777f";
  g.font = "500 36px Inter, sans-serif";
  g.fillText(line, 56, 318);
  return c;
}

/** A crop of a real in-game screenshot, framed like a PinSpo window. */
function shotPanel(img, sx, sy, sw, sh) {
  const c = document.createElement("canvas");
  c.width = PANEL_W;
  c.height = PANEL_H;
  const g = c.getContext("2d");
  roundRect(g, 6, 6, PANEL_W - 12, PANEL_H - 12, 44);
  g.save();
  g.clip();
  g.filter = "grayscale(1) contrast(1.08)";
  g.drawImage(img, sx, sy, sw, sh, 0, 0, PANEL_W, PANEL_H);
  g.filter = "none";
  const shade = g.createLinearGradient(0, 0, 0, PANEL_H);
  shade.addColorStop(0, "rgba(255,255,255,0.18)");
  shade.addColorStop(1, "rgba(0,0,0,0.12)");
  g.fillStyle = shade;
  g.fillRect(0, 0, PANEL_W, PANEL_H);
  g.restore();
  g.lineWidth = 4;
  g.strokeStyle = "rgba(255,255,255,0.9)";
  roundRect(g, 6, 6, PANEL_W - 12, PANEL_H - 12, 44);
  g.stroke();
  return c;
}

let backTex;
function backTexture() {
  if (backTex) return backTex;
  const c = document.createElement("canvas");
  c.width = PANEL_W;
  c.height = PANEL_H;
  const g = c.getContext("2d");
  roundRect(g, 6, 6, PANEL_W - 12, PANEL_H - 12, 44);
  g.fillStyle = "rgba(232,230,226,0.62)";
  g.fill();
  g.lineWidth = 3;
  g.strokeStyle = "rgba(255,255,255,0.9)";
  g.stroke();
  backTex = new THREE.CanvasTexture(c);
  backTex.colorSpace = THREE.SRGBColorSpace;
  return backTex;
}

/** A curved slice of a cylinder around the head, so the cards wrap the way the screen-glass does. */
function curvedPanel(texCanvas, radius, angle, y, width) {
  const theta = width / radius;
  const geo = new THREE.CylinderGeometry(radius, radius, width * (PANEL_H / PANEL_W), 32, 1, true,
    angle - theta / 2, theta);
  const tex = new THREE.CanvasTexture(texCanvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  const front = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ map: tex, transparent: true, side: THREE.FrontSide, depthWrite: false }));
  // Seen from behind, a card is just frosted glass, not mirrored text.
  const back = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ map: backTexture(), transparent: true, side: THREE.BackSide, depthWrite: false }));
  const panel = new THREE.Group();
  panel.add(back, front);
  panel.position.y = y;
  front.renderOrder = back.renderOrder = 2;
  return panel;
}

async function main() {
  const [skinImg, cams, wall] = await Promise.all([
    loadImage("img/skin.png"),
    loadImage("img/cameras.jpg"),
    loadImage("img/wallphoto.jpg"),
    document.fonts.ready,
  ]);

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(30, 1, 1, 400);

  scene.add(new THREE.AmbientLight(0xffffff, 0.9));
  const key = new THREE.DirectionalLight(0xffffff, 2.4);
  key.position.set(-30, 40, 40);
  scene.add(key);
  const fill = new THREE.DirectionalLight(0xffffff, 1.1);
  fill.position.set(10, -20, 50);
  scene.add(fill);
  const rim = new THREE.DirectionalLight(0xffffff, 0.8);
  rim.position.set(40, 10, -30);
  scene.add(rim);

  const skinTex = new THREE.CanvasTexture(monoSkin(skinImg));
  skinTex.magFilter = THREE.NearestFilter;
  skinTex.minFilter = THREE.NearestFilter;
  skinTex.colorSpace = THREE.SRGBColorSpace;

  const skin = new SkinObject();
  skin.modelType = stage.dataset.model === "slim" ? "slim" : "default";
  skin.map = skinTex;
  // Chin up, eyes on the sky: the Mindly-style pose, but blocky.
  skin.head.rotation.x = -0.55;
  skin.head.rotation.y = 0.22;
  skin.rightArm.rotation.x = 0.05;
  skin.leftArm.rotation.x = -0.05;

  const player = new THREE.Group();
  player.add(skin);
  player.rotation.y = -0.38;
  scene.add(player);

  // The ring sits around the head (head pivot is at y = 0 in SkinObject; the head itself spans 0..8).
  const ring = new THREE.Group();
  ring.position.y = RING_Y;
  // Tipped forward: cards pass under the chin at the front and over the hat at the back.
  ring.rotation.set(0.5, 0, -0.08);
  const R = 14;
  const panels = [
    curvedPanel(labelPanel("Search", "Find refs without alt-tab.", "red"), R, 0.0, 0.6, 10),
    curvedPanel(shotPanel(cams, 0, 0, 760, 475), R, 1.05, -1.6, 9.5),
    curvedPanel(labelPanel("Cameras", "Every side, one account.", "dark"), R, 2.1, 1.2, 10),
    curvedPanel(shotPanel(wall, 300, 40, 760, 475), R, 3.15, -1.0, 9.5),
    curvedPanel(labelPanel("Wall photo", "Trace it at true scale.", "dark"), R, 4.2, 1.4, 10),
    curvedPanel(shotPanel(cams, 640, 420, 760, 475), R, 5.25, -1.2, 9),
  ];
  panels.forEach((p) => ring.add(p));
  scene.add(ring);

  function resize() {
    const w = stage.clientWidth;
    const h = stage.clientHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }
  resize();
  new ResizeObserver(resize).observe(stage);

  const target = new THREE.Vector3(0, 1, 0);
  const pointer = { x: 0, y: 0, sx: 0, sy: 0 };
  addEventListener("pointermove", (e) => {
    pointer.x = e.clientX / innerWidth - 0.5;
    pointer.y = e.clientY / innerHeight - 0.5;
  });

  const clock = new THREE.Clock();
  function frame() {
    const t = still ?? (reduceMotion ? 2 : clock.getElapsedTime());
    pointer.sx += (pointer.x - pointer.sx) * 0.05;
    pointer.sy += (pointer.y - pointer.sy) * 0.05;
    // Low angle, looking up at the character like the reference shot.
    camera.position.set(pointer.sx * 8, -6 + pointer.sy * 3, 40);
    camera.lookAt(target);
    ring.rotation.y = 0.45 + t * 0.12;
    ring.position.y = RING_Y + Math.sin(t * 0.8) * 0.35;
    skin.head.rotation.y = 0.22 + Math.sin(t * 0.4) * 0.06;
    ring.visible = !params.has("noring");
    renderer.render(scene, camera);
    if (!reduceMotion) requestAnimationFrame(frame);
  }
  frame();
  stage.classList.add("ready");
}

main().catch((err) => {
  console.warn("PinSpo hero fell back to the still image:", err);
  stage.classList.add("fallback");
});
