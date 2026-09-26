package org.mintype.playerlog;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Playerlog implements ModInitializer {

    public static final String MOD_ID = "playerlog";

    private static final Map<UUID, Long> damageCooldowns = new HashMap<>();

    @Override
    public void onInitialize() {

        ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, baseDamage, damageTaken, blocked) -> {

                    ModConfig config = ModConfig.INSTANCE;

                    if (!config.enabled) {
                        return;
                    }

                    if (!config.notifications.damage) {
                        return;
                    }

                    if (!(entity instanceof ServerPlayer damagedPlayer)) {
                        return;
                    }

                    if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                            && !config.notifications.fire) {
                        return;
                    }

                    if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                            && !config.notifications.fallDamage) {
                        return;
                    }

                    if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                            && !config.notifications.projectileDamage) {
                        return;
                    }

                    if (damageTaken < config.damage.minimumDamage) {
                        return;
                    }

                    MinecraftServer server = damagedPlayer.level().getServer();

                    if (server == null) {
                        return;
                    }

                    boolean lowHealth =
                            damagedPlayer.getHealth() < config.damage.lowHealthThreshold;

                    if (damageTaken >= config.damage.minimumDamage || lowHealth) {
                        Component message = createDamageMessage(
                                damagedPlayer,
                                source,
                                damageTaken
                        );

                        notifyRecipients(server, message);
                    }
                }
        );

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        registerCommands(dispatcher)
        );
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
                Commands.literal("playerlog")

                        // /playerlog
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal(
                                            "PlayerLog is "
                                                    + (ModConfig.INSTANCE.enabled
                                                    ? "enabled"
                                                    : "disabled")
                                    ),
                                    false
                            );

                            return 1;
                        })

                        // /playerlog enable
                        .then(Commands.literal("enable")
                                .requires(source -> {
                                    // Allow console
                                    if (source.getEntity() == null) return true;

                                    // Allow operators
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        return source.getServer()
                                                .getPlayerList()
                                                .isOp(new NameAndId(
                                                        player.getGameProfile()
                                                ));
                                    }

                                    return false;
                                })
                                .executes(context -> {
                                    ModConfig.INSTANCE.enabled = true;

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "PlayerLog enabled."
                                            ),
                                            true
                                    );

                                    return 1;
                                }))

                        // /playerlog disable
                        .then(Commands.literal("disable")
                                .requires(source -> {
                                    // Allow console
                                    if (source.getEntity() == null) return true;

                                    // Allow operators
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        return source.getServer()
                                                .getPlayerList()
                                                .isOp(new NameAndId(
                                                        player.getGameProfile()
                                                ));
                                    }

                                    return false;
                                })
                                .executes(context -> {
                                    ModConfig.INSTANCE.enabled = false;

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "PlayerLog disabled."
                                            ),
                                            true
                                    );

                                    return 1;
                                }))

                        // /playerlog reload
                        .then(Commands.literal("reload")
                                .requires(source -> {
                                    // Allow console
                                    if (source.getEntity() == null) return true;

                                    // Allow operators
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        return source.getServer()
                                                .getPlayerList()
                                                .isOp(new NameAndId(
                                                        player.getGameProfile()
                                                ));
                                    }

                                    return false;
                                })
                                .executes(context -> {
                                    ModConfig.INSTANCE = ModConfig.load();

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "PlayerLog config reloaded."
                                            ),
                                            true
                                    );

                                    return 1;
                                }))

                        // /playerlog tp <player>
                        .then(Commands.literal("tp")
                                .requires(source -> {

                                    // Console is NOT allowed
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        return false;
                                    }

                                    // Allow operators
                                    if (source.getServer()
                                            .getPlayerList()
                                            .isOp(new NameAndId(
                                                    player.getGameProfile()
                                            ))) {
                                        return true;
                                    }

                                    // Allow spectators
                                    return player.gameMode
                                            .getGameModeForPlayer()
                                            == GameType.SPECTATOR;
                                })
                                .then(Commands.argument(
                                                        "player",
                                                        EntityArgument.player()
                                                )
                                                .executes(context -> {
                                                    ServerPlayer target =
                                                            EntityArgument.getPlayer(
                                                                    context,
                                                                    "player"
                                                            );

                                                    ServerPlayer executor =
                                                            context.getSource()
                                                                    .getPlayerOrException();

                                                    executor.teleportTo(
                                                            target.getX(),
                                                            target.getY(),
                                                            target.getZ()
                                                    );

                                                    context.getSource().sendSuccess(
                                                            () -> Component.literal(
                                                                    "Teleported to "
                                                                            + target.getName()
                                                                            .getString()
                                                            ),
                                                            false
                                                    );

                                                    return 1;
                                                })
                                )
                        )
        );

        // /pl alias
        dispatcher.register(
                Commands.literal("pl")
                        .redirect(
                                dispatcher.getRoot()
                                        .getChild("playerlog")
                        )
        );
    }

    private static MutableComponent createDamageMessage(
            ServerPlayer player,
            DamageSource source,
            float damage
    ) {
        ModConfig config = ModConfig.INSTANCE;

        MutableComponent message = Component.empty()
                .append(Component.literal(config.chat.prefix + " ")
                        .withStyle(ChatFormatting.GRAY))
                .append(player.getName().copy()
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" took ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%.1f", damage))
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal(" damage")
                        .withStyle(ChatFormatting.GRAY));

        if (config.damage.includeSource) {
            message.append(
                    Component.literal(" from ")
                            .withStyle(ChatFormatting.GRAY)
            );

            if (source.getEntity() instanceof ServerPlayer attacker) {
                // Player attacked the player
                message.append(
                        attacker.getName().copy()
                                .withStyle(ChatFormatting.RED)
                );

            } else if (source.getEntity() != null) {
                // Mob / other entity attacked the player
                message.append(
                        source.getEntity().getDisplayName()
                                .copy()
                                .withStyle(ChatFormatting.RED)
                );

            } else {
                // Environmental damage
                message.append(
                        Component.literal(source.getMsgId())
                                .withStyle(ChatFormatting.GOLD)
                );
            }
        }

        if (config.damage.includeHealth) {
            message.append(Component.literal(
                    String.format(
                            " (%.1f/%.1f HP)",
                            player.getHealth(),
                            player.getMaxHealth()
                    )
            ).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (config.chat.includeCoordinates) {
            message.append(Component.literal(
                    String.format(
                            " [%d, %d, %d]",
                            player.blockPosition().getX(),
                            player.blockPosition().getY(),
                            player.blockPosition().getZ()
                    )
            ).withStyle(ChatFormatting.DARK_AQUA));
        }

        if (config.chat.includeTeleport) {
            int x = player.blockPosition().getX();
            int y = player.blockPosition().getY();
            int z = player.blockPosition().getZ();

            message.append(
                    Component.literal(" ")
                            .withStyle(ChatFormatting.GRAY)
            );

            message.append(
                    Component.literal("[Teleport]")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(
                                            new ClickEvent.RunCommand(
                                                    "/pl tp " + player.getName().getString()
                                            )
                                    )
                                    .withHoverEvent(
                                            new HoverEvent.ShowText(
                                                    Component.literal(
                                                            String.format(
                                                                    "Teleport to %s\n(%d, %d, %d)",
                                                                    player.getName().getString(),
                                                                    x,
                                                                    y,
                                                                    z
                                                            )
                                                    )
                                            )
                                    )
                            )
            );
        }

        return message;
    }

    private static void notifyRecipients(
            MinecraftServer server,
            Component message
    ) {
        ModConfig.Recipients recipients = ModConfig.INSTANCE.recipients;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {

            boolean isOperator =
                    server.getPlayerList().isOp(player.nameAndId());

            boolean isSpectator =
                    player.gameMode.getGameModeForPlayer()
                            == GameType.SPECTATOR;

            boolean shouldNotify =
                    (recipients.operators && isOperator)
                            || (recipients.spectators && isSpectator);

            if (shouldNotify) {
                player.sendSystemMessage(message);
            }
        }
    }
}