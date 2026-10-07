package com.maidweapon.forge.client;

import net.minecraft.client.sounds.AudioStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;

/** One streaming channel: once-only first passage followed by the author's looping OGG. */
public final class BlackFoxMusicStream implements AudioStream {
    private final AudioStream intro, loop;
    private boolean inIntro = true, closed;
    private long introBytesRemaining;
    public BlackFoxMusicStream(AudioStream intro, AudioStream loop, long introFrames) throws IOException {
        this.intro = intro; this.loop = loop;
        introBytesRemaining = introFrames * intro.getFormat().getFrameSize();
        if (!intro.getFormat().matches(loop.getFormat())) {
            try { intro.close(); } finally { loop.close(); }
            throw new IOException("Black Fox intro/loop formats must match");
        }
    }
    @Override public AudioFormat getFormat() { return loop.getFormat(); }
    @Override public ByteBuffer read(int size) throws IOException {
        if (closed) throw new IOException("Music stream closed");
        if (!inIntro) return loop.read(size);
        ByteBuffer first = intro.read(size);
        // Discard the short Vorbis final-block padding rather than delaying the
        // musical join; sample count comes from the generated resource manifest.
        first.limit(first.position() + (int) Math.min(first.remaining(), introBytesRemaining));
        introBytesRemaining -= first.remaining();
        if (first.remaining() >= size) return first;
        // Fill the same audio buffer across the boundary, without stopping/restarting a channel.
        inIntro = false; intro.close();
        ByteBuffer second = loop.read(size - first.remaining());
        ByteBuffer joined = ByteBuffer.allocateDirect(first.remaining() + second.remaining());
        joined.put(first).put(second).flip();
        return joined;
    }
    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try { if (inIntro) intro.close(); } finally { loop.close(); }
    }
}
