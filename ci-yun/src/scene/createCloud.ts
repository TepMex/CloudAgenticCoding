import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import {
  boxesOverlap,
  clusterCentroid,
  focusPoint,
  separatedLabels,
  visibleLabels,
  WORD_LABEL_GAP,
  wordGlyph,
  type GlyphLabel,
} from "../lib/focus";
import { glossOf } from "../lib/gloss";
import type { Cloud, Mode } from "../lib/types";

export type CloudViewState = {
  mode: Mode;
  focusId: number | null;
  activeCluster: number | null;
  resetToken: number;
};

export type CloudMount = {
  setState: (state: CloudViewState) => void;
  dispose: () => void;
};

const PAPER = 0xf4ead7;

function circleTexture(): THREE.CanvasTexture {
  const canvas = document.createElement("canvas");
  canvas.width = 64;
  canvas.height = 64;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Нет 2D-контекста для спрайта точки.");
  const gradient = context.createRadialGradient(32, 32, 0, 32, 32, 32);
  gradient.addColorStop(0, "rgba(255,255,255,1)");
  gradient.addColorStop(0.42, "rgba(255,255,255,0.9)");
  gradient.addColorStop(1, "rgba(255,255,255,0)");
  context.fillStyle = gradient;
  context.fillRect(0, 0, 64, 64);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function hexToRgb(hex: string): [number, number, number] {
  const value = Number.parseInt(hex.slice(1), 16);
  return [((value >> 16) & 255) / 255, ((value >> 8) & 255) / 255, (value & 255) / 255];
}

function ellipse(points: readonly [number, number][]): { cx: number; cy: number; rx: number; ry: number; angle: number } {
  const count = points.length || 1;
  let cx = 0;
  let cy = 0;
  for (const [x, y] of points) {
    cx += x;
    cy += y;
  }
  cx /= count;
  cy /= count;
  let xx = 0;
  let yy = 0;
  let xy = 0;
  for (const [x, y] of points) {
    const dx = x - cx;
    const dy = y - cy;
    xx += dx * dx;
    yy += dy * dy;
    xy += dx * dy;
  }
  xx /= count;
  yy /= count;
  xy /= count;
  const angle = 0.5 * Math.atan2(2 * xy, xx - yy);
  const trace = xx + yy;
  const det = xx * yy - xy * xy;
  const disc = Math.sqrt(Math.max(0, (trace * trace) / 4 - det));
  const major = Math.sqrt(Math.max(trace / 2 + disc, 0)) * 2.05;
  const minor = Math.sqrt(Math.max(trace / 2 - disc, 0)) * 2.05;
  return { cx, cy, rx: Math.max(major, 0.35), ry: Math.max(minor, 0.35), angle };
}

export function mountCloud(
  canvas: HTMLCanvasElement,
  overlay: HTMLElement,
  cloud: Cloud,
  onPick: (id: number) => void,
): CloudMount {
  if (cloud.words.some((word, index) => word.id !== index)) {
    throw new Error("Идентификаторы слов должны совпадать с индексом.");
  }

  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const count = cloud.words.length;
  const positions2 = new Float32Array(count * 3);
  const positions3 = new Float32Array(count * 3);
  const colors = new Float32Array(count * 3);
  const palette = cloud.clusters.map((cluster) => hexToRgb(cluster.color));
  const members = cloud.clusters.map(() => [] as number[]);

  for (const word of cloud.words) {
    positions2[word.id * 3] = word.x2;
    positions2[word.id * 3 + 1] = word.y2;
    positions3[word.id * 3] = word.x;
    positions3[word.id * 3 + 1] = word.y;
    positions3[word.id * 3 + 2] = word.z;
    members[word.cluster]?.push(word.id);
  }

  let radius2 = 0.001;
  let radius3 = 0.001;
  for (let index = 0; index < count; index += 1) {
    radius2 = Math.max(radius2, Math.hypot(positions2[index * 3]!, positions2[index * 3 + 1]!));
    radius3 = Math.max(
      radius3,
      Math.hypot(positions3[index * 3]!, positions3[index * 3 + 1]!, positions3[index * 3 + 2]!),
    );
  }

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setClearColor(0x000000, 0);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));

  const scene = new THREE.Scene();
  const perspective = new THREE.PerspectiveCamera(48, 1, 0.1, 400);
  const opening = new THREE.Vector3(0.35, 0.48, 1).normalize();
  const openingDistance = (radius3 * 1.22) / Math.tan(THREE.MathUtils.degToRad(perspective.fov) / 2);
  perspective.position.copy(opening.multiplyScalar(openingDistance));
  perspective.lookAt(0, 0, 0);
  const ortho = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 200);
  ortho.position.set(0, 0, 40);
  let camera: THREE.PerspectiveCamera | THREE.OrthographicCamera = perspective;

  const texture = circleTexture();
  const colorAttr = new THREE.BufferAttribute(colors, 3);
  const positionAttr = new THREE.BufferAttribute(positions3, 3);

  const pointsMaterial = new THREE.PointsMaterial({
    size: 0.42,
    map: texture,
    vertexColors: true,
    transparent: true,
    opacity: 0.95,
    depthWrite: false,
    sizeAttenuation: true,
  });
  const points = new THREE.Points(new THREE.BufferGeometry(), pointsMaterial);
  points.geometry.setAttribute("position", positionAttr);
  points.geometry.setAttribute("color", colorAttr);
  points.frustumCulled = false;
  points.renderOrder = 1;

  const haloMaterial = new THREE.PointsMaterial({
    size: 1.15,
    map: texture,
    vertexColors: true,
    transparent: true,
    opacity: 0.28,
    depthWrite: false,
    depthTest: false,
    sizeAttenuation: true,
    blending: THREE.AdditiveBlending,
  });
  const halo = new THREE.Points(new THREE.BufferGeometry(), haloMaterial);
  halo.geometry.setAttribute("position", positionAttr);
  halo.geometry.setAttribute("color", colorAttr);
  halo.frustumCulled = false;
  halo.renderOrder = 0;
  halo.raycast = () => undefined;

  const hullMaterials: THREE.MeshBasicMaterial[] = [];
  const hull2 = new THREE.Group();
  const hull3 = new THREE.Group();
  for (const cluster of cloud.clusters) {
    const ids = members[cluster.id] ?? [];
    const flat = ids.map((id) => [positions2[id * 3]!, positions2[id * 3 + 1]!] as [number, number]);
    const disk = ellipse(flat);
    const diskMaterial = new THREE.MeshBasicMaterial({
      color: cluster.color,
      transparent: true,
      opacity: 0.14,
      depthWrite: false,
      depthTest: false,
    });
    hullMaterials.push(diskMaterial);
    const diskMesh = new THREE.Mesh(new THREE.CircleGeometry(1, 48), diskMaterial);
    diskMesh.position.set(disk.cx, disk.cy, 0);
    diskMesh.rotation.z = disk.angle;
    diskMesh.scale.set(disk.rx, disk.ry, 1);
    diskMesh.renderOrder = 0;
    hull2.add(diskMesh);

    let cx = 0;
    let cy = 0;
    let cz = 0;
    for (const id of ids) {
      cx += positions3[id * 3]!;
      cy += positions3[id * 3 + 1]!;
      cz += positions3[id * 3 + 2]!;
    }
    const n = ids.length || 1;
    cx /= n;
    cy /= n;
    cz /= n;
    let scatter = 0;
    for (const id of ids) {
      scatter += Math.hypot(positions3[id * 3]! - cx, positions3[id * 3 + 1]! - cy, positions3[id * 3 + 2]! - cz) ** 2;
    }
    const sphereMaterial = new THREE.MeshBasicMaterial({
      color: cluster.color,
      transparent: true,
      opacity: 0.05,
      depthWrite: false,
      depthTest: false,
    });
    hullMaterials.push(sphereMaterial);
    const sphere = new THREE.Mesh(new THREE.SphereGeometry(1, 18, 12), sphereMaterial);
    sphere.position.set(cx, cy, cz);
    sphere.scale.setScalar(Math.max(Math.sqrt(scatter / n) * 0.72, 0.4));
    sphere.renderOrder = 0;
    hull3.add(sphere);
  }

  const lineMaterial = new THREE.LineBasicMaterial({ color: PAPER, transparent: true, opacity: 0.75 });
  const neighborLines = new THREE.LineSegments(new THREE.BufferGeometry(), lineMaterial);
  neighborLines.frustumCulled = false;
  neighborLines.renderOrder = 2;

  const focusMaterial = new THREE.PointsMaterial({
    color: PAPER,
    size: 1.35,
    map: texture,
    transparent: true,
    depthWrite: false,
    sizeAttenuation: true,
  });
  const focusGeom = new THREE.BufferGeometry();
  focusGeom.setAttribute("position", new THREE.Float32BufferAttribute([0, 0, 0], 3));
  const focusPointMesh = new THREE.Points(focusGeom, focusMaterial);
  focusPointMesh.frustumCulled = false;
  focusPointMesh.renderOrder = 3;
  focusPointMesh.visible = false;
  focusPointMesh.raycast = () => undefined;

  scene.add(hull2, hull3, halo, points, neighborLines, focusPointMesh);

  const controls = new OrbitControls(camera, canvas);
  controls.enableDamping = false;
  controls.zoomToCursor = true;
  controls.minDistance = 3;
  controls.maxDistance = 80;

  const labelEls = new Map<number, HTMLDivElement>();
  for (const cluster of cloud.clusters) {
    const label = document.createElement("div");
    label.className = "cluster-label";
    label.textContent = cluster.name;
    label.style.color = cluster.color;
    overlay.append(label);
    labelEls.set(cluster.id, label);
  }
  const focusLabel = document.createElement("div");
  focusLabel.className = "focus-label";
  const hoverLabel = document.createElement("div");
  hoverLabel.className = "hover-label";
  hoverLabel.style.visibility = "hidden";
  overlay.append(focusLabel, hoverLabel);

  const glyphs: GlyphLabel[] = new Array(count);
  for (const word of cloud.words) glyphs[word.id] = wordGlyph(word.id, word.simplified, 0, 0);
  const candidates: GlyphLabel[] = [];
  const shownGlyphs: GlyphLabel[] = [];
  const wordEls: HTMLDivElement[] = [];
  const wordElById = new Map<number, HTMLDivElement>();
  const focusObstacle: GlyphLabel = { id: -1, x: 0, y: 0, halfW: 0, halfH: 0 };
  const obstacles: GlyphLabel[] = [];
  const clusterBoxSize = new Map<number, { halfW: number; halfH: number }>();
  let shownWordIds = new Set<number>();
  let viewStamp = "";
  let hiddenCaption: HTMLDivElement | null = null;

  let state: CloudViewState = { mode: "3d", focusId: null, activeCluster: null, resetToken: -1 };
  let labelMode: Mode = state.mode;
  let labelCluster: number | null = state.activeCluster;
  let animating = false;
  let animStart = 0;
  const fromTarget = new THREE.Vector3();
  const toTarget = new THREE.Vector3();
  const fromPos = new THREE.Vector3();
  const toPos = new THREE.Vector3();
  let fromZoom = 1;
  let toZoom = 1;
  let writing = false;

  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const scratch = new THREE.Vector3();
  const scratchB = new THREE.Vector3();
  const ahead = new THREE.Vector3();
  let hovered: number | null = null;
  let pointerInside = false;
  let pointerButtons = 0;
  let lastClientX = 0;
  let lastClientY = 0;
  let pointerDownX = 0;
  let pointerDownY = 0;

  function activeArray(): Float32Array {
    return state.mode === "2d" ? positions2 : positions3;
  }

  function wordVector(id: number, target: THREE.Vector3): THREE.Vector3 {
    const word = cloud.words[id];
    if (!word) return target.set(0, 0, 0);
    const [x, y, z] = focusPoint(word, state.mode);
    return target.set(x, y, z);
  }

  function fitDistance(): number {
    const aspect = canvas.clientWidth / Math.max(canvas.clientHeight, 1);
    const fov = THREE.MathUtils.degToRad(perspective.fov);
    const radius = state.mode === "2d" ? radius2 : radius3;
    return (radius * 1.22) / Math.tan(fov / 2) / Math.min(aspect, 1);
  }

  function resize(): void {
    const width = canvas.clientWidth || 1;
    const height = canvas.clientHeight || 1;
    renderer.setSize(width, height, false);
    perspective.aspect = width / height;
    perspective.updateProjectionMatrix();
    const half = radius2 * 1.16;
    const aspect = width / height;
    ortho.top = half;
    ortho.bottom = -half;
    ortho.left = -half * aspect;
    ortho.right = half * aspect;
    ortho.updateProjectionMatrix();
  }

  function paintColors(): void {
    const dim = state.activeCluster;
    for (let index = 0; index < count; index += 1) {
      const cluster = cloud.words[index]!.cluster;
      const rgb = palette[cluster] ?? [1, 1, 1];
      const scale = dim == null || cluster === dim ? 1 : 0.32;
      colors[index * 3] = rgb[0] * scale;
      colors[index * 3 + 1] = rgb[1] * scale;
      colors[index * 3 + 2] = rgb[2] * scale;
    }
    colorAttr.needsUpdate = true;
    for (const material of hullMaterials) {
      material.opacity = dim == null ? (state.mode === "2d" ? 0.16 : 0.03) : 0.02;
    }
    if (dim != null) {
      const disk = hull2.children[dim] as THREE.Mesh | undefined;
      const sphere = hull3.children[dim] as THREE.Mesh | undefined;
      const diskMaterial = disk?.material;
      const sphereMaterial = sphere?.material;
      if (diskMaterial instanceof THREE.MeshBasicMaterial) diskMaterial.opacity = 0.3;
      if (sphereMaterial instanceof THREE.MeshBasicMaterial) sphereMaterial.opacity = 0.07;
    }
  }

  function setSegments(lines: THREE.LineSegments, pairs: THREE.Vector3[]): void {
    const array = new Float32Array(pairs.length * 3);
    pairs.forEach((point, index) => {
      array[index * 3] = point.x;
      array[index * 3 + 1] = point.y;
      array[index * 3 + 2] = point.z;
    });
    lines.geometry.setAttribute("position", new THREE.BufferAttribute(array, 3));
    lines.visible = pairs.length > 0;
  }

  function syncDecorations(): void {
    const focusId = state.focusId;
    const focusWord = focusId == null ? undefined : cloud.words[focusId];
    focusPointMesh.visible = focusWord != null;
    focusLabel.hidden = focusWord == null;
    if (focusWord) {
      const position = wordVector(focusId!, scratch);
      const attribute = focusGeom.getAttribute("position");
      attribute.setXYZ(0, position.x, position.y, position.z);
      attribute.needsUpdate = true;
      focusLabel.replaceChildren();
      const strong = document.createElement("strong");
      strong.textContent = focusWord.simplified;
      focusLabel.append(strong);
      const pairs: THREE.Vector3[] = [];
      for (const neighborId of focusWord.neighbors) {
        pairs.push(position.clone(), wordVector(neighborId, new THREE.Vector3()));
      }
      setSegments(neighborLines, pairs);
    } else {
      setSegments(neighborLines, []);
    }
  }

  function applyMode(mode: Mode): void {
    camera = mode === "2d" ? ortho : perspective;
    controls.object = camera;
    controls.enableRotate = mode === "3d";
    controls.zoomToCursor = mode === "2d";
    controls.mouseButtons.LEFT = mode === "2d" ? THREE.MOUSE.PAN : THREE.MOUSE.ROTATE;
    controls.touches.ONE = mode === "2d" ? THREE.TOUCH.PAN : THREE.TOUCH.ROTATE;
    controls.screenSpacePanning = mode === "2d";
    const positions = new THREE.BufferAttribute(activeArray(), 3);
    points.geometry.setAttribute("position", positions);
    halo.geometry.setAttribute("position", positions);
    pointsMaterial.size = mode === "2d" ? 8 : 0.46;
    pointsMaterial.sizeAttenuation = mode === "3d";
    pointsMaterial.depthTest = mode === "3d";
    haloMaterial.size = mode === "2d" ? 18 : 1.25;
    haloMaterial.sizeAttenuation = mode === "3d";
    focusMaterial.size = mode === "2d" ? 22 : 1.45;
    focusMaterial.sizeAttenuation = mode === "3d";
    hull2.visible = mode === "2d";
    hull3.visible = mode === "3d";
    resize();
  }

  function beginAnimation(): void {
    const mode = state.mode;
    const fit = fitDistance();
    controls.maxDistance = fit * 1.8;
    const target = new THREE.Vector3();
    if (state.focusId != null) wordVector(state.focusId, target);
    else if (state.activeCluster != null) {
      const [x, y, z] = clusterCentroid(cloud, state.activeCluster, mode);
      target.set(x, y, z);
    }
    fromTarget.copy(controls.target);
    fromPos.copy(camera.position);
    fromZoom = camera.zoom;
    toTarget.copy(target);

    if (mode === "3d") {
      const offset = camera.position.clone().sub(controls.target);
      if (offset.lengthSq() < 0.25) offset.set(0.35, 0.48, 1);
      let distance = offset.length();
      const wide = distance > fit * 0.82 || state.focusId == null && state.activeCluster == null;
      if (state.focusId != null && wide) distance = fit * 0.4;
      else if (state.activeCluster != null && state.focusId == null && wide) distance = fit * 0.78;
      else if (state.focusId == null && state.activeCluster == null) distance = fit;
      else distance = Math.min(Math.max(distance, 4), fit);
      offset.setLength(distance);
      toPos.copy(target).add(offset);
      toZoom = 1;
    } else {
      toPos.set(target.x, target.y, 40);
      if (state.focusId != null) toZoom = Math.max(camera.zoom, 2.5);
      else if (state.activeCluster != null) toZoom = Math.max(camera.zoom, 1.65);
      else toZoom = 1;
    }
    animating = true;
    animStart = performance.now();
  }

  function projectPoint(point: THREE.Vector3): { x: number; y: number; depth: number; visible: boolean } {
    const depth = scratchB.copy(point).sub(camera.position).dot(ahead);
    const width = canvas.clientWidth || 1;
    const height = canvas.clientHeight || 1;
    const ndc = scratchB.copy(point).project(camera);
    const x = (ndc.x * 0.5 + 0.5) * width;
    const y = (-ndc.y * 0.5 + 0.5) * height;
    return { x, y, depth, visible: depth > 0 && ndc.z < 1 && Number.isFinite(x) && Number.isFinite(y) };
  }

  function projectLabel(point: THREE.Vector3, element: HTMLElement, lift: number): { x: number; y: number; visible: boolean } {
    const placed = projectPoint(point);
    element.style.transform = `translate(${placed.x}px, ${placed.y + lift}px) translate(-50%, -50%)`;
    return placed;
  }

  function pointRadiusPx(depth: number): number {
    if (!pointsMaterial.sizeAttenuation) return pointsMaterial.size * 0.5;
    const height = canvas.clientHeight || 1;
    return (pointsMaterial.size * height * 0.25) / Math.max(depth, 0.05);
  }

  function takeWordLabel(index: number): HTMLDivElement {
    const existing = wordEls[index];
    if (existing) return existing;
    const element = document.createElement("div");
    element.className = "word-label";
    element.setAttribute("aria-hidden", "true");
    overlay.insertBefore(element, focusLabel);
    wordEls[index] = element;
    return element;
  }

  function viewKey(): string {
    const position = camera.position;
    const target = controls.target;
    return [
      state.mode,
      state.focusId,
      state.activeCluster,
      camera.zoom.toFixed(3),
      position.x.toFixed(3),
      position.y.toFixed(3),
      position.z.toFixed(3),
      target.x.toFixed(3),
      target.y.toFixed(3),
      target.z.toFixed(3),
      canvas.clientWidth,
      canvas.clientHeight,
    ].join("|");
  }

  function frameLabels(): void {
    camera.getWorldDirection(ahead);
    const stamp = viewKey();
    if (stamp === viewStamp) {
      placePointerLabels();
      return;
    }
    viewStamp = stamp;
    const width = canvas.clientWidth || 1;
    const height = canvas.clientHeight || 1;
    if (labelMode !== state.mode || labelCluster !== state.activeCluster) {
      shownWordIds = new Set();
      labelMode = state.mode;
      labelCluster = state.activeCluster;
    }

    candidates.length = 0;
    obstacles.length = 0;
    const focusWord = state.focusId == null ? undefined : cloud.words[state.focusId];
    if (focusWord && state.focusId != null) {
      const placed = projectPoint(wordVector(state.focusId, scratch));
      if (placed.visible) {
        focusObstacle.x = placed.x;
        focusObstacle.y = placed.y - 22;
        focusObstacle.halfW = focusWord.simplified.length * 14;
        focusObstacle.halfH = 16;
        obstacles.push(focusObstacle);
      }
    }

    const pad = 220;
    for (let id = 0; id < count; id += 1) {
      if (id === state.focusId) continue;
      const word = cloud.words[id];
      const glyph = glyphs[id];
      if (!word || !glyph) continue;
      if (state.activeCluster != null && word.cluster !== state.activeCluster) continue;
      const placed = projectPoint(wordVector(id, scratch));
      if (!placed.visible) continue;
      if (placed.x < -pad || placed.y < -pad || placed.x > width + pad || placed.y > height + pad) continue;
      glyph.x = placed.x;
      glyph.y = placed.y - pointRadiusPx(placed.depth) - 3 - glyph.halfH;
      candidates.push(glyph);
    }

    const visibleIds = separatedLabels(candidates, WORD_LABEL_GAP, shownWordIds, obstacles);
    shownWordIds = visibleIds;
    shownGlyphs.length = 0;
    let slot = 0;
    wordElById.clear();
    for (const glyph of candidates) {
      if (!visibleIds.has(glyph.id)) continue;
      shownGlyphs.push(glyph);
      if (glyph.x + glyph.halfW < 0 || glyph.y + glyph.halfH < 0 || glyph.x - glyph.halfW > width || glyph.y - glyph.halfH > height) {
        continue;
      }
      const element = takeWordLabel(slot);
      slot += 1;
      if (element.dataset.id !== String(glyph.id)) {
        element.dataset.id = String(glyph.id);
        element.textContent = cloud.words[glyph.id]?.simplified ?? "";
      }
      element.style.visibility = "visible";
      element.style.transform = `translate(${glyph.x}px, ${glyph.y}px) translate(-50%, -50%)`;
      wordElById.set(glyph.id, element);
    }
    for (let index = slot; index < wordEls.length; index += 1) wordEls[index]!.style.visibility = "hidden";
    hiddenCaption = null;

    const boxes: { id: number; x: number; y: number; size: number; element: HTMLDivElement }[] = [];
    for (const cluster of cloud.clusters) {
      const element = labelEls.get(cluster.id);
      if (!element) continue;
      const [x, y, z] = clusterCentroid(cloud, cluster.id, state.mode);
      const placed = projectLabel(scratch.set(x, y, z), element, 0);
      const dimmed = state.activeCluster != null && state.activeCluster !== cluster.id;
      element.style.opacity = dimmed ? "0.28" : "1";
      if (!placed.visible) {
        element.style.visibility = "hidden";
        continue;
      }
      boxes.push({ id: cluster.id, x: placed.x, y: placed.y, size: cluster.size, element });
    }
    const shown = visibleLabels(boxes, 58, state.activeCluster ?? undefined);
    for (const box of boxes) {
      let size = clusterBoxSize.get(box.id);
      if (!size) {
        const name = cloud.clusters[box.id]?.name ?? "";
        const widthPx = box.element.offsetWidth || name.length * 8;
        const heightPx = box.element.offsetHeight || 16;
        size = { halfW: widthPx / 2, halfH: heightPx / 2 };
        if (box.element.offsetWidth > 0) clusterBoxSize.set(box.id, size);
      }
      const clusterBox: GlyphLabel = { id: box.id, x: box.x, y: box.y, halfW: size.halfW, halfH: size.halfH };
      const covered = shownGlyphs.some((glyph) => boxesOverlap(clusterBox, glyph, 4));
      box.element.style.visibility = shown.has(box.id) && !covered ? "visible" : "hidden";
    }

    placePointerLabels();
  }

  function placePointerLabels(): void {
    if (hiddenCaption) {
      hiddenCaption.style.visibility = "visible";
      hiddenCaption = null;
    }
    if (state.focusId != null) {
      wordVector(state.focusId, scratch);
      projectLabel(scratch, focusLabel, -22);
      focusLabel.style.visibility = "visible";
    }
    if (hovered != null && hovered !== state.focusId) {
      const word = cloud.words[hovered];
      if (word) {
        hoverLabel.replaceChildren();
        const strong = document.createElement("strong");
        strong.textContent = word.simplified;
        const meta = document.createElement("span");
        const gloss = glossOf(word);
        meta.textContent = gloss ? `${word.pinyin} · ${gloss}` : word.pinyin;
        hoverLabel.append(strong, meta);
        wordVector(hovered, scratch);
        const placed = projectLabel(scratch, hoverLabel, -28);
        const rect = canvas.getBoundingClientRect();
        const near = Math.hypot(placed.x - (lastClientX - rect.left), placed.y - (lastClientY - rect.top)) < 24;
        hoverLabel.style.visibility = near && placed.visible ? "visible" : "hidden";
        if (!near) hovered = null;
      }
    } else {
      hoverLabel.style.visibility = "hidden";
    }
    if (hovered != null && hoverLabel.style.visibility === "visible") {
      const caption = wordElById.get(hovered);
      if (caption) {
        caption.style.visibility = "hidden";
        hiddenCaption = caption;
      }
    }
  }

  function pick(event: PointerEvent): number | null {
    const rect = canvas.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return null;
    pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(pointer, camera);
    const zoom = camera instanceof THREE.OrthographicCamera ? camera.zoom : 1;
    const distance = camera.position.distanceTo(controls.target);
    raycaster.params.Points.threshold = state.mode === "2d" ? 0.4 / Math.max(zoom, 0.4) : Math.max(0.22, distance * 0.012);
    camera.updateMatrixWorld();
    const hits = raycaster.intersectObject(points, false);
    let bestIndex: number | null = null;
    let bestPixels = 18;
    for (const hit of hits) {
      if (hit.index == null) continue;
      const word = cloud.words[hit.index];
      if (!word) continue;
      const [x, y, z] = focusPoint(word, state.mode);
      const ndc = scratchB.set(x, y, z).project(camera);
      const screenX = (ndc.x * 0.5 + 0.5) * rect.width + rect.left;
      const screenY = (-ndc.y * 0.5 + 0.5) * rect.height + rect.top;
      const pixels = Math.hypot(screenX - event.clientX, screenY - event.clientY);
      if (pixels < bestPixels) {
        bestPixels = pixels;
        bestIndex = hit.index;
      }
    }
    return bestIndex;
  }

  function refreshHover(): void {
    const under = document.elementFromPoint(lastClientX, lastClientY);
    const overCloud = under === canvas || (under != null && overlay.contains(under));
    if (!overCloud || pointerButtons !== 0) {
      hovered = null;
      canvas.style.cursor = "grab";
      return;
    }
    hovered = pick({ clientX: lastClientX, clientY: lastClientY } as PointerEvent);
    canvas.style.cursor = hovered == null ? "grab" : "pointer";
  }

  function onPointerMove(event: PointerEvent): void {
    pointerInside = true;
    pointerButtons = event.buttons;
    lastClientX = event.clientX;
    lastClientY = event.clientY;
    refreshHover();
  }

  function onPointerDown(event: PointerEvent): void {
    pointerDownX = event.clientX;
    pointerDownY = event.clientY;
    pointerButtons = event.buttons;
    lastClientX = event.clientX;
    lastClientY = event.clientY;
    refreshHover();
  }

  function onPointerUp(event: PointerEvent): void {
    pointerButtons = 0;
    lastClientX = event.clientX;
    lastClientY = event.clientY;
    refreshHover();
    if (Math.hypot(event.clientX - pointerDownX, event.clientY - pointerDownY) > 5) return;
    const id = pick(event);
    if (id != null) onPick(id);
  }

  function onPointerLeave(): void {
    pointerInside = false;
    pointerButtons = 0;
    hovered = null;
    canvas.style.cursor = "grab";
  }

  window.addEventListener("pointermove", onPointerMove);
  canvas.addEventListener("pointerdown", onPointerDown);
  window.addEventListener("pointerup", onPointerUp);
  canvas.addEventListener("pointerleave", onPointerLeave);
  controls.addEventListener("change", () => {
    if (!writing) animating = false;
  });

  const resizeObserver = new ResizeObserver(() => resize());
  resizeObserver.observe(canvas);
  applyMode("3d");
  paintColors();

  let raf = 0;
  function tick(now: number): void {
    raf = requestAnimationFrame(tick);
    if (animating) {
      const duration = reduced ? 0 : 900;
      const t = duration === 0 ? 1 : Math.min(1, (now - animStart) / duration);
      const eased = 1 - (1 - t) ** 3;
      writing = true;
      controls.target.lerpVectors(fromTarget, toTarget, eased);
      camera.position.lerpVectors(fromPos, toPos, eased);
      if (camera instanceof THREE.OrthographicCamera) {
        camera.zoom = fromZoom + (toZoom - fromZoom) * eased;
        camera.updateProjectionMatrix();
      }
      controls.update();
      writing = false;
      if (t >= 1) animating = false;
    } else {
      writing = true;
      controls.update();
      writing = false;
    }
    refreshHover();
    frameLabels();
    renderer.render(scene, camera);
  }
  raf = requestAnimationFrame(tick);

  return {
    setState(next) {
      const modeChanged = next.mode !== state.mode;
      const focusChanged = next.focusId !== state.focusId;
      const clusterChanged = next.activeCluster !== state.activeCluster;
      const resetChanged = next.resetToken !== state.resetToken;
      state = next;
      if (modeChanged) applyMode(next.mode);
      if (clusterChanged || modeChanged) paintColors();
      if (focusChanged || clusterChanged || modeChanged) syncDecorations();
      if (modeChanged || focusChanged || clusterChanged || resetChanged) beginAnimation();
    },
    dispose() {
      cancelAnimationFrame(raf);
      resizeObserver.disconnect();
      window.removeEventListener("pointermove", onPointerMove);
      canvas.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("pointerup", onPointerUp);
      canvas.removeEventListener("pointerleave", onPointerLeave);
      controls.dispose();
      points.geometry.dispose();
      halo.geometry.dispose();
      focusGeom.dispose();
      neighborLines.geometry.dispose();
      pointsMaterial.dispose();
      haloMaterial.dispose();
      focusMaterial.dispose();
      lineMaterial.dispose();
      texture.dispose();
      for (const material of hullMaterials) material.dispose();
      hull2.traverse((node) => {
        if (node instanceof THREE.Mesh) node.geometry.dispose();
      });
      hull3.traverse((node) => {
        if (node instanceof THREE.Mesh) node.geometry.dispose();
      });
      renderer.dispose();
      overlay.replaceChildren();
    },
  };
}
