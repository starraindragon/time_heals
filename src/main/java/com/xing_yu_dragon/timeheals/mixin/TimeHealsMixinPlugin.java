package com.xing_yu_dragon.timeheals.mixin;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 门禁：每个 Mixin 只在它依赖的模组真正存在时才应用。
 *
 * <p>当前工程只对龙之进化（{@code draconicevolution}）有软依赖，
 * 后续新增 mixin 时在这里按需追加分支即可。
 *
 * <p>关键：判断模组是否存在必须用 {@link LoadingModList}（模组发现阶段就已就绪），
 * 不能用 {@link ModList}——后者在模组构建完成后才可用，而 {@code shouldApplyMixin}
 * 可能在 {@code ModList} 就绪前就被调用（此时返回 null 会误判为"未加载"）。
 */
public final class TimeHealsMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("time_heals");

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("[Mixin门禁] time_heals mixin 插件已加载");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".ChaosWeaponDamageMixin")) {
            boolean apply = isModLoaded("draconicevolution");
            LOGGER.info("[Mixin门禁] ChaosWeaponDamageMixin 应用={} (draconicevolution {})",
                    apply, apply ? "已加载" : "未加载");
            return apply;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 不做目标合并
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需处理
    }

    /** 用最早期可用的来源判断模组是否加载。 */
    @SuppressWarnings("unused")
    private static boolean isModLoaded(String modId) {
        try {
            LoadingModList loading = LoadingModList.get();
            if (loading != null && loading.getModFileById(modId) != null) {
                return true;
            }
        } catch (Throwable ignored) {
            // 极端早期可能抛异常
        }
        try {
            ModList modList = ModList.get();
            if (modList != null && modList.isLoaded(modId)) {
                return true;
            }
        } catch (Throwable ignored) {
            // 忽略
        }
        return false;
    }
}
