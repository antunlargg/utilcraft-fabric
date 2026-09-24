package net.xntnlr.chestcraft.network;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class ChestcraftPayloads {

    private ChestcraftPayloads() {}

    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(PanelActionPayload.ID, PanelActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PanelSessionPayload.ID, PanelSessionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PanelStatePayload.ID, PanelStatePayload.CODEC);
    }

    public record PanelActionPayload(byte kind, byte slot, byte button, boolean shift) implements CustomPayload {

        public static final byte KIND_ARMOR = 0;
        public static final byte KIND_GRID = 1;
        public static final byte KIND_RESULT = 2;
        public static final byte KIND_DRAG_BEGIN = 3;
        public static final byte KIND_DRAG_SLOT = 4;
        public static final byte KIND_DRAG_END = 5;

        public static final byte AREA_ARMOR = 0;
        public static final byte AREA_GRID = 1;

        public static final CustomPayload.Id<PanelActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestcraft", "panel_action"));

        public static final PacketCodec<RegistryByteBuf, PanelActionPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeByte(value.kind());
                buf.writeByte(value.slot());
                buf.writeByte(value.button());
                buf.writeBoolean(value.shift());
            },
            buf -> new PanelActionPayload(buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean()));

        @Override
        public CustomPayload.Id<PanelActionPayload> getId() {
            return ID;
        }
    }

    public record PanelSessionPayload(byte kind) implements CustomPayload {
        public static final CustomPayload.Id<PanelSessionPayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestcraft", "panel_session"));

        public static final PanelSessionPayload OPEN = new PanelSessionPayload((byte) 0);
        public static final PanelSessionPayload CLOSE = new PanelSessionPayload((byte) 1);

        public static final PacketCodec<RegistryByteBuf, PanelSessionPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeByte(value.kind()),
            buf -> new PanelSessionPayload(buf.readByte()));

        @Override
        public CustomPayload.Id<PanelSessionPayload> getId() {
            return ID;
        }
    }

    public record PanelStatePayload(ItemStack cursor, ItemStack[] grid, ItemStack result) implements CustomPayload {
        public static final CustomPayload.Id<PanelStatePayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestcraft", "panel_state"));

        public static final PacketCodec<RegistryByteBuf, PanelStatePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, value.cursor());
                for (ItemStack stack : value.grid()) {
                    ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, stack);
                }
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, value.result());
            },
            buf -> {
                ItemStack cursor = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
                ItemStack[] grid = new ItemStack[4];
                for (int i = 0; i < 4; i++) {
                    grid[i] = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
                }
                ItemStack result = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
                return new PanelStatePayload(cursor, grid, result);
            });

        @Override
        public CustomPayload.Id<PanelStatePayload> getId() {
            return ID;
        }
    }
}