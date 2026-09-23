package net.bennyboops.modid;

import net.bennyboops.modid.client.PocketDimensionEffects;
import net.bennyboops.modid.client.PocketSkyRenderer;
import net.bennyboops.modid.client.RuneParticle;
import net.bennyboops.modid.particle.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class PocketReposeClient implements ClientModInitializer {
    /** Matches the "effects" field of the pocket dimension type. */
    public static final Identifier POCKET_EFFECTS_ID = new Identifier(PocketRepose.MOD_ID, "pocket");
    private static final String DIMENSION_PREFIX = "pocket_dimension_";

    private static final PocketSkyRenderer SKY_RENDERER = new PocketSkyRenderer();
    private static final Set<RegistryKey<World>> REGISTERED = new HashSet<>();

    @Override
    public void onInitializeClient() {
        DimensionRenderingRegistry.registerDimensionEffects(POCKET_EFFECTS_ID, new PocketDimensionEffects());
        ParticleFactoryRegistry.getInstance().register(ModParticles.RUNE, RuneParticle.Factory::new);
        ClientTickEvents.END_CLIENT_TICK.register(PocketReposeClient::spawnEnchantSwirl);

        // Pocket worlds are created at runtime with dynamic keys, so the sky renderer is
        // attached the first time the client finds itself inside one of them.
        ClientTickEvents.END_CLIENT_TICK.register(PocketReposeClient::registerSkyForCurrentWorld);
    }

    private static final Random SWIRL_RANDOM = Random.create();

    /**
     * Layers vanilla enchanting-glyph particles on top of the biome's rune particles.
     * Glyphs converge on random points in the air around the player, like an enchanting table.
     */
    private static void spawnEnchantSwirl(MinecraftClient client) {
        if (client.world == null || client.player == null || client.isPaused()) {
            return;
        }
        if (!isPocketDimension(client.world.getRegistryKey())) {
            return;
        }
        if (SWIRL_RANDOM.nextInt(9) != 0) {
            return;
        }
        // Pick a focus point near the player, then a glyph offset around it.
        double fx = client.player.getX() + (SWIRL_RANDOM.nextDouble() - 0.5) * 24.0;
        double fy = client.player.getY() + 1.0 + (SWIRL_RANDOM.nextDouble() - 0.3) * 8.0;
        double fz = client.player.getZ() + (SWIRL_RANDOM.nextDouble() - 0.5) * 24.0;
        for (int i = 0; i < 2; i++) {
            double dx = (SWIRL_RANDOM.nextDouble() - 0.5) * 3.0;
            double dy = (SWIRL_RANDOM.nextDouble() - 0.5) * 3.0;
            double dz = (SWIRL_RANDOM.nextDouble() - 0.5) * 3.0;
            client.world.addParticle(ParticleTypes.ENCHANT, fx, fy, fz, dx, dy, dz);
        }
    }

    private static boolean isPocketDimension(RegistryKey<World> key) {
        Identifier id = key.getValue();
        return id.getNamespace().equals(PocketRepose.MOD_ID) && id.getPath().startsWith(DIMENSION_PREFIX);
    }

    private static void registerSkyForCurrentWorld(MinecraftClient client) {
        if (client.world == null) {
            return;
        }
        RegistryKey<World> key = client.world.getRegistryKey();
        if (REGISTERED.contains(key)) {
            return;
        }
        Identifier id = key.getValue();
        if (!id.getNamespace().equals(PocketRepose.MOD_ID) || !id.getPath().startsWith(DIMENSION_PREFIX)) {
            return;
        }
        if (DimensionRenderingRegistry.getSkyRenderer(key) == null) {
            DimensionRenderingRegistry.registerSkyRenderer(key, SKY_RENDERER);
        }
        REGISTERED.add(key);
    }
}
