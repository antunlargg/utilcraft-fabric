package net.xntnlr.chestcraft.client;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.xntnlr.chestcraft.network.ChestcraftPayloads;

public class ChestcraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ChestcraftPayloads.PanelStatePayload.ID, (payload, context) ->
            context.client().execute(() ->
                ChestcraftClientState.applyServerState(payload.cursor(), payload.grid(), payload.result())));
    }
}