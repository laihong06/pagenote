// Trusted renderer code lives in PageNote. Only serializable state lives in .md.
async function buildChromosome(host, getState, commit, isCurrent = () => true) {
  const THREE = await import('three');
  if (!host.isConnected || !isCurrent()) return null;
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.setSize(Math.max(200, host.clientWidth), host.clientHeight || 360);
  renderer.setClearColor('#f7f3e8');
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  host.append(renderer.domElement);
  const scene = new THREE.Scene(),
    camera = new THREE.PerspectiveCamera(
      40,
      Math.max(200, host.clientWidth) / (host.clientHeight || 360),
      0.1,
      100,
    );
  camera.position.z = 5.6;
  scene.add(new THREE.HemisphereLight(0xfff8e8, 0x696c5e, 2.5));
  const light = new THREE.DirectionalLight(0xffffff, 3);
  light.position.set(3, 4, 5);
  light.castShadow = true;
  light.shadow.mapSize.set(1024, 1024);
  scene.add(light);
  const group = new THREE.Group();
  scene.add(group);
  const pickable = [],
    rings = [],
    labels = [];
  const bitTextures = [0, 1].map((bit) => {
    const canvas = document.createElement('canvas');
    canvas.width = 64;
    canvas.height = 64;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#f0ecdf';
    ctx.beginPath();
    ctx.arc(32, 32, 29, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#293028';
    ctx.font = 'bold 38px Georgia';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(String(bit), 32, 33);
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  });
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(8, 8),
    new THREE.ShadowMaterial({ opacity: 0.13 }),
  );
  ground.rotation.x = -Math.PI / 2;
  ground.position.y = -1.68;
  ground.receiveShadow = true;
  scene.add(ground);
  const genes = getState().genes;
  for (let arm = 0; arm < 2; arm++) {
    const sign = arm ? 1 : -1;
    const curve = new THREE.CatmullRomCurve3([
      new THREE.Vector3(sign * 0.8, 1.4, 0),
      new THREE.Vector3(sign * 0.5, 0.7, 0.08),
      new THREE.Vector3(0, 0, arm * 0.15),
      new THREE.Vector3(-sign * 0.55, -0.8, 0),
      new THREE.Vector3(-sign * 0.8, -1.4, 0),
    ]);
    const stem = new THREE.Mesh(
      new THREE.TubeGeometry(curve, 48, 0.16, 12, false),
      new THREE.MeshStandardMaterial({ color: '#d9dcc5', roughness: 0.85 }),
    );
    group.add(stem);
    stem.castShadow = true;
    for (const t of [0, 1]) {
      const cap = new THREE.Mesh(
        new THREE.SphereGeometry(0.16, 16, 12),
        stem.material,
      );
      cap.position.copy(curve.getPoint(t));
      cap.castShadow = true;
      group.add(cap);
    }
    const count = Math.ceil(genes.length / 2);
    for (let j = 0; j < count; j++) {
      const index = arm * count + j;
      if (index >= genes.length) continue;
      const t = 0.12 + (0.76 * j) / Math.max(1, count - 1),
        position = curve.getPoint(t);
      const ring = new THREE.Mesh(
        new THREE.TorusGeometry(0.19, 0.045, 8, 24),
        new THREE.MeshStandardMaterial({
          color: genes[index] ? '#a44c34' : '#376d70',
          roughness: 0.55,
        }),
      );
      ring.position.copy(position);
      ring.quaternion.setFromUnitVectors(
        new THREE.Vector3(0, 0, 1),
        curve.getTangent(t),
      );
      ring.userData.gene = index;
      group.add(ring);
      ring.castShadow = true;
      pickable.push(ring);
      rings.push(ring);
      const bead = new THREE.Mesh(
        new THREE.SphereGeometry(0.085, 12, 8),
        ring.material,
      );
      bead.position.copy(position).add(new THREE.Vector3(0.19, 0, 0.08));
      bead.userData.gene = index;
      group.add(bead);
      pickable.push(bead);
      const label = new THREE.Sprite(
        new THREE.SpriteMaterial({
          map: bitTextures[genes[index]],
          transparent: true,
          depthWrite: false,
        }),
      );
      label.position.copy(bead.position).add(new THREE.Vector3(0, 0, 0.12));
      label.scale.set(0.18, 0.18, 1);
      label.userData.gene = index;
      group.add(label);
      labels.push(label);
      pickable.push(label);
    }
  }
  let disposed = false,
    visible = true,
    lost = false,
    suspended = false,
    drag = false,
    startX = 0,
    lastX = 0,
    moved = false,
    raf;
  group.rotation.y = Number.isFinite(getState().angle)
    ? getState().angle
    : 0.15;
  const draw = () => {
    if (!disposed && !lost) renderer.render(scene, camera);
  };
  const frame = () => {
    cancelAnimationFrame(raf);
    if (disposed || lost || !visible || document.hidden || suspended) {
      host.dataset.renderState = disposed
        ? 'disposed'
        : lost
          ? 'lost'
          : !visible
            ? 'offscreen'
            : 'background';
      return;
    }
    host.dataset.renderState =
      getState().paused === false ? 'running' : 'paused';
    draw();
    if (
      getState().paused === false &&
      !matchMedia('(prefers-reduced-motion:reduce)').matches
    )
      raf = requestAnimationFrame(() => {
        if (!drag) group.rotation.y += 0.006;
        frame();
      });
  };
  renderer.domElement.onpointerdown = (e) => {
    drag = true;
    moved = false;
    startX = lastX = e.clientX;
    renderer.domElement.setPointerCapture(e.pointerId);
  };
  renderer.domElement.onpointermove = (e) => {
    if (!drag) return;
    moved ||= Math.abs(e.clientX - startX) > 4;
    group.rotation.y += (e.clientX - lastX) * 0.012;
    lastX = e.clientX;
    draw();
  };
  renderer.domElement.onpointerup = (e) => {
    drag = false;
    const state = getState();
    if (!moved) {
      const box = renderer.domElement.getBoundingClientRect(),
        pointer = new THREE.Vector2(
          ((e.clientX - box.left) / box.width) * 2 - 1,
          (-(e.clientY - box.top) / box.height) * 2 + 1,
        ),
        ray = new THREE.Raycaster();
      ray.setFromCamera(pointer, camera);
      const hit = ray.intersectObjects(pickable)[0];
      if (hit) {
        const genes = [...state.genes];
        genes[hit.object.userData.gene] ^= 1;
        commit({ ...state, genes, angle: group.rotation.y });
        return;
      }
    }
    commit({ ...state, angle: group.rotation.y });
  };
  renderer.domElement.addEventListener('webglcontextlost', (event) => {
    if (disposed) return;
    event.preventDefault();
    lost = true;
    cancelAnimationFrame(raf);
    host.dataset.gpu = 'lost';
    const status = document.createElement('p');
    status.textContent =
      '图形上下文已丢失。Markdown 数据仍保留，可重新打开笔记恢复。';
    host.append(status);
  });
  const observer = new ResizeObserver(() => {
    if (disposed) return;
    const width = Math.max(200, host.clientWidth);
    const height = host.clientHeight || 360;
    renderer.setSize(width, height);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    frame();
  });
  observer.observe(host);
  const visibility = () => {
    if (!document.hidden) suspended = false;
    frame();
  };
  const suspend = () => {
    suspended = true;
    frame();
  };
  const resume = () => {
    suspended = false;
    frame();
  };
  window.addEventListener('pagenote-render-suspend', suspend);
  window.addEventListener('focus', resume);
  document.addEventListener('visibilitychange', visibility);
  const intersection = new IntersectionObserver((entries) => {
    visible = entries[0]?.isIntersecting ?? false;
    frame();
  });
  intersection.observe(host);
  frame();
  return {
    update() {
      for (const ring of rings)
        ring.material.color.set(
          getState().genes[ring.userData.gene] ? '#a44c34' : '#376d70',
        );
      for (const label of labels) {
        label.material.map = bitTextures[getState().genes[label.userData.gene]];
        label.material.needsUpdate = true;
      }
      frame();
    },
    angle: () => group.rotation.y,
    dispose() {
      disposed = true;
      cancelAnimationFrame(raf);
      observer.disconnect();
      intersection.disconnect();
      document.removeEventListener('visibilitychange', visibility);
      window.removeEventListener('pagenote-render-suspend', suspend);
      window.removeEventListener('focus', resume);
      scene.traverse((node) => {
        node.geometry?.dispose();
        node.material?.dispose();
      });
      renderer.dispose();
      bitTextures.forEach((texture) => texture.dispose());
      renderer.forceContextLoss();
      renderer.domElement.remove();
    },
  };
}

