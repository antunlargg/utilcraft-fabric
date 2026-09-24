package net.xntnlr.chestcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.xntnlr.chestcraft.network.ChestcraftPayloads;
import net.xntnlr.chestcraft.server.CraftPanelServer;

public class Chestcraft implements ModInitializer {
    @Override
    public void onInitialize() {
        ChestcraftPayloads.registerTypes();

        CraftPanelServer.registerReceivers();

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            CraftPanelServer.onDisconnect(handler.player));
    }
}