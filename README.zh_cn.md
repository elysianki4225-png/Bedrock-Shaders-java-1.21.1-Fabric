# Bedrock Shaders —— Minecraft Java 版 1.21.1（Fabric · Quilt · Forge · NeoForge）

[English](README.md) · **简体中文**

把 Minecraft **基岩版（Bedrock Edition）延迟渲染管线（Deferred Rendering Pipeline）** 的观感
带到 **Java 版 1.21.1**，四个主流加载器都支持：基岩版那套数据驱动的光照 / 大气 / 雾 /
调色系统、PBR 风格水面、基岩版天空散射，以及一条后处理链（SSAO、泛光、体积光、色调映射）。

## 下载

每个加载器都有对应的 jar，文件名写明了它属于哪个加载器。

| 加载器 | 下载 | 前置要求 | 状态 |
| --- | --- | --- | --- |
| **Fabric** | [**bedrock-shaders-fabric-1.0.0.jar**](dist/bedrock-shaders-fabric-1.0.0.jar) | Fabric Loader ≥ 0.16 + Fabric API ≥ 0.100 | **主要支持** |
| **Quilt** | [**直接使用上面同一个 Fabric jar**](dist/bedrock-shaders-fabric-1.0.0.jar)（没有单独文件） | Quilt Loader ≥ 0.30 + Fabric API ≥ 0.100 | **主要支持** |
| Forge | [bedrock-shaders-forge-1.0.0.jar](dist/bedrock-shaders-forge-1.0.0.jar) | Forge 52.0.28（1.21.1） | 维护中，次要 |
| NeoForge | [bedrock-shaders-neoforge-1.0.0.jar](dist/bedrock-shaders-neoforge-1.0.0.jar) | NeoForge 21.1.119（1.21.1） | 维护中，次要 |

源码 jar：[bedrock-shaders-fabric-1.0.0-sources.jar](dist/bedrock-shaders-fabric-1.0.0-sources.jar)

**这里刻意不再提供 `-quilt` 版。** Quilt Loader 0.30+ 原生加载 Fabric 模组，所以 Fabric 那个
jar 就是 Quilt 的下载。之前多打一个只是换了元数据的 jar，等于有两份东西要测、两处版本号可能
对不上 —— 而且它确实一度带着字面量 `${version}` 的 `quilt.mod.json`，Quilt Loader 会直接拒绝
加载。现在只有一个产物，只有一件事要做好。

**Fabric（以及走 Fabric 版的 Quilt）是主要优化目标。** Forge / NeoForge 会同步编译更新，但
目前在 CI 中以非阻塞方式构建（打包环节仍在收尾），所以单独一个加载器失败不会把整个分支标红。

## 安装

1. 为 Minecraft 1.21.1 装上你的加载器（Fabric / Quilt 还需要 Fabric API：Quilt Loader 原生
   支持加载 Fabric 模组，而 1.21.1 没有 Quilted Fabric API 发布）。
2. 把 `dist/` 里对应的那个 jar 放进 `.minecraft/mods/`。
3. 启动游戏。模组会自动生成运行时着色器包到 `config/bedrock-shaders/pack` 并自动启用。

界面语言：English 与简体中文（`assets/bedrock_shaders/lang/{en_us,zh_cn}.json`，键集合完全一致）。

| 按键 | 作用 |
| --- | --- |
| `F9` | 打开 Bedrock Shaders 设置界面 |
| `F10` | 开关渲染管线 |

命令：`/bedrock menu`、`/bedrock toggle`、`/bedrock preset <potato|low|balanced|high|ultra>`、
`/bedrock packs`、`/bedrock import`、`/bedrock reload`、`/bedrock fog`、`/bedrock effects`、
`/bedrock debug`。

## 和其他模组一起用

这个渲染器天生"动手比较狠"：它接管五个原版核心着色器程序，并用自己的后处理链写回主帧缓冲。
几个非常流行的模组会抢同一块地盘，所以模组在启动时就决定档位，而不是等出问题再说：

| 装了这些 | 档位 | 会发生什么 |
| --- | --- | --- |
| 没有相关模组 | **完整** | 全部功能运行 |
| **Sodium / Embeddium / Rubidium** | **完整** | 它们只替换区块渲染后端，对我们没有影响 |
| **锂（Lithium）、FerriteCore、ModernFix、ImmediatelyFast、EntityCulling、C2ME、Krypton、Starlight、MemoryLeakFix、BadOptimizations、MoreCulling** | **完整** | 它们根本不碰 GL 管线：不冲突，也不需要特殊处理 |
| **Iris / Oculus** | **让路** | Iris 自己拥有整个帧和那五个程序。本模组保留 `.mcpack` 导入、生成的资源包和设置界面，但不再绑定 uniform、不替换天空、不跑后处理 —— 既不会双重色调映射，也不会争抢帧缓冲 |
| **VulkanMod** | **禁用（无 GL）** | 那里根本没有 OpenGL，任何 GL 调用都会崩。模组照常加载、界面照常能开、导入照常可用，只是不渲染任何效果 |

