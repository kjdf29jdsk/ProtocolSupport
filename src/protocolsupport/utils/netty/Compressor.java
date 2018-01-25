package protocolsupport.utils.netty;

import java.util.Arrays;
import java.util.zip.Deflater;

import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import protocolsupport.ProtocolSupport;
import protocolsupport.utils.Utils;

public class Compressor {

	protected static final int compressionLevel = Utils.getJavaPropertyValue("compressionlevel", 0, Integer::parseInt);

	static {
		ProtocolSupport.logInfo("Compression level: "+compressionLevel);
	}

	protected static final Recycler<Compressor> recycler = new Recycler<Compressor>() {
		@Override
		protected Compressor newObject(Handle<Compressor> handle) {
			return new Compressor(handle);
		}
	};

	public static Compressor create() {
		return recycler.get();
	}

	private final Deflater deflater = new Deflater(compressionLevel);
	private final Deflater legacyDeflater = new Deflater(3);
	private final Handle<Compressor> handle;
	protected Compressor(Handle<Compressor> handle) {
		this.handle = handle;
	}

	public byte[] compress(byte[] input) {
		return compress(deflater, input);
	}

	public byte[] compressLegacy(byte[] input) {
		return compress(legacyDeflater, input);
	}

	private byte[] compress(Deflater deflater, byte[] input) {
		deflater.setInput(input);
		deflater.finish();
		byte[] compressedBuf = new byte[((input.length * 11) / 10) + 50];
		int size = deflater.deflate(compressedBuf);
		deflater.reset();
		return Arrays.copyOf(compressedBuf, size);
	}

	public void recycle() {
		handle.recycle(this);
	}

	public static byte[] compressStatic(byte[] input) {
		Compressor compressor = create();
		try {
			return compressor.compress(input);
		} finally {
			compressor.recycle();
		}
	}

	public static byte[] compressLegacyStatic(byte[] input) {
		Compressor compressor = create();
		try {
			return compressor.compressLegacy(input);
		} finally {
			compressor.recycle();
		}
	}

}
