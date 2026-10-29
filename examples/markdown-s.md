# Markdown S：把过程画出来

这是一份离线学习演示。点击每张卡片的“运行”查看，点击“停止”释放资源；源码可以展开查看。编辑模式下双击卡片标题修改源码。

## 信息素如何引导搜索

$$p(i,j)\propto\tau_{ij}^{\alpha}\eta_{ij}^{\beta}$$

```mermaid
flowchart LR
 A[选择城市] --> B[完成路线] --> C[挥发与强化] --> A
```

## 交互式解释

```mds-html
<style>
body{background:#f5f8ff;color:#16254b}section{max-width:520px;margin:30px auto}h1{font-size:32px}input{width:100%;accent-color:#4f79ed}.trail{height:10px;background:linear-gradient(90deg,#4f79ed,#9ddcd1);border-radius:20px;transition:width .3s}output{font-size:38px;color:#4f79ed}
</style>
<section><p>ANT COLONY · 学习实验</p><h1>信息素越强，路线越容易被选择</h1><p>拖动滑块，观察强化程度。</p><input id="amount" aria-label="信息素强度" type="range" min="10" max="100" value="55"><p><output>55%</output></p><div class="trail" style="width:55%"></div></section>
<script>amount.oninput=()=>{document.querySelector('output').textContent=amount.value+'%';document.querySelector('.trail').style.width=amount.value+'%'}</script>
```

## 迭代结果

```mds-chart
{"type":"line","title":"示例路线长度（合成数据）","labels":["初始","第10轮","第20轮","第30轮"],"values":[160,125,102,91]}
```

## 空间关系

```mds-three
<style>body{margin:0;background:#0e1830;color:#dce7ff}p{position:absolute;left:20px;top:0}canvas{display:block}</style>
<p>离线 Three.js · 旋转立方体</p>
<script>
try {
 const renderer=new THREE.WebGLRenderer({antialias:true});
 renderer.setSize(innerWidth,380);document.body.append(renderer.domElement);
 const scene=new THREE.Scene();scene.background=new THREE.Color('#0e1830');
 const camera=new THREE.PerspectiveCamera(50,innerWidth/380,.1,100);camera.position.z=4;
 const cube=new THREE.Mesh(new THREE.BoxGeometry(),new THREE.MeshNormalMaterial());scene.add(cube);
 const reduced=matchMedia('(prefers-reduced-motion:reduce)').matches;
 function draw(t){if(!reduced){cube.rotation.x=t*.0003;cube.rotation.y=t*.0005}renderer.render(scene,camera);if(!reduced)requestAnimationFrame(draw)}
 draw(0);
} catch(e){document.body.append(document.createTextNode('当前设备无法运行 WebGL：'+e.message))}
</script>
```

以上数据和图形用于解释渲染能力，不是实际算法实验结果。旧版编辑器会把增强块作为代码展示，保存前应确认兼容性。