这个判断在启动时根据已安装模组 id 一次性做出并写进日志，所以 `latest.log` 永远能看出当前是哪个
档位、为什么。`/bedrock debug` 也会显示。`verification/test_compat.py` 锁定了整张矩阵 —— 包括
像 Create 这种内容模组，或者 id 只是以 `iris`、`vulkan` 开头的无关模组，绝不会误触发降级。

## 导入基岩版光影包（.mcpack）

和 Iris 一样，模组首次启动时会在实例目录里建好文件夹：

```
.minecraft/bedrock-shaders/packs/      <- 把 .mcpack / .mcaddon / .zip 丢这里
```

然后重启、执行 `/bedrock import`，或者在包管理器里按 **Rescan folder**（`F9` → **Packs** →
*Bedrock Pack Manager*）。对每个压缩包，模组会：

1. 拆开嵌套压缩包（`.mcaddon` 里的 `.mcpack`、`.mcpack` 里再套的 `.mcpack`，最多递归 4 层）；
2. 只保留基岩版渲染数据 —— `*_fog_setting.json`、`*_lighting.json`、`*_atmospherics.json`、
   `*_color_grading.json` 与 `biomes_client.json`，丢弃贴图、模型、音效和清单文件；
3. 在 `.minecraft/resourcepacks/bedrock_shaders_<名字>/` 写出一份合法的 Java 资源包
   （自动生成 `pack.mcmeta`）；
4. 把它接到资源包栈里（**追加在最后**，所以你自己放在上面的包依然优先）并重载资源 —— 不需要重启。

### 针对知名基岩光影的定向调校

基岩版光影包**只有数据、没有代码**：里面装的是雾 / 光照 / 大气 / 调色的 JSON。所以在 Java 版里，
一个包不可能仅靠自身数据就复现它作者原本的观感 —— 必须由渲染器把那份意图还原出来。当导入的包
被识别为某个已知系列时，渲染器会在包自带数据之上叠加该系列的调校：

| 系列 | 识别到的名称 | 调校内容 |
| --- | --- | --- |
| **Newb** | Newb X Legacy、Golden Legacy、Glow Legacy、X LMI、Renewed、Reforged、Nostalgia、Dawn、Ale、Dragon Complementary | 雾延伸得更远、水下雾更浓更偏冷、阳光更暖（金色）、饱和度略提、对比度更柔和、泛光与焦散略增 —— 这就是 Newb 的标志性观感 |
| YSS | YSS、YSS Special Edition | 水面反射更清透、阳光略强 |
| Eternity | Eternity | 电影感：更重的泛光、更强的反射与体积光、更高对比度 |
| Solas / Harmonics | Solas、Harmonics、Harmony | 更干净、偏蓝调时刻、雾更轻 |
| 通用 | 其它所有包 | 保持模组自身忠于基岩版的默认值，不做改动 |

识别会同时读取压缩包文件名、包内清单里的名称和描述，所以 `Newb-X-Legacy-v1.2.mcpack`，以及一个
文件名随便但清单里写着 “Newb X LMI” 的包，都会归到 Newb 系列。识别结果会在包管理器里以标签显示，
`/bedrock debug` 也会输出，并且可以在 `Packs → 光影系列调校` 里整体关闭。

每个包的状态记录在 `bedrock-shaders/index.json`，所以没变过的包不会被重复导入，删掉的包会自动
注销。管理界面可以启用 / 停用、删除、打开文件夹，也能跳到原版自己的资源包界面。

**不支持的内容：** 二进制 RenderDragon 着色器（`*.material.bin`）—— 那是编译好的管线二进制，
在 Java 版里没有对应物。只导入数据驱动的延迟渲染 JSON。

## 一套渲染器，三个产物

渲染器本身（`BedrockPipeline`、`FrameContext`、`BedrockAssetLibrary`、`.mcpack` 导入器、
GUI 和命令）是纯 Java，不引用任何 Minecraft 类。每个平台只提供同样的三小样东西：