export async function createNativeChromosome(host, getState, commit) {
  let current,
    disposed = false,
    visible = false,
    generation = 0;
  const start = () => {
    const epoch = ++generation;
    if (disposed || !visible || !host.isConnected) return;
    host.replaceChildren();
    host.dataset.renderState = 'loading';
    buildChromosome(
      host,
      getState,
      commit,
      () => !disposed && visible && epoch === generation,
    )
      .then((handle) => {
        if (disposed || !visible || epoch !== generation) {
          handle?.dispose();
          return;
        }
        current = handle;
      })
      .catch((error) => {
        if (!disposed && epoch === generation) {
          host.textContent = '当前设备无法绘制三维视图：' + error.message;
          host.dataset.renderState = 'error';
        }
      });
  };
  const observer = new IntersectionObserver((entries) => {
    const next = entries[0]?.isIntersecting ?? false;
    if (next === visible) return;
    visible = next;
    if (next) start();
    else {
      generation++;
      current?.dispose();
      current = null;
      host.replaceChildren();
      host.dataset.renderState = 'offscreen';
    }
  });
  observer.observe(host);
  return {
    update: () => current?.update(),
    angle: () => current?.angle() ?? getState().angle ?? 0.15,
    dispose() {
      disposed = true;
      generation++;
      observer.disconnect();
      current?.dispose();
      current = null;
      host.replaceChildren();
    },
  };
}
