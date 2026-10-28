package net.koiduu.pinspo;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.koiduu.pinspo.mixin.LevelExtractorAccessor;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.koiduu.pinspo.mixin.CameraAccessor;
import net.koiduu.pinspo.mixin.GameRendererAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * Renders the world a second time from a planted camera, straight after the player's own view, into an
 * offscreen target the camera's window then shows. One camera is refreshed per frame, round robin, so two
 * cameras cost about one extra world render rather than two.
 */
public final class CameraRenderer {

    private static final Map<CameraPoints.Point, TextureTarget> TARGETS = new IdentityHashMap<>();
    private static final Set<CameraPoints.Point> READY = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Vector4f CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);

    /**
     * Camera passes get their own pool of scratch targets. Sharing the game's meant the transparency and
     * post passes of a camera could hand textures still holding camera pixels back to the player's own
     * frame, which showed up as a faded ghost of another viewpoint across the screen.
     */
    private static CrossFrameResourcePool pool;
    private static boolean busy;
    private static boolean cullingRelaxed;
    private static boolean broken;
    private static int cursor;

    private CameraRenderer() {
    }

    public static boolean isRendering() {
        return busy;
    }

    public static void afterLevel(GameRenderer gameRenderer, DeltaTracker deltaTracker) {
        if (busy || broken) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        List<CameraPoints.Point> visible = CameraPoints.visibleHere();
        if (visible.isEmpty()) {
            if (cullingRelaxed) {
                client.smartCull = true;
                cullingRelaxed = false;
            }
            return;
        }
        // The chunk visibility graph is shared by every render. With occlusion culling on it is rebuilt
        // around whichever viewpoint rendered last, so the player's view lost chunks the camera could not
        // see (and the camera showed the player's). Without it the graph is simply "everything in range",
        // the same for both views.
        if (!cullingRelaxed) {
            client.smartCull = false;
            cullingRelaxed = true;
            client.levelRenderer.sectionOcclusionGraph().invalidate();
        }
        CameraPoints.Point point = visible.get(Math.floorMod(cursor++, visible.size()));
        Camera camera = gameRenderer.mainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        CameraAccessor cameraAccess = (CameraAccessor) camera;
        GameRendererAccessor rendererAccess = (GameRendererAccessor) gameRenderer;
        RenderTarget main = gameRenderer.mainRenderTarget();
        TextureTarget target = target(point, main.width, main.height);

        Vec3 position = camera.position();
        float yRot = camera.yRot();
        float xRot = camera.xRot();
        boolean detached = cameraAccess.pinspo$detached();
        float fov = cameraAccess.pinspo$fov();
        float viewFov = point.fov > 0.0F ? point.fov : fov;
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

        CrossFrameResourcePool gamePool = rendererAccess.pinspo$resourcePool();
        // The camera pass refills the renderer's visible-section lists for its own frustum. They're put back
        // afterwards; otherwise the player's next frame can reuse them (it only refilters when its own
        // rotation changes) and draws only what the camera sees.
        LevelExtractorAccessor extractorAccess = (LevelExtractorAccessor) client.levelExtractor;
        double prevRotX = extractorAccess.pinspo$prevCamRotX();
        double prevRotY = extractorAccess.pinspo$prevCamRotY();
        List<SectionRenderDispatcher.RenderSection> playerSections = new ArrayList<>(client.levelRenderer.visibleSections());
        List<SectionRenderDispatcher.RenderSection> playerNearby = new ArrayList<>(client.levelRenderer.nearbyVisibleSections());
        busy = true;
        try {
            cameraAccess.pinspo$setPosition(point.position());
            cameraAccess.pinspo$setRotation(point.yaw, point.pitch);
            // Detached, so the player's own body shows up in the view like any other entity.
            cameraAccess.pinspo$setDetached(true);
            setFov(cameraAccess, viewFov);
            refreshFrustum(camera, cameraAccess);
            rendererAccess.pinspo$extractCamera(deltaTracker, partialTick);
            extractorAccess.pinspo$setPrevCamRotX(Double.NaN);
            client.levelExtractor.extract(deltaTracker, camera, partialTick);

            if (pool == null) {
                pool = new CrossFrameResourcePool(3);
            }
            rendererAccess.pinspo$setResourcePool(pool);
            rendererAccess.pinspo$setMainRenderTarget(target);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), CLEAR, target.getDepthTexture(), 1.0D);
            gameRenderer.renderLevel();
            READY.add(point);
        } catch (Throwable error) {
            broken = true;
            PinSpoClient.LOGGER.error("Camera view failed; live cameras are off until the game restarts", error);
            if (client.player != null) {
                client.player.sendSystemMessage(Component.translatable("message.pinspo.camera_failed"));
            }
        } finally {
            rendererAccess.pinspo$setMainRenderTarget(main);
            rendererAccess.pinspo$setResourcePool(gamePool);
            client.levelRenderer.visibleSections().clear();
            client.levelRenderer.visibleSections().addAll(playerSections);
            client.levelRenderer.nearbyVisibleSections().clear();
            client.levelRenderer.nearbyVisibleSections().addAll(playerNearby);
            extractorAccess.pinspo$setPrevCamRotX(prevRotX);
            extractorAccess.pinspo$setPrevCamRotY(prevRotY);
            if (pool != null) {
                pool.endFrame();
            }
            cameraAccess.pinspo$setPosition(position);
            cameraAccess.pinspo$setRotation(yRot, xRot);
            cameraAccess.pinspo$setDetached(detached);
            setFov(cameraAccess, fov);
            refreshFrustum(camera, cameraAccess);
            try {
                rendererAccess.pinspo$extractCamera(deltaTracker, partialTick);
            } catch (Throwable ignored) {
            }
            busy = false;
        }
    }

    /** Same projection the game sets up each frame, just with the camera's own field of view. */
    private static void setFov(CameraAccessor access, float fov) {
        Minecraft client = Minecraft.getInstance();
        access.pinspo$setFov(fov);
        access.pinspo$setupPerspective(0.05F, access.pinspo$depthFar(), fov,
                client.getWindow().getWidth(), client.getWindow().getHeight());
    }

    private static void refreshFrustum(Camera camera, CameraAccessor access) {
        access.pinspo$prepareCullFrustum(camera.getViewRotationMatrix(new Matrix4f()),
                access.pinspo$cullProjection(), camera.position());
    }

    private static TextureTarget target(CameraPoints.Point point, int width, int height) {
        TextureTarget target = TARGETS.get(point);
        if (target == null) {
            target = new TextureTarget("PinSpo camera", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
            TARGETS.put(point, target);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }

    /** Shows the camera's latest frame; false while it has not rendered one yet. */
    public static boolean draw(GuiGraphicsExtractor guiGraphics, CameraPoints.Point point,
                               int x, int y, int width, int height) {
        TextureTarget target = TARGETS.get(point);
        if (target == null || !READY.contains(point) || target.getColorTextureView() == null) {
            return false;
        }
        guiGraphics.blit(target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x, y, x + width, y + height, 0.0F, 1.0F, 1.0F, 0.0F);
        return true;
    }

    public static void release(CameraPoints.Point point) {
        READY.remove(point);
        TextureTarget target = TARGETS.remove(point);
        if (target != null) {
            target.destroyBuffers();
        }
    }

    public static void releaseAll() {
        for (CameraPoints.Point point : List.copyOf(TARGETS.keySet())) {
            release(point);
        }
    }
}
