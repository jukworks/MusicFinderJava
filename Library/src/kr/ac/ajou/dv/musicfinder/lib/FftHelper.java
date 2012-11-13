package kr.ac.ajou.dv.musicfinder.lib;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class FftHelper {
    public static int[] getIntensivePointsAfterFft(short[] pcmChunk) {
        DoubleFFT_1D fftDo = new DoubleFFT_1D(pcmChunk.length);
        double[] fft = new double[pcmChunk.length * 2];
        for (int i = 0; i < pcmChunk.length; i++)
            fft[i] = (double) pcmChunk[i];
        fftDo.realForwardFull(fft);

        int fftResultLen = (pcmChunk.length / 2) + 1;
        long[] fftAbs = new long[fftResultLen];
        for (int i = 0; i < fftResultLen; i++) {
            double real = fft[i * 2];
            double complex = fft[i * 2 + 1];
            fftAbs[i] = (long) (real * real + complex * complex);
        }

        int[] intensivePoints = new int[4];
        intensivePoints[0] = findMaximumPoint(fftAbs, 0, fftResultLen / 4);
        intensivePoints[1] = findMaximumPoint(fftAbs, fftResultLen / 4 + 1, fftResultLen / 2);
        intensivePoints[2] = findMaximumPoint(fftAbs, fftResultLen / 2 + 1, fftResultLen * 3 / 4);
        intensivePoints[3] = findMaximumPoint(fftAbs, fftResultLen * 3 / 4 + 1, fftResultLen);

        return intensivePoints;
    }

    private static int findMaximumPoint(long[] fft, int start, int end) {
        int point = 0;
        long max = Long.MIN_VALUE;
        for (int i = start; i < end; i++)
            if (fft[i] > max) {
                max = fft[i];
                point = i;
            }
        return point;
    }

}
