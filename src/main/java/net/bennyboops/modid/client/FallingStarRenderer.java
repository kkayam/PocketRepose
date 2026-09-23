package net.bennyboops.modid.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Occasional shooting stars: a bright head streaking across the upper sky with a tapered,
 * glowing trail that fades behind it. Purely client-side and time driven.
 */
public class FallingStarRenderer {
    private static final float RADIUS = 96.0f;
    private static final int TRAIL_SEGMENTS = 18;
    /** Ticks between streaks. TESTING: every 5 seconds. Production values: 900f and 1800f. */
    private static final float MIN_GAP = 100f;
    private static final float MAX_GAP = 100f;

    private static final float[][] COLOURS = {
            {1.00f, 1.00f, 1.00f},
            {0.80f, 0.90f, 1.00f},
            {1.00f, 0.85f, 0.95f},
            {1.00f, 0.95f, 0.70f},
            {0.75f, 1.00f, 0.95f}
    };

    private final Random random = Random.create();
    private final List<Star> active = new ArrayList<>();
    private float nextSpawn = -1f;
    private float lastTime = -1f;

    private static final class Star {
        Vector3f start;
        Vector3f end;
        float spawnTime;
        float duration;
        float trail;      // fraction of the path the tail spans
        float width;
        float[] colour;
    }

    public void render(Matrix4f pose, Matrix4f projection, float time, float night, float clear) {
        if (lastTime < 0f || time < lastTime) {
            // First frame, or the world clock wrapped: reschedule.
            nextSpawn = time + 200f + random.nextFloat() * 600f;
        }
        lastTime = time;

        if (time >= nextSpawn) {
            spawn(time);
            nextSpawn = time + MIN_GAP + random.nextFloat() * (MAX_GAP - MIN_GAP);
            // Occasionally a pair, a few seconds apart.
            if (random.nextInt(4) == 0) {
                nextSpawn = time + 60f + random.nextFloat() * 120f;
            }
        }

        if (active.isEmpty()) {
            return;
        }

        float visibility = (0.45f + 0.55f * night) * clear;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        Iterator<Star> it = active.iterator();
        while (it.hasNext()) {
            Star s = it.next();
            float progress = (time - s.spawnTime) / s.duration;
            if (progress > 1.0f + s.trail) {
                it.remove();
                continue;
            }
            drawStar(buffer, pose, s, progress, visibility);
        }
        RenderSystem.defaultBlendFunc();
    }

    private void spawn(float time) {
        Star s = new Star();
        // Start high-ish in a random direction, travel a decent arc downward and sideways.
        float az = random.nextFloat() * MathHelper.TAU;
        float elevStart = 35f + random.nextFloat() * 40f;
        float sweep = (0.25f + random.nextFloat() * 0.35f) * (random.nextBoolean() ? 1f : -1f);
        float elevEnd = elevStart - (12f + random.nextFloat() * 16f);
        s.start = direction(az, elevStart);
        s.end = direction(az + sweep, elevEnd);
        s.spawnTime = time;
        s.duration = 14f + random.nextFloat() * 12f;
        s.trail = 0.45f + random.nextFloat() * 0.25f;
        s.width = 0.9f + random.nextFloat() * 0.7f;
        s.colour = COLOURS[random.nextInt(COLOURS.length)];
        active.add(s);
    }

    private static Vector3f direction(float azimuth, float elevationDeg) {
        float e = elevationDeg * MathHelper.RADIANS_PER_DEGREE;
        float h = MathHelper.cos(e);
        return new Vector3f(MathHelper.cos(azimuth) * h, MathHelper.sin(e), MathHelper.sin(azimuth) * h);
    }

    /** Point on the sphere at parameter t along the star's path. */
    private static Vector3f pointAt(Star s, float t) {
        Vector3f p = new Vector3f(s.start).lerp(s.end, t);
        if (p.lengthSquared() < 1e-6f) {
            p.set(s.start);
        }
        return p.normalize(RADIUS);
    }