- 一个 `ClientSnapshot`（相机、生物群系、天气、水面高度）—— Fabric / Quilt 用 Yarn 名，
  Forge / NeoForge 用官方映射名；
- 一个 `ResourceSource`（`open` + `list`），封装该加载器的资源管理器；
- 三个 mixin，注入同三个原版方法（`GameRenderer#renderLevel`、`LevelRenderer#renderSky`、
  `ShaderInstance#apply` / `ShaderProgram#bind`）。

Quilt 完全不需要移植：Quilt Loader 0.30 原生加载 Fabric 模组（含 Fabric API，因为 1.21.1 没有
Quilted Fabric API 发布），所以 Fabric 那个 jar 原样就能跑。

## 工作原理

Minecraft 1.21.1 通过核心着色器程序 `rendertype_solid`、`rendertype_cutout`、
`rendertype_cutout_mipped`、`rendertype_translucent` 和 `rendertype_entity_solid` 绘制区块。
模组**不**改原版类的渲染代码，而是：

1. **生成一个真正的资源包**（`BedrockShaderPack`），用基岩风格的 GLSL 及其 JSON 定义接管这五个
   程序。因为它就是普通资源包，所以包栈、重载逻辑和用户覆盖都照常工作。
2. **注入基岩版 uniform**（`BedrockUniforms`、`ShaderProgramMixin`）—— 日 / 月方向与强度、天空
   天顶 / 地平线颜色、雾色与雾浓度、水色与波浪偏移 —— 在原版解析好的 `ShaderProgram` 绑定后立刻写入。
3. **运行时解析基岩版资源**（`BedrockAssetLibrary`）：`assets/bedrock_shaders/bedrock/` 下 126 份
   基岩版 JSON（光照、大气、雾、调色）被解析成曲线，并绑定到原版的生物群系 / 雾标识上，于是每个
   生物群系都能拿到自己的基岩版雾、天空与调色。
4. **跑一条后处理链**（`BedrockPipeline`），用自己的帧缓冲和 GLSL 150 程序实现 SSAO、泛光、
   体积光与基岩版调色。

### 着色器源码

`shadersrc/` 是唯一真相来源。`verification/sync_shaders.mjs` 会把 `#moj_import` 展开并写出打包
副本到 `src/main/resources/assets/bedrock_shaders/bedrockshader/`；Gradle 在 `processResources`
之前运行它，所以 jar 里不可能出现未同步的 GLSL。

## 验证（不需要 GPU，也不需要装 Minecraft）

```bash
cd verification && npm install
node validate_shaders.mjs                    # 用 Khronos glslang 编译每个核心 + 后处理程序
python3 ../verification/validate_pack.py     # 生成的包符合 1.21.1 核心着色器规则
python3 ../verification/validate_assets.py   # 每份基岩版 JSON 都能解析且数值在范围内
python3 ../verification/test_importer.py     # .mcpack 布局规则、嵌套压缩包、语言键一致性
python3 ../verification/test_families.py     # 光影系列识别（17 个真实包名）
```

包验证器存在的理由：核心着色器 JSON 里一个错误属性会让整次资源重载直接中止，而不是优雅失败；
它检查 `vertex`/`fragment` 命名、uniform 类型（只允许 `matrix4x4`/`float`/`int`）、
`count`/`values` 一致性，以及程序声明的采样器是否恰好等于原版渲染层所绑定的那些。

## 目录结构

```
shadersrc/core|include      基岩版 GLSL（手工维护，#version 150）
src/main/java               通用部分：配置模型、基岩版资源模型（曲线、光照、雾、水）
src/client/java/.../pack    .mcpack 导入器、包注册表、资源包激活
src/client/java             Fabric 客户端：管线、uniform、Yarn mixin、GUI、命令
src/main/resources/assets   同步生成的 GLSL + 基岩版 JSON 样例 + 语言文件 + 图标
src/quilt/resources         quilt.mod.json（Quilt 产物 = Fabric 产物换元数据）
loaders/common-official     Forge 与 NeoForge 共用的官方映射客户端代码
loaders/forge|neoforge      独立的 Gradle 构建 + 各自入口与元数据
verification/               离线着色器 / 资源 / 包验证器，CI 入口
dist/                       CI 发布的 jar
```

## 授权说明

`assets/bedrock_shaders/bedrock/**` 包含来自 Minecraft: Bedrock Edition
（Mojang/bedrock-samples）的样例 JSON 文档，依 Minecraft EULA 仍归 © Mojang AB 所有 —— 见
`LICENSE`。其余部分为 MIT。
