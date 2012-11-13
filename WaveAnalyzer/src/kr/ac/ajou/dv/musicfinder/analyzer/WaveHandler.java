package kr.ac.ajou.dv.musicfinder.analyzer;

import kr.ac.ajou.dv.musicfinder.lib.DbWorker;
import kr.ac.ajou.dv.musicfinder.lib.FftHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;

public class WaveHandler {
    public static final int CHUNK_SIZE = 4096;
    public static final int WINDOW_SIZE = 16;
    public static final int SUB_CHUNK_SIZE = (CHUNK_SIZE / WINDOW_SIZE);
    public static final int SHORT_BYTES = (Short.SIZE / Byte.SIZE);
    public static final int NUMBER_OF_SAMPLES = (CHUNK_SIZE / SHORT_BYTES);

    public void handle(File wav, DbWorker dbWorker) throws IOException, InvalidWaveFormatException {
        FileInputStream fis = new FileInputStream(wav);
        byte[] header = new byte[36];

        fis.read(header);
        if (!new String(header, 0, 4).equals("RIFF"))
            throw new InvalidWaveFormatException("This file does not conform RIFF.");

        if (!new String(header, 8, 4).equals("WAVE"))
            throw new InvalidWaveFormatException("This is not a WAVE file.");

        String subChunk1Id = new String(header, 12, 4);
        int subChunk1Size = ByteBuffer.wrap(header, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        short audioFormat = ByteBuffer.wrap(header, 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        short numChannels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        int sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        short bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();

        if (!subChunk1Id.equals("fmt ") ||
                subChunk1Size != 16 ||
                audioFormat != 1 ||
                numChannels != 1 ||
                sampleRate != 32000 ||
                bitsPerSample != 16)
            throw new InvalidWaveFormatException("Invalid 'fmt' sub-chunk.");

        byte[] subChunk2Header = new byte[8];
        fis.read(subChunk2Header);

        String subChunk2Id = new String(subChunk2Header, 0, 4);
        if (!subChunk2Id.equals("data"))
            throw new InvalidWaveFormatException("Invalid 'data' sub-chunk.");

        ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<byte[]>(WINDOW_SIZE);

        int position = 0;
        while (true) {
            byte[] subChunk = new byte[SUB_CHUNK_SIZE];
            if (fis.read(subChunk) < SUB_CHUNK_SIZE) break;
            // In an initial state, the queue is not filled. We need the full 8 sub-chunks for the FFT transform.
            if (queue.offer(subChunk)) continue;
            queue.poll();
            queue.offer(subChunk);

            byte[] chunk = new byte[CHUNK_SIZE];
            int offset = 0;
            for (byte[] b : queue) {
                System.arraycopy(b, 0, chunk, offset, SUB_CHUNK_SIZE);
                offset += SUB_CHUNK_SIZE;
            }
            ByteBuffer sampleBuffer = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);

            short[] pcm = new short[NUMBER_OF_SAMPLES];
            for (int i = 0; i < NUMBER_OF_SAMPLES; i++)
                pcm[i] = sampleBuffer.getShort();

            int[] intensivePoints = FftHelper.getIntensivePointsAfterFft(pcm);
            dbWorker.work(position++, DbWorker.fuzz(intensivePoints));
        }
    }
}
