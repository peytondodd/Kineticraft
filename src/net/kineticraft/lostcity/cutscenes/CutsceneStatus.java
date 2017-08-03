package net.kineticraft.lostcity.cutscenes;

import lombok.Getter;
import net.kineticraft.lostcity.mechanics.metadata.Metadata;
import net.kineticraft.lostcity.mechanics.metadata.MetadataManager;
import net.kineticraft.lostcity.utils.Utils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Holds information about a player and their current cutscene.
 * Created by Kneesnap on 7/22/2017.
 */
@Getter
public class CutsceneStatus {

    private Cutscene cutscene;
    private List<Player> players;
    private int stageId = 0;
    private Location startLocation;
    private Map<String, Entity> entityMap = new HashMap<>();

    public CutsceneStatus(Cutscene cutscene, List<Player> players) {
        this.cutscene = cutscene;
        this.players = new ArrayList<>(players);
        this.startLocation = players.get(0).getLocation();
        getPlayers().forEach(p -> Cutscenes.getDataMap().put(p, this)); // Players must be listed under this cutscene before the camera is created.
        makeCamera(EntityType.ARMOR_STAND);
    }

    /**
     * Create the new camera for the cutscene, and attach players.
     * @param type
     */
    public void makeCamera(EntityType type) {
        Location loc = getCamera() != null ? getCamera().getLocation() : getStartLocation();
        if (getCamera() != null) // Toss the old camera.
            getCamera().remove();

        getEntityMap().put("Camera", loc.getWorld().spawnEntity(loc, type));
        Utils.giveInfinitePotion(getCamera(), PotionEffectType.INVISIBILITY);
        getCamera().setAI(false); // Disable the AI of the camera.
        getCamera().setGravity(false);
        getCamera().setInvulnerable(true); // Disable all damage.

        // Mount all viewers to the camera.
        getPlayers().forEach(p -> {
            p.setGameMode(GameMode.SPECTATOR);
            p.setSpectatorTarget(getCamera());
        });
    }

    /**
     * Get the current stage.
     * @return stage
     */
    public CutsceneStage getStage() {
        return getCutscene().getStages().getValueSafe(getStageId());
    }

    /**
     * Execute the current stage code, then move to the next stage.
     */
    public void nextStage() {
        if (getCutscene().getStages().hasIndex(getStageId())) {
            getStage().action(this); // Execute this stage.
            this.stageId++; // Go to the next stage.
            return;
        }

        finish(); //Cutscene has ended.
    }

    /**
     * Finish this cutscene, remove entities and finish players.
     */
    public void finish() {
        new HashSet<>(getEntityMap().keySet()).forEach(this::removeEntity);
        new ArrayList<>(getPlayers()).forEach(this::finish);
    }

    /**
     * Remove this entity prop from the cutscene, and from the world.
     * @param name
     */
    public void removeEntity(String name) {
        Entity e = getEntityMap().remove(name);
        if (!MetadataManager.hasMetadata(e, Metadata.CUTSCENE_KEEP))
            e.remove();
    }

    public LivingEntity getCamera() {
        return (LivingEntity) getEntityMap().get("Camera");
    }

    /**
     * Finish the cutscene for the given player.
     * @param p
     */
    public void finish(Player p) {
        Cutscenes.removeState(p);
        p.setGameMode(GameMode.SURVIVAL);
        Utils.safeTp(p, getStartLocation());
        getPlayers().remove(p);
    }

    @Override
    public String toString() {
        return "[" + getCutscene().getName() + "/" + getStageId() + "]";
    }
}