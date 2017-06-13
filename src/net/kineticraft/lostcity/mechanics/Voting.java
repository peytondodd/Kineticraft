package net.kineticraft.lostcity.mechanics;

import com.vexsoftware.votifier.model.VotifierEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.kineticraft.lostcity.Core;
import net.kineticraft.lostcity.config.Configs;
import net.kineticraft.lostcity.config.configs.VoteConfig;
import net.kineticraft.lostcity.data.JsonData;
import net.kineticraft.lostcity.data.Jsonable;
import net.kineticraft.lostcity.data.QueryTools;
import net.kineticraft.lostcity.data.wrappers.KCPlayer;
import net.kineticraft.lostcity.utils.TextBuilder;
import net.kineticraft.lostcity.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles players voting for the server.
 *
 * Created by Kneesnap on 6/3/2017.
 */
public class Voting extends Mechanic {

    @EventHandler
    public void onVote(VotifierEvent evt) {
        handleVote(evt.getVote().getUsername());
    }

    /**
     * Handle a player voting.
     * @param username
     */
    public static void handleVote(String username) {

        TextBuilder textBuilder = new TextBuilder(username).color(ChatColor.AQUA)
                .append(" voted and received a reward! Vote ").color(ChatColor.GRAY).append("HERE").underline().bold()
                .openURL(Configs.getMainConfig().getVoteURL()).color(ChatColor.AQUA);
        Bukkit.broadcast(textBuilder.create());

        if (!getMonthName().equals(Configs.getVoteData().getMonth()))
            resetVotes(); // A new month! Time to reset the votes.

        VoteConfig data = Configs.getVoteData();
        data.setTotalVotes(data.getTotalVotes() + 1); // Increment the total vote count

        int toParty = data.getVotesUntilParty() - 1; // Decrement the amount of votes until party.
        data.setVotesUntilParty(toParty);
        if (toParty > 0) {
            if (toParty % 5 == 0 || toParty <= 10)
                Core.kineticaMessage("Thanks for voting " + ChatColor.YELLOW + username + ChatColor.WHITE + "! We need "
                        + ChatColor.YELLOW + toParty + ChatColor.WHITE + " more votes for a party.");
        } else {
            doVoteParty();
        }

        QueryTools.getData(username, player ->  {
            player.setPendingVotes(player.getPendingVotes() + 1);
            if (player.isOnline())
                giveRewards(player.getPlayer());
        });
    }

    /**
     * Activate a vote party.
     */
    public static void doVoteParty() {
        Core.kineticaMessage("Wooo! We made it! Parrrty!");
        Configs.getVoteData().setVotesUntilParty(Configs.getVoteData().getVotesPerParty()); // Reset counter.

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack reward = generatePartyReward();
            Utils.giveItem(player, reward);
            player.sendMessage(ChatColor.GOLD + "You received " + ChatColor.YELLOW + reward.getAmount() + "x"
                    + Utils.getItemName(reward) + ChatColor.GOLD + " from the vote party.");
        }
    }

    /**
     * Give a player their pending vote rewards.
     * @param player
     */
    public static void giveRewards(Player player) {
        KCPlayer p = KCPlayer.getWrapper(player);
        int pending = p.getPendingVotes();

        if (pending <= 0)
            return; // They don't have any votes.

        player.sendMessage(ChatColor.GOLD + "Receiving " + ChatColor.YELLOW + pending + ChatColor.GOLD
                + " vote reward" + (pending > 1 ? "s" : "") + ".");

        for (int i = 0; i < pending; i++) {
            Configs.getVoteData().getNormal().getValues().forEach(j -> Utils.giveItem(player, j.getItem()));
            player.setLevel(player.getLevel() + 10); // Add 10 XP Levels
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);

        // Give achievement rewards
        for (VoteAchievement va : Configs.getVoteData().getAchievements().getValues()) {
            if (va.getVotesNeeded() > p.getTotalVotes() && p.getTotalVotes() + pending >= va.getVotesNeeded()) {
                Utils.giveItem(player, va.getItem());
                player.sendMessage(ChatColor.GREEN + "You have received a special reward for voting "
                        + va.getVotesNeeded() + " times.");
            }
        }

        p.setPendingVotes(0);
        p.setMonthlyVotes(p.getMonthlyVotes() + pending);
        p.setTotalVotes(p.getTotalVotes() + pending);

        calculateTopVoter();
    }

    /**
     * Generate a random vote party reward.
     * @return item
     */
    public static ItemStack generatePartyReward() {
        VoteConfig data = Configs.getVoteData();
        if (data.getParty().isEmpty()) // No vote rewards.
            return new ItemStack(Material.DIRT);

        PartyReward test = Utils.randElement(data.getParty()); // Get the reward we'll attempt to give them.
        return Utils.randChance(test.getChance()) ? test.getItem() : generatePartyReward();
    }

    /**
     * Reset monthly vote count.
     */
    public static void resetVotes() {
        VoteConfig data = Configs.getVoteData();
        data.setMonth(getMonthName()); // Do this before query so if two votes come in quickly it won't run twice.

        QueryTools.queryData(players -> {
            players.forEach(p -> {
                if (p.getMonthlyVotes() <= 0)
                    return;
                p.setMonthlyVotes(0);
                p.writeData();
            });

            data.setTopVoter(null);
            Core.announce("Votes have reset for the month of " + getMonthName()
                    + "! Better start voting to get top voter! (.vote)");
        });
    }

    /**
     * Get the name of the current month.
     * @return monthName
     */
    private static String getMonthName() {
        return Calendar.getInstance().getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH);
    }

    /**
     * Recalculate the top voter for this month.
     */
    public static void calculateTopVoter() {
        VoteConfig data = Configs.getVoteData();

        QueryTools.queryData(players -> {
            KCPlayer topVoter = players.sorted(Comparator.comparing(KCPlayer::getMonthlyVotes)).collect(Collectors.toList()).get(0);
            if (topVoter.getUuid().equals(data.getTopVoter()))
                return; // The top voter hasn't changed.

            data.setTopVoter(topVoter.getUuid());
            Core.announce(ChatColor.YELLOW + topVoter.getUsername() + ChatColor.RED
                    + " is the new top voter! Votes: " + ChatColor.YELLOW + topVoter.getMonthlyVotes());

            if (topVoter.isOnline()) {
                Player player = topVoter.getPlayer();
                player.sendMessage(ChatColor.LIGHT_PURPLE + " * You are now the top voter this month. *");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1F);
                topVoter.updatePlayer();
            }
        });
    }

    @AllArgsConstructor @Data
    public static class VoteAchievement implements Jsonable {
        private int votesNeeded;
        private ItemStack item;

        public VoteAchievement(JsonData data) {
            load(data);
        }

        @Override
        public void load(JsonData data) {
            setVotesNeeded(data.getInt("needed"));
            setItem(data.getItem("item"));
        }

        @Override
        public JsonData save() {
            return new JsonData().setNum("needed", getVotesNeeded()).setItem("item", getItem());
        }
    }

    @AllArgsConstructor @Data
    public static class PartyReward implements Jsonable {
        private int chance;
        private ItemStack item;

        public PartyReward(JsonData data) {
            load(data);
        }

        @Override
        public void load(JsonData data) {
            setChance(Math.max(0, data.getInt("chance")));
            setItem(data.getItem("item"));
        }

        @Override
        public JsonData save() {
            return new JsonData().setNum("chance", getChance()).setItem("item", getItem());
        }
    }
}