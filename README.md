# Time Heals (`time_heals`)

**中文**：一个 NeoForge 模组，作为《简单工程 EazyTAC》整合包中**时间系列装备**的前置/机制扩展。
物品本身由整合包的 KubeJS 脚本注册，本模组只补上 KubeJS 无法实现的行为：

- **混沌级伤害判定** —— 主手持有时间系列武器时，龙之进化（Draconic Evolution）将其攻击判定为混沌级；
- **时间混乱** —— 三层递进效果，第 III 层每秒造成真实伤害；
- **时间加速** —— 对准机器按快捷键，消耗时间水晶让方块实体加速运转；
- **内置指南** —— 基于 GuideME，悬停物品长按 G 直接跳转对应页面。

**English**: A NeoForge addon for the *time-series equipment* of the EazyTAC modpack.
Items are registered by the pack's KubeJS scripts; this mod only supplies what KubeJS cannot:
chaotic damage detection, the Time Confusion effect, block-entity time acceleration,
and an in-game GuideME guide.

- Minecraft `1.21.1` · NeoForge `21.1.248+` · Mod ID `time_heals` · Version `1.2.0`

---

## 需要哪些依赖

| 依赖 | 类型 | 用途 |
| --- | --- | --- |
| `draconicevolution` | 可选 | 混沌级伤害判定（Mixin 到 `DEDamage#getDamageLevel`） |
| `guideme` | 可选 | 内置说明文档 |
| `kubejs` | 不在编译期依赖 | 提供被强化的时间系列物品 |

缺少可选依赖时模组仍能正常加载，只是对应功能不启用。

## 功能

### 1. 混沌级伤害

DE 判定一次伤害是否为「混沌级」只看两件事：伤害类型标签，或主手物品是否实现
`IDraconicMelee`。KubeJS 物品无法实现 Java 接口，因此本模组用 Mixin 接管判定：

```java
@Inject(method = "getDamageLevel", at = @At("HEAD"), cancellable = true)
private static void time_heals$chaoticWeaponWhitelist(...) { ... }
```

白名单写在配置 `timeWeapons` 中，默认是时间系列五件套。

### 2. 时间混乱（3 层）

被时间系列武器命中的生物叠加层数：

| 层 | 效果 | 持续 |
| --- | --- | --- |
| I | 移速 -30%，受到伤害 ×1.10 | 10 秒 |
| II | 无法移动，受到伤害 ×1.32 | 30 秒 |
| III | 剥夺 AI + 无法移动，受到伤害 ×1.584，每秒损失 `max(当前生命 10%, 10)` | 60 秒 |

- 第 III 层的侵蚀伤害直接修改生命值，**无视护甲、抗性、吸收与绝大多数减伤**；
- 附带粒子与自定义字幕音效（`time_erosion` / `time_barrier_break`）。

### 3. 时间加速

手持时间之剑，对准机器使用快捷键：

| 操作 | 消耗 | 效果 |
| --- | --- | --- |
| 第一次 | 1 个时间水晶 | 速度 ×2，30 秒 |
| 第二次 | 1 个时间水晶 | 速度 ×4，刷新 30 秒 |
| 之后 | 1 个时间水晶 | 剩余时间 +30 秒 |

- 实现方式与无用之物的造化垂青之杖一致：每个游戏 tick 额外调用方块实体的
  `BlockEntityTicker#tick`；没有方块实体但会随机刻的方块则额外掷随机刻；
- 被加速的方块上方显示 `×倍率 · 剩余秒数` 的悬浮文字（`Display.TextDisplay`），
  显示半径可配置，默认 5 格；
- 只有白名单 `accelerationWeapons`（默认仅 `kubejs:time_sword`）可触发。

### 4. 快捷键

| 项 | 说明 |
| --- | --- |
| 默认绑定 | **Shift + 右键** |
| 自定义 | 改键后**按下该键直接触发**，不需要再点右键 |
| 改键位置 | 选项 → 控制 → 按键绑定 → Time Heals |

客户端只在按键命中时向服务端发送 `time_heals:accelerate` 数据包，
服务端会校验距离（≤8 格）与主手物品。

### 5. 内置指南

基于 GuideME，内容位于 `assets/time_heals/time_heals_guide/`：

```
index.md                    首页
items/time-weapons.md       五件套总览
items/time-sword.md         时间之剑
items/time-pickaxe.md       时间之镐
items/time-axe.md           时间之斧
items/time-shovel.md        时间之锹
items/time-hoe.md           时间之锄
items/time-crystal.md       时间水晶
items/time-upgrade-template.md  时间锻造模板
blocks/time-crystal-block.md    时间水晶块
blocks/2x-time-crystal-block.md 2 重压缩时间水晶块
effects/time-confusion.md   时间混乱
mechanics/chaos-damage.md   混沌级伤害
mechanics/time-acceleration.md  时间加速
```

悬停物品**长按 G**（GuideME 默认热键）即可打开对应页面。

> 注意：指南注册必须早于资源重载的 prepare 阶段，因此是在模组构造阶段调用
> `Guide.builder(...).build()`，不要改到 `FMLClientSetupEvent`。

## 配置

`config/time_heals-common.toml`：

```toml
# 能触发混沌级伤害/时间混乱的物品白名单
timeWeapons = ["kubejs:time_sword", "kubejs:time_pickaxe", "kubejs:time_axe",
               "kubejs:time_shovel", "kubejs:time_hoe"]

# 能触发「时间加速」的物品白名单（仅时间之剑）
accelerationWeapons = ["kubejs:time_sword"]

# 加速悬浮文字的可见半径（格）
accelerationDisplayRange = 5.0
```

## 构建

需要 **JDK 21** 与 Gradle（自带 wrapper 8.8）：

```bash
./gradlew build
```

产物：`build/libs/time_heals-1.2.0.jar`

### dev 运行约定

开发环境（`runClient`）只挂载少量核心模组，且**全部来自工程内 `devmods/`**：

- 整合包里不少 jar 带中文/emoji 前缀（如 `[玉 🔍] Jade-...`），其中 emoji 经 Windows
  启动参数传递时会丢字符，模组会被静默跳过；
- 因此约定：把需要的 jar 复制到 `devmods/` 并改成**纯英文文件名**，`build.gradle` 只引用：

```groovy
runtimeOnly fileTree('devmods') { include '*.jar' }
```

`devmods/` 仅用于本地调试，不参与发布产物，已在 `.gitignore` 中排除。

## 多人游戏

时间混乱的 Mob 效果、快捷键与加速网络包都依赖本模组：
**服务端与所有客户端都需要安装**，否则加速请求无法发出、效果 ID 客户端也不识别。

## 已知限制

- 不注册任何物品，所有时间系物品来自 KubeJS；
- 时间加速只对「注册了 `BlockEntityTicker` 的方块实体」或「会随机刻的方块」生效；
- 加速状态仅存在于内存，服务器重启后失效，需要重新施加；
- 混沌守卫的晶体/守卫本身属于多部位实体，本模组不为其做特殊处理。

## 变更记录

### 1.2.0

- 新增：**时序裁决器**方块（`time_heals:verdict_device`），3×3 视觉、逻辑占 1 格；
- 模型读取 `assets/time_heals/models/block/verdict_device_parts.json`，替换该 JSON 与贴图即可换模型；
- 中央水晶：自旋 15°/秒、上下浮动总行程 1 格（周期 2 秒）；
- 碰撞箱只覆盖底座，避免遮挡走位。

### 1.1.0

- 新增：对**免疫 Mob 效果**的生物（凋零、末影龙）启用独立追踪器，
  移速削减、定身、剥夺 AI 与每秒侵蚀同样生效；
- 新增：Mixin 放行这两种 BOSS 的「时间混乱」效果，Jade 可正常显示；
- 新增：锁 AI 期间手动接管 BOSS 血条（凋零 `bossEvent`、末影龙 `dragonEvent`）；
- 新增：多部位实体识别（准星命中 `EnderDragonPart` 时取本体）；
- 原因：两者的 `addEffect` 恒返回 `false`，且血条依赖自身 AI tick 更新。

### 1.0.0

- 新增：时间混乱三层效果（移速/定身/剥夺 AI + 每秒真实伤害）；
- 新增：时间系列武器在龙之进化中判定为混沌级；
- 新增：时间加速（×2 → ×4 → 续时）与悬浮文字；
- 新增：GuideME 内置指南（14 页）与可改键快捷键；
- 新增：自定义音效事件与字幕。

## 许可证

AGPL-3.0-or-later，详见 [LICENSE](LICENSE)。
