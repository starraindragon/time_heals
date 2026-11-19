---
navigation:
  parent: index.md
  title: 混沌级伤害
  position: 3
  icon: draconicevolution:wyvern_sword
---

# 混沌级伤害

龙之进化（Draconic Evolution）判定一次攻击是否「混沌级」时只看两件事：

1. 伤害类型是否带有 `draconicevolution:chaotic` 标签；
2. 攻击者**主手**物品是否实现了 `IDraconicMelee` 并返回混沌科技等级。

KubeJS 注册的物品无法实现 Java 接口，因此本模组通过 Mixin 接管判定：
**主手持有白名单中的物品时，伤害直接按混沌级处理。**

## 效果

- 攻击混沌晶体的护盾时 **无视「不稳定窗口」**，可以直接造成伤害；
- 与其它混沌级武器一致。

## 时序裁决器

[时序裁决器](../blocks/verdict-device.md) 的攻击阶段同样使用混沌级伤害
（`draconicevolution:chaos_implosion`），一次 53000 点，足以连护盾带本体一起打掉守卫。

## 白名单

白名单写在配置 `config/time_heals-common.toml` 的 `timeWeapons` 里，
默认就是时间系列五件套；想给别的武器也加上，改配置即可，无需重新编译。
