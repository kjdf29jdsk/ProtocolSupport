package protocolsupport.zplatform.impl.spigot.network.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import protocolsupport.protocol.serializer.MiscSerializer;
import protocolsupport.protocol.serializer.VarNumberSerializer;
import protocolsupport.utils.netty.Compressor;

public class SpigotLegacyPacketCompressor extends net.minecraft.server.PacketCompressor {

    private final Compressor compressor = Compressor.create();
    private final int threshold;

    public SpigotLegacyPacketCompressor(int threshold) {
        super(threshold);
        this.threshold = threshold;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        compressor.recycle();
    }

    @Override
    protected void a(ChannelHandlerContext ctx, ByteBuf from, ByteBuf to)  {
        int readable = from.readableBytes();
        if (readable == 0) {
            return;
        }
        if (readable < this.threshold) {
            VarNumberSerializer.writeVarInt(to, 0);
            to.writeBytes(from);
        } else {
            VarNumberSerializer.writeVarInt(to, readable);
            to.writeBytes(compressor.compressLegacy(MiscSerializer.readAllBytes(from)));
        }
    }

    @Override
    protected void encode(ChannelHandlerContext var1, ByteBuf var2, ByteBuf var3) {
        this.a(var1, var2, var3);
    }

}
