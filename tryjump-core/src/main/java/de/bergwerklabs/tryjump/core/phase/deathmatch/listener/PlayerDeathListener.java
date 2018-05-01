package de.bergwerklabs.tryjump.core.phase.deathmatch.listener;

import de.bergwerklabs.framework.bedrock.api.PlayerRegistry;
import de.bergwerklabs.framework.commons.spigot.scoreboard.LabsScoreboard;
import de.bergwerklabs.framework.commons.spigot.scoreboard.Row;
import de.bergwerklabs.tryjump.core.Jumper;
import de.bergwerklabs.tryjump.core.TryJumpSession;
import de.bergwerklabs.tryjump.core.phase.deathmatch.DeathmatchPhase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Created by Yannic Rieger on 11.02.2018.
 *
 * <p>
 *
 * @author Yannic Rieger
 */
class PlayerDeathListener extends DeathmachtListener {

  PlayerDeathListener(DeathmatchPhase phase, TryJumpSession session) {
    super(phase, session);
  }

  @EventHandler
  private void onPlayerDeath(PlayerDeathEvent event) {
    final PlayerRegistry<Jumper> registry = this.tryJump.getPlayerRegistry();
    final Player killed = event.getEntity();
    final Player killer = killed.getKiller();

    final Jumper killedJumper = registry.getPlayer(killed.getUniqueId());
    final Jumper killingJumper = registry.getPlayer(killer.getUniqueId());
    final int livesLeft = killedJumper.decrementLife();

    if (livesLeft <= 0) {
      // TODO: set spectator since player has no lives left.
      Bukkit.getServer().broadcastMessage("TOT");
    }

    new PotionEffect(PotionEffectType.REGENERATION, 20, 20, false, false).apply(killer);

    killingJumper.updateKills();

    this.jumpers.forEach(
        jumper -> {
          // TODO: use rank color and maybe use different messages each time a player dies.
          this.tryJump
              .getMessenger()
              .message(
                  "§a"
                      + killer.getDisplayName()
                      + " §7hat §a"
                      + killed.getDisplayName()
                      + " §7getötet.",
                  jumper.getPlayer());

          final LabsScoreboard scoreboard = jumper.getScoreboard();
          final Row row = scoreboard.getPlayerSpecificRows().get(killed.getUniqueId());
          row.setText("§7" + killed.getDisplayName() + ": §b" + livesLeft);
        });
  }
}