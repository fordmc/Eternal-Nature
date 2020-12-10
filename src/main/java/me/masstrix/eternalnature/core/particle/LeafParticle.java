/*
 * Copyright 2019 Matthew Denton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.masstrix.eternalnature.core.particle;

import me.masstrix.eternalnature.EternalEngine;
import me.masstrix.eternalnature.api.Leaf;
import me.masstrix.eternalnature.core.entity.shadow.ArmorStandBodyPart;
import me.masstrix.eternalnature.core.entity.shadow.ItemSlot;
import me.masstrix.eternalnature.core.entity.shadow.ShadowArmorStand;
import me.masstrix.eternalnature.core.item.CustomItem;
import me.masstrix.eternalnature.events.LeafSpawnEvent;
import me.masstrix.eternalnature.util.MathUtil;
import me.masstrix.eternalnature.util.SimplexNoiseOctave;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Objects;

public class LeafParticle extends BaseParticle implements Leaf {

    private SimplexNoiseOctave movementNoise;
    private ShadowArmorStand leaf;
    private double animationOffset;
    private boolean hasSettled;
    private boolean willFloat;
    private int ticks;
    private double fallRate;
    private double randomArmOffset = degreesToEuler(MathUtil.randomDouble() * 360);

    /**
     * Creates a new leaf effect. Leaves when ticked will slowly fall until they hit a
     * non passable block or there lifetime has ended.
     *
     * @param loc location to spawn the effect at.
     */
    public LeafParticle(Location loc) {
        this(loc, null);
    }

    /**
     * Creates a new leaf effect. Leaves when ticked will slowly fall until they hit a
     * non passable block or there lifetime has ended. Using this method will also call
     * the {@link LeafSpawnEvent}.
     *
     * @param loc    location to spawn the effect at.
     * @param engine engine for the plugin, when this is set wind forces will be applied
     *               to the effect.
     */
    public LeafParticle(Location loc, EternalEngine engine) {
        if (loc == null) return;
        if (engine != null) {
            // Call the spawn ever.
            Bukkit.getScheduler().callSyncMethod(engine.getPlugin(), () -> {
                LeafSpawnEvent event = new LeafSpawnEvent(this);
                Bukkit.getPluginManager().callEvent(event);
                return true;
            });
        }
        lifeTime = MathUtil.randomInt(90, 150);
        fallRate = MathUtil.randomDouble(0.01, 0.1);
        movementNoise = new SimplexNoiseOctave(MathUtil.randomInt(10000));
        willFloat = MathUtil.chance(0.3);

        loc.setYaw(MathUtil.randomInt(0, 360));
        leaf = new ShadowArmorStand(loc);
        leaf.setSmall(true);
        leaf.setMarker(true);
        leaf.setArms(true);
        leaf.setInvisible(true);
        leaf.setSlot(ItemSlot.MAINHAND, CustomItem.LEAF.get());

        // Sets how far away a player has to be to see the particle.
        // This will not be sent to a player if they suddenly become in range of it.
        int renderDistance = 32;

        int distSq = renderDistance * renderDistance;
        for (Player player : Objects.requireNonNull(loc.getWorld()).getPlayers()) {
            if (loc.distanceSquared(player.getLocation()) < distSq) {
                leaf.sendTo(player);
            }
        }
        alive = true;
    }

    @Override
    public boolean hasSettled() {
        return hasSettled;
    }

    @Override
    public void remove() {
        super.remove();
        leaf.remove();
    }

    @Override
    public Location getLocation() {
        return leaf.getLocation();
    }

    @Override
    public void tick() {
        if (lifeTime-- <= 0 || ticks++ > 20 && !leaf.getLocation().clone().add(0, 1, 0).getBlock().isPassable()) {
            remove(); // End the effect if leaf hits the ground
            lifeTime = 0;
        }

        Location loc = leaf.getLocation().clone().add(0, 0.3, 0);
        hasSettled = !loc.getBlock().isPassable();
        boolean inWater = loc.getBlock().getType() == Material.WATER;
        if (inWater && !hasSettled && willFloat) {
            // TODO make leaf particles interact with flowing water.
            //      Also make some of them float on the top.
            //      Water direction is client side so that will need to be worked
            //      out to get the flowing direction.
        }

        // Burn the particle
        if (loc.getBlock().getType() == Material.LAVA) {
            Levelled levelled = (Levelled) loc.getBlock().getBlockData();
            double y = loc.getBlock().getY() + (levelled.getLevel() / (double) levelled.getMaximumLevel());

            // Remove particle if in the lava
            if (loc.getY() < y) {
                this.alive = false;
                remove();
                return;
            }
        }

        animationOffset += 0.05;

        if (wind != null) {
            Vector force = wind.getForce(loc.getBlockX(), loc.getBlockZ())
                    .divide(new Vector(70, 100, 70));
            velocity.add(force);
        }

        velocity.add(new Vector(0, !hasSettled ? -fallRate / 6 : 0, 0));

        if (!hasSettled) {
            velocity.add(new Vector(
                    movementNoise.noise(animationOffset) * 0.01,
                    0,
                    movementNoise.noise(animationOffset + 50) * 0.01));
            leaf.move(velocity.getX(), velocity.getY(), velocity.getZ());
            double poseX = movementNoise.noise(animationOffset) * 0.5;
            double poseY = movementNoise.noise(animationOffset) * 10;
            double poseZ = movementNoise.noise(animationOffset) * 10;
            leaf.setPose(ArmorStandBodyPart.RIGHT_ARM, new Vector(poseX + randomArmOffset, poseY, poseZ));
        } else {
            Vector pose = leaf.getPose(ArmorStandBodyPart.RIGHT_ARM);
            pose.divide(new Vector(1.2, 1.2, 1.2));
            leaf.setPose(ArmorStandBodyPart.RIGHT_ARM, pose);
        }
        velocity.divide(new Vector(2, 2,2 ));
        animationOffset += 0.01;
    }
}
