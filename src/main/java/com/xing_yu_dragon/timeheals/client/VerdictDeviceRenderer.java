package com.xing_yu_dragon.timeheals.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xing_yu_dragon.timeheals.TimeHeals;
import com.xing_yu_dragon.timeheals.VerdictDeviceBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时序裁决器渲染器。
 *
 * <p>模型直接读 {@code assets/time_heals/models/block/verdict_device_parts.json}
 * （就是 Blockbench 导出的 Java 方块模型格式），逐 element 画 6 个面。
 * 这样作者改模型后只要替换这个 json + 贴图，不用改 Java。
 *
 * <p>动画：
 * <ul>
 *   <li>中央水晶：绕模型中心自旋 15°/秒；</li>
 *   <li>上下浮动：±0.5 格（总行程 1 格），周期 2 秒。</li>
 * </ul>
 */
public class VerdictDeviceRenderer implements BlockEntityRenderer<VerdictDeviceBlockEntity> {

    private static final ResourceLocation PARTS =
            ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "models/block/verdict_device_parts.json");

    private static final String TEX_ROOT = "textures/block/verdict_device/";

    /** 每帧 15° = 0.75°/tick。 */
    private static final float DEG_PER_TICK = 0.75F;
    /** 上下浮动：±8 模型单位 = ±0.5 格。 */
    private static final float BOB_UNITS = 8.0F;
    /** 浮动周期（tick）。 */
    private static final float BOB_PERIOD = 40.0F;
    /** 自旋与浮动的中心（模型单位）。 */
    private static final float PIVOT_X = 8.0F;
    private static final float PIVOT_Z = 8.0F;

    /** 底盘进度槽：外沿半宽 22、槽宽 2、贴合在 y=2 的槽底上。 */
    private static final float RING_OUTER = 22.0F;
    private static final float RING_WIDTH = 2.0F;
    private static final float RING_Y = 2.06F;
    private static final int RING_SEGMENTS = 64;
    /** 进度槽用纯白发光贴图 + 顶点颜色染色。 */
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID, "textures/block/verdict_device/glow.png");

    private static List<Part> parts;
    private static final Map<String, RenderType> RENDER_TYPES = new ConcurrentHashMap<>();
    /** 全亮光照（0xF000F0）。 */
    private static final int LIGHT_FULL = 0xF000F0;

    public VerdictDeviceRenderer(BlockEntityRendererProvider.Context context) {
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void render(VerdictDeviceBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (parts == null) {
            parts = loadParts();
        }
        if (parts.isEmpty()) {
            return;
        }

        float time = be.getLevel() == null
                ? 0.0F
                : be.getLevel().getGameTime() + partialTick;
        float spin = (time * DEG_PER_TICK) % 360.0F;
        float bob = Mth.sin(time / BOB_PERIOD * Mth.TWO_PI) * BOB_UNITS;

        for (Part part : parts) {
            pose.pushPose();
            // 水晶组：绕中心自旋 + 上下浮动
            if (part.crystal) {
                pose.translate(PIVOT_X / 16.0F, bob / 16.0F, PIVOT_Z / 16.0F);
                pose.mulPose(Axis.YP.rotationDegrees(spin));
                pose.translate(-PIVOT_X / 16.0F, 0.0F, -PIVOT_Z / 16.0F);
            }
            // 元素自身的旋转（Blockbench 导出的 rotation）
            if (part.angle != 0.0F) {
                pose.translate((float) part.origin.x / 16.0F, (float) part.origin.y / 16.0F,
                        (float) part.origin.z / 16.0F);
                pose.mulPose(new Quaternionf().rotationAxis(
                        (float) Math.toRadians(part.angle), axisVector(part.axis)));
                pose.translate((float) -part.origin.x / 16.0F, (float) -part.origin.y / 16.0F,
                        (float) -part.origin.z / 16.0F);
            }

            VertexConsumer vc = buffers.getBuffer(
                    renderType(part.faces.get(0).texture, part.crystal));
            Matrix4f mat = pose.last().pose();
            int partLight = part.crystal ? LIGHT_FULL : light;
            for (Face face : part.faces) {
                drawFace(vc, mat, part, face, partLight, overlay);
            }
            pose.popPose();
        }

        drawProgressRing(pose, buffers, be.getProgress(), time);
        drawPillarRings(pose, buffers, be.getCrystalRings(), time);
        if (be.getPhase() == VerdictDeviceBlockEntity.PHASE_LOCK && be.hasAim()) {
            drawLockBeam(pose, buffers, be.getAimX(), be.getAimY(), be.getAimZ(), time);
        }
        if (be.getPhase() == VerdictDeviceBlockEntity.PHASE_CHARGE) {
            drawChargeBeams(pose, buffers, time);
        }
    }

    /** 锁定期：中央水晶向被锁定的混沌守卫发射追踪光束（reactor_energy_beam 风格）。 */
    private static void drawLockBeam(PoseStack pose, MultiBufferSource buffers,
                                     float aimX, float aimY, float aimZ, float time) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GLOW));
        Matrix4f mat = pose.last().pose();

        // 起点：中央水晶中心（模型单位）
        float sx = 8.0F, sy = 20.0F, sz = 8.0F;
        // 终点：目标（相对方块，转成模型单位）
        float ex = aimX * 16.0F, ey = aimY * 16.0F, ez = aimZ * 16.0F;

        // 亮线本体
        int seg = 40;
        float pulse = 0.7F + 0.3F * Mth.sin(time * 0.5F);
        int lr = (int) (150 * pulse + 60);
        int lg = (int) (245 * pulse + 10);
        int lb = (int) (235 * pulse + 20);
        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg;
            float t1 = (i + 1) / (float) seg;
            float x0 = Mth.lerp(t0, sx, ex), y0 = Mth.lerp(t0, sy, ey), z0 = Mth.lerp(t0, sz, ez);
            float x1 = Mth.lerp(t1, sx, ex), y1 = Mth.lerp(t1, sy, ey), z1 = Mth.lerp(t1, sz, ez);
            float w = 0.035F * 16.0F;
            coloredQuad(vc, mat, x0, y0 + w, z0, x1, y1 + w, z1, x1, y1 - w, z1, x0, y0 - w, z0,
                    lr, lg, lb, 150, 0.0F, 1.0F, 0.0F);
            coloredQuad(vc, mat, x0 + w, y0, z0, x1 + w, y1, z1, x1 - w, y1, z1, x0 - w, y0, z0,
                    lr, lg, lb, 110, 0.0F, 0.0F, 1.0F);
        }

        // 沿光束流动的粒子
        int points = 28;
        for (int i = 0; i < points; i++) {
            float t = (i / (float) points + time * 0.12F) % 1.0F;
            float x = Mth.lerp(t, sx, ex);
            float y = Mth.lerp(t, sy, ey) + Mth.sin(time * 0.25F + i) * 0.3F;
            float z = Mth.lerp(t, sz, ez);
            glowCross(vc, mat, x, y, z, 0.05F * 16.0F, 255, 255, 255, 235);
        }

        // 目标处的命中光点
        for (int i = 0; i < 6; i++) {
            double a = time * 0.3F + i * (Math.PI * 2 / 6);
            glowCross(vc, mat,
                    ex + (float) (Math.cos(a) * 1.2F),
                    ey + Mth.sin(time * 0.5F + i) * 0.8F,
                    ez + (float) (Math.sin(a) * 1.2F),
                    0.06F * 16.0F, 255, 180, 120, 240);
        }
    }

    /** 蓄能期：从四根柱子顶端向中央水晶发射粒子流，密到几乎连成线。 */
    private static void drawChargeBeams(PoseStack pose, MultiBufferSource buffers, float time) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GLOW));
        Matrix4f mat = pose.last().pose();

        float[] px = {-7.5F, 23.5F, -7.5F, 23.5F};
        float[] pz = {-7.5F, -7.5F, 23.5F, 23.5F};
        float startY = 32.0F;      // 柱顶（模型单位）
        // 终点 = 网格坐标 (47, 49, 47) 换算到模型单位：grid × 0.5 − 16
        float endX = 7.5F;
        float endY = 24.5F;
        float endZ = 7.5F;
        int points = 30;           // 每条束上的粒子数，密到连成线
        float size = 0.05F * 16.0F;

        // 颜色随时间在青白之间脉动
        float pulse = 0.65F + 0.35F * Mth.sin(time * 0.35F);
        int r = (int) (170 * pulse + 60);
        int g = (int) (245 * pulse + 10);
        int b = (int) (230 * pulse + 25);
        int alpha = 235;

        for (int p = 0; p < 4; p++) {
            // 先铺一条很细的亮线，保证远看也是连成一条
            int lineSeg = 32;
            for (int i = 0; i < lineSeg; i++) {
                float t0 = i / (float) lineSeg;
                float t1 = (i + 1) / (float) lineSeg;
                float x0 = Mth.lerp(t0, px[p], endX);
                float y0 = Mth.lerp(t0, startY, endY);
                float z0 = Mth.lerp(t0, pz[p], endZ);
                float x1 = Mth.lerp(t1, px[p], endX);
                float y1 = Mth.lerp(t1, startY, endY);
                float z1 = Mth.lerp(t1, pz[p], endZ);
                float w = 0.02F * 16.0F;
                coloredQuad(vc, mat, x0, y0 + w, z0, x1, y1 + w, z1, x1, y1 - w, z1, x0, y0 - w, z0,
                        120, 240, 230, 120, 0.0F, 1.0F, 0.0F);
            }
            for (int i = 0; i < points; i++) {
                // 粒子沿路径流动，整体看起来像连续光束
                float t = (i / (float) points + time * 0.09F) % 1.0F;
                float x = Mth.lerp(t, px[p], endX);
                float y = Mth.lerp(t, startY, endY) + Mth.sin(time * 0.2F + i) * 0.4F;
                float z = Mth.lerp(t, pz[p], endZ);
                glowCross(vc, mat, x, y, z, size, r, g, b, alpha);
            }
        }
        // 核心处汇聚的亮点
        for (int i = 0; i < 8; i++) {
            double a = time * 0.15F + i * (Math.PI * 2 / 8);
            float x = endX + (float) (Math.cos(a) * 1.6F);
            float z = endZ + (float) (Math.sin(a) * 1.6F);
            float y = endY + Mth.sin(time * 0.4F + i) * 1.2F;
            glowCross(vc, mat, x, y, z, size * 1.4F, 255, 255, 255, 240);
        }
    }

    /** 两个互相垂直的竖面片，从任意角度看都能看到一点“粒子”。 */
    private static void glowCross(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                                  float half, int r, int g, int b, int alpha) {
        coloredQuad(vc, mat, x - half, y + half, z, x + half, y + half, z,
                x + half, y - half, z, x - half, y - half, z, r, g, b, alpha, 0, 0, 1);
        coloredQuad(vc, mat, x, y + half, z - half, x, y + half, z + half,
                x, y - half, z + half, x, y - half, z - half, r, g, b, alpha, 1, 0, 0);
    }

    /** 每根柱子上已点亮层数的常驻圆环：细环带，客户端每帧绘制。 */
    private static void drawPillarRings(PoseStack pose, MultiBufferSource buffers, int rings, float time) {
        if (rings <= 0) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GLOW));
        Matrix4f mat = pose.last().pose();

        float[] cx = {-7.5F, 23.5F, -7.5F, 23.5F};
        float[] cz = {-7.5F, -7.5F, 23.5F, 23.5F};
        int segments = 24;
        float rInner = 0.30F * 16.0F;
        float rOuter = 0.38F * 16.0F;

        for (int layer = 0; layer < Math.min(rings, 14); layer++) {
            float t = (layer + 1) / 14.0F;
            float[] c = lerpColor(0.0F, 1.0F, t);
            float pulse = 0.70F + 0.30F * Mth.sin(time * 0.12F + layer * 0.85F);
            int r = (int) (c[0] * 255 * pulse);
            int g = (int) (c[1] * 255 * pulse);
            int b = (int) (c[2] * 255 * pulse);
            int alpha = 225;
            float y = (0.30F + (1.62F * layer) / 13.0F) * 16.0F;
            float phase = time * 0.02F + layer * 0.35F;

            for (int p = 0; p < 4; p++) {
                for (int i = 0; i < segments; i++) {
                    double a0 = (Math.PI * 2.0D) * i / segments + phase;
                    double a1 = (Math.PI * 2.0D) * (i + 1) / segments + phase;
                    float x0 = cx[p] + (float) (Math.cos(a0) * rInner);
                    float z0 = cz[p] + (float) (Math.sin(a0) * rInner);
                    float x1 = cx[p] + (float) (Math.cos(a0) * rOuter);
                    float z1 = cz[p] + (float) (Math.sin(a0) * rOuter);
                    float x2 = cx[p] + (float) (Math.cos(a1) * rOuter);
                    float z2 = cz[p] + (float) (Math.sin(a1) * rOuter);
                    float x3 = cx[p] + (float) (Math.cos(a1) * rInner);
                    float z3 = cz[p] + (float) (Math.sin(a1) * rInner);
                    coloredQuad(vc, mat, x0, y, z0, x1, y, z1, x2, y, z2, x3, y, z3,
                            r, g, b, alpha, 0.0F, 1.0F, 0.0F);
                }
            }
        }
    }

    private static void coloredQuad(VertexConsumer vc, Matrix4f mat,
                                    float ax, float ay, float az,
                                    float bx, float by, float bz,
                                    float cx2, float cy, float cz2,
                                    float dx, float dy, float dz,
                                    int r, int g, int b, int alpha,
                                    float nx, float ny, float nz) {
        vc.addVertex(mat, ax / 16.0F, ay / 16.0F, az / 16.0F).setColor(r, g, b, alpha)
                .setUv(0.5F, 0.5F).setOverlay(0).setLight(LIGHT_FULL).setNormal(nx, ny, nz);
        vc.addVertex(mat, bx / 16.0F, by / 16.0F, bz / 16.0F).setColor(r, g, b, alpha)
                .setUv(0.5F, 0.5F).setOverlay(0).setLight(LIGHT_FULL).setNormal(nx, ny, nz);
        vc.addVertex(mat, cx2 / 16.0F, cy / 16.0F, cz2 / 16.0F).setColor(r, g, b, alpha)
                .setUv(0.5F, 0.5F).setOverlay(0).setLight(LIGHT_FULL).setNormal(nx, ny, nz);
        vc.addVertex(mat, dx / 16.0F, dy / 16.0F, dz / 16.0F).setColor(r, g, b, alpha)
                .setUv(0.5F, 0.5F).setOverlay(0).setLight(LIGHT_FULL).setNormal(nx, ny, nz);
    }

    /** 底盘的进度环：按进度逐段点亮，颜色由绿过渡到红。 */
    private static void drawProgressRing(PoseStack pose, MultiBufferSource buffers, float progress, float time) {
        if (progress <= 0.0F) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GLOW));
        Matrix4f mat = pose.last().pose();
        int alpha = 180 + (int) (60.0F * Math.abs(Mth.sin(time / 10.0F)));

        for (int i = 0; i < RING_SEGMENTS; i++) {
            float t0 = i / (float) RING_SEGMENTS;
            if (t0 >= progress) {
                break;
            }
            float t1 = Math.min((i + 1) / (float) RING_SEGMENTS, progress);
            float[] c = lerpColor(0.22F, 1.0F, t1);
            int r = (int) (c[0] * 255);
            int g = (int) (c[1] * 255);
            int b = (int) (c[2] * 255);
            quadRingSegment(vc, mat, t0, t1, r, g, b, alpha);
        }
    }

    /** 绿(0x38d39f) → 红(0xff3b30) 的插值。 */
    private static float[] lerpColor(float from, float to, float t) {
        float k = (t - from) / (to - from);
        k = Mth.clamp(k, 0.0F, 1.0F);
        float g0 = 0.83F, b0 = 0.62F, r0 = 0.22F;   // #38d39f
        float g1 = 0.23F, b1 = 0.19F, r1 = 1.00F;   // #ff3b30
        return new float[]{Mth.lerp(k, r0, r1), Mth.lerp(k, g0, g1), Mth.lerp(k, b0, b1)};
    }

    /** 矩形环上的参数点：u ∈ [0,1) 顺时针一圈；inward=true 取内沿。
     *  坐标以模型中心 (8, 8) 为原点，所以整体要加 8。 */
    private static Vec3 ringPoint(float u, boolean inward) {
        float half = inward ? RING_OUTER - RING_WIDTH : RING_OUTER;
        u = u - (float) Math.floor(u);
        float t = u * 4.0F;
        int side = (int) t;
        float f = t - side;
        float cx = 8.0F, cz = 8.0F;
        return switch (side) {
            case 0 -> new Vec3(cx - half + 2 * half * f, RING_Y, cz - half);   // 北边：x 向右
            case 1 -> new Vec3(cx + half, RING_Y, cz - half + 2 * half * f);   // 东边：z 向前
            case 2 -> new Vec3(cx + half - 2 * half * f, RING_Y, cz + half);   // 南边：x 向左
            default -> new Vec3(cx - half, RING_Y, cz + half - 2 * half * f);  // 西边：z 向后
        };
    }

    private static void quadRingSegment(VertexConsumer vc, Matrix4f mat, float t0, float t1,
                                        int r, int g, int b, int alpha) {
        Vec3 o0 = ringPoint(t0, false);
        Vec3 o1 = ringPoint(t1, false);
        Vec3 i1 = ringPoint(t1, true);
        Vec3 i0 = ringPoint(t0, true);
        coloredVertex(vc, mat, o0, r, g, b, alpha);
        coloredVertex(vc, mat, o1, r, g, b, alpha);
        coloredVertex(vc, mat, i1, r, g, b, alpha);
        coloredVertex(vc, mat, i0, r, g, b, alpha);
    }

    private static void coloredVertex(VertexConsumer vc, Matrix4f mat, Vec3 p,
                                      int r, int g, int b, int alpha) {
        vc.addVertex(mat, (float) p.x / 16.0F, (float) p.y / 16.0F, (float) p.z / 16.0F)
                .setColor(r, g, b, alpha)
                .setUv(0.5F, 0.5F)
                .setOverlay(0)
                .setLight(LIGHT_FULL)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static Vector3f axisVector(String axis) {
        return switch (axis) {
            case "x" -> new Vector3f(1.0F, 0.0F, 0.0F);
            case "z" -> new Vector3f(0.0F, 0.0F, 1.0F);
            default -> new Vector3f(0.0F, 1.0F, 0.0F);
        };
    }

    /** 水晶走半透明渲染，其余部件走镂空不透明渲染。 */
    private static RenderType renderType(ResourceLocation tex, boolean translucent) {
        String key = (translucent ? "T:" : "C:") + tex;
        return RENDER_TYPES.computeIfAbsent(key,
                k -> translucent ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex));
    }

    /** 按方向画一个四边形；UV 来自 element 的 faces 定义（模型空间 0~32 → 0~1）。 */
    private static void drawFace(VertexConsumer vc, Matrix4f mat, Part p, Face f,
                                 int light, int overlay) {
        float x0 = (float) p.from.x, y0 = (float) p.from.y, z0 = (float) p.from.z;
        float x1 = (float) p.to.x, y1 = (float) p.to.y, z1 = (float) p.to.z;
        // 模型 UV 以 16 为基准（Blockbench 项目贴图 16px，导出 JSON 的 texture_size 却写成 32）
        float u1 = f.u1 / 16.0F, v1 = f.v1 / 16.0F;
        float u2 = f.u2 / 16.0F, v2 = f.v2 / 16.0F;
        float nx = 0.0F, ny = 0.0F, nz = 0.0F;

        switch (f.dir) {
            case "down" -> {
                ny = -1.0F;
                vertex(vc, mat, x0, y0, z0, u1, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x0, y0, z1, u1, v2, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y0, z1, u2, v2, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y0, z0, u2, v1, nx, ny, nz, light, overlay);
            }
            case "up" -> {
                ny = 1.0F;
                vertex(vc, mat, x0, y1, z0, u1, v2, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y1, z0, u2, v2, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y1, z1, u2, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x0, y1, z1, u1, v1, nx, ny, nz, light, overlay);
            }
            case "north" -> {
                nz = -1.0F;
                vertex(vc, mat, x1, y1, z0, u1, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x0, y1, z0, u2, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x0, y0, z0, u2, v2, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y0, z0, u1, v2, nx, ny, nz, light, overlay);
            }
            case "south" -> {
                nz = 1.0F;
                vertex(vc, mat, x0, y1, z1, u1, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y1, z1, u2, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y0, z1, u2, v2, nx, ny, nz, light, overlay);
                vertex(vc, mat, x0, y0, z1, u1, v2, nx, ny, nz, light, overlay);
            }
            case "west" -> {
                nx = -1.0F;
                vertex(vc, mat, x0, y1, z0, u2, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x0, y1, z1, u1, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x0, y0, z1, u1, v2, nx, ny, nz, light, overlay);
                vertex(vc, mat, x0, y0, z0, u2, v2, nx, ny, nz, light, overlay);
            }
            case "east" -> {
                nx = 1.0F;
                vertex(vc, mat, x1, y1, z1, u2, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y1, z0, u1, v1, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y0, z0, u1, v2, nx, ny, nz, light, overlay);
                vertex(vc, mat, x1, y0, z1, u2, v2, nx, ny, nz, light, overlay);
            }
            default -> {
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light, int overlay) {
        vc.addVertex(mat, x / 16.0F, y / 16.0F, z / 16.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }

    // ------------------------------------------------------------------
    // 模型加载
    // ------------------------------------------------------------------

    private static List<Part> loadParts() {
        List<Part> out = new ArrayList<>();
        try (var stream = Minecraft.getInstance().getResourceManager()
                .getResource(PARTS).orElseThrow().open()) {
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject textures = root.has("textures") ? root.getAsJsonObject("textures") : new JsonObject();
            for (var element : root.getAsJsonArray("elements")) {
                JsonObject e = element.getAsJsonObject();
                Part p = new Part();
                p.from = vec(e.getAsJsonArray("from"));
                p.to = vec(e.getAsJsonArray("to"));
                if (e.has("rotation")) {
                    JsonObject r = e.getAsJsonObject("rotation");
                    p.axis = r.has("axis") ? r.get("axis").getAsString() : "y";
                    p.angle = r.has("angle") ? r.get("angle").getAsFloat() : 0.0F;
                    p.origin = r.has("origin") ? vec(r.getAsJsonArray("origin")) : new Vec3(0, 0, 0);
                }
                JsonObject faces = e.getAsJsonObject("faces");
                for (String dir : faces.keySet()) {
                    JsonObject face = faces.getAsJsonObject(dir);
                    Face f = new Face();
                    f.dir = dir;
                    JsonArray uv = face.getAsJsonArray("uv");
                    f.u1 = uv.get(0).getAsFloat();
                    f.v1 = uv.get(1).getAsFloat();
                    f.u2 = uv.get(2).getAsFloat();
                    f.v2 = uv.get(3).getAsFloat();
                    String key = face.has("texture") ? face.get("texture").getAsString() : "#0";
                    if (key.startsWith("#")) {
                        key = key.substring(1);
                    }
                    String texName = textures.has(key) ? textures.get(key).getAsString() : key;
                    f.texture = ResourceLocation.fromNamespaceAndPath(TimeHeals.MODID,
                            TEX_ROOT + texName + ".png");
                    p.faces.add(f);
                }
                String firstTex = p.faces.isEmpty() ? "" : p.faces.get(0).texture.getPath();
                p.crystal = firstTex.contains("crystal");
                out.add(p);
            }
        } catch (Exception ex) {
            TimeHeals.LOGGER.error("[time_heals] 时序裁决器模型加载失败: {}", ex.toString());
            return List.of();
        }
        if (!out.isEmpty()) {
            TimeHeals.LOGGER.info("[time_heals] 时序裁决器模型已加载: {} 个部件，首个贴图={}",
                    out.size(), out.get(0).faces.get(0).texture);
        }
        return out;
    }

    private static Vec3 vec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    // ------------------------------------------------------------------
    // 数据结构
    // ------------------------------------------------------------------

    private static final class Part {
        Vec3 from;
        Vec3 to;
        Vec3 origin = new Vec3(0, 0, 0);
        String axis = "y";
        float angle;
        boolean crystal;
        final List<Face> faces = new ArrayList<>();
    }

    private static final class Face {
        String dir;
        float u1, v1, u2, v2;
        ResourceLocation texture;
    }
}