    private void drawStar(BufferBuilder buffer, Matrix4f pose, Star s, float progress, float visibility) {
        float head = Math.min(progress, 1.0f);
        float tail = Math.max(progress - s.trail, 0.0f);
        if (head <= tail) {
            return;
        }
        // Fade in quickly, then fade out once the head has left the path and the tail catches up.
        float fade = MathHelper.clamp(progress * 6f, 0f, 1f)
                * MathHelper.clamp((1.0f + s.trail - progress) / s.trail, 0f, 1f);
        float alphaScale = visibility * fade;
        float[] c = s.colour;

        // Outer soft glow, then a thin bright core on top.
        drawTrail(buffer, pose, s, tail, head, s.width * 1.0f, 0.45f * alphaScale, c, 0.55f);
        drawTrail(buffer, pose, s, tail, head, s.width * 0.28f, 1.0f * alphaScale, c, 1.0f);

        if (progress <= 1.0f) {
            drawHead(buffer, pose, pointAt(s, head), s.width * 2.4f, alphaScale, c);
        }
    }

    /**
     * A camera-facing strip along the path from tail to head. Width and alpha taper towards
     * the tail; whiteness controls how much the colour is pushed towards white.
     */
    private void drawTrail(BufferBuilder buffer, Matrix4f pose, Star s, float tail, float head,
                           float maxWidth, float maxAlpha, float[] c, float whiteness) {
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= TRAIL_SEGMENTS; i++) {
            float k = (float) i / TRAIL_SEGMENTS;            // 0 = tail, 1 = head
            float t = MathHelper.lerp(k, tail, head);
            Vector3f p = pointAt(s, t);
            Vector3f ahead = pointAt(s, Math.min(t + 0.01f, 1.0f));
            Vector3f dir = new Vector3f(ahead).sub(p);
            if (dir.lengthSquared() < 1e-6f) {
                dir = new Vector3f(s.end).sub(s.start);
            }
            Vector3f side = new Vector3f(dir).cross(p).normalize();
            // Brightest just behind the head, thinning to nothing at the tail.
            float profile = k * k * (1.0f - 0.35f * k);
            float width = maxWidth * (0.1f + 0.9f * k);
            float alpha = maxAlpha * profile / 0.65f;
            float r = MathHelper.lerp(whiteness * k, c[0], 1.0f);
            float g = MathHelper.lerp(whiteness * k, c[1], 1.0f);
            float b = MathHelper.lerp(whiteness * k, c[2], 1.0f);
            buffer.vertex(pose, p.x + side.x * width, p.y + side.y * width, p.z + side.z * width)
                    .color(r, g, b, alpha).next();
            buffer.vertex(pose, p.x - side.x * width, p.y - side.y * width, p.z - side.z * width)
                    .color(r, g, b, alpha).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /** A soft round glow: bright centre fading to transparent at the rim. */
    private void drawHead(BufferBuilder buffer, Matrix4f pose, Vector3f p, float radius, float alpha, float[] c) {
        Vector3f up = new Vector3f(0, 1, 0).cross(p);
        if (up.lengthSquared() < 1e-4f) {
            up.set(1, 0, 0);
        }
        up.normalize();
        Vector3f right = new Vector3f(p).cross(up).normalize();

        int segments = 16;
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buffer.vertex(pose, p.x, p.y, p.z).color(1f, 1f, 1f, alpha).next();
        for (int i = 0; i <= segments; i++) {
            float ang = (float) i / segments * MathHelper.TAU;
            float cx = MathHelper.cos(ang) * radius;
            float cy = MathHelper.sin(ang) * radius;
            float x = p.x + up.x * cy + right.x * cx;
            float y = p.y + up.y * cy + right.y * cx;
            float z = p.z + up.z * cy + right.z * cx;
            buffer.vertex(pose, x, y, z).color(c[0], c[1], c[2], 0.0f).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // Small hot core.
        float core = radius * 0.3f;
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buffer.vertex(pose, p.x, p.y, p.z).color(1f, 1f, 1f, alpha).next();
        for (int i = 0; i <= segments; i++) {
            float ang = (float) i / segments * MathHelper.TAU;
            float cx = MathHelper.cos(ang) * core;
            float cy = MathHelper.sin(ang) * core;
            buffer.vertex(pose, p.x + up.x * cy + right.x * cx, p.y + up.y * cy + right.y * cx,
                    p.z + up.z * cy + right.z * cx).color(1f, 1f, 1f, alpha * 0.4f).next();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
