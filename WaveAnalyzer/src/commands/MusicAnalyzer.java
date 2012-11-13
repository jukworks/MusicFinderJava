package commands;

import kr.ac.ajou.dv.musicfinder.analyzer.InvalidWaveFormatException;
import kr.ac.ajou.dv.musicfinder.analyzer.WaveHandler;
import kr.ac.ajou.dv.musicfinder.lib.DbSaveWorker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Properties;

public class MusicAnalyzer {
    public static final String TEMPORARY_WAVE = "temp.wav";

    public static void main(String[] args) {
        Properties config = new Properties();
        try {
            config.load(new FileInputStream("analyzer.conf"));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        File mp3dir = new File(config.getProperty("mp3_directory"));
        String soxParams = config.getProperty("sox_parameters");

        WaveHandler wh = new WaveHandler();
        DbSaveWorker dbSaveWorker = new DbSaveWorker();

        for (File f : mp3dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !dir.getName().endsWith(name);
            }
        })) {
            if (!f.isDirectory() && f.getName().toLowerCase().endsWith(".mp3")) {
                String songName = f.getName().substring(0, f.getName().length() - 4);
                System.out.println(songName);
                String command = "sox '" + f.getName() + "' " + soxParams + " '" + TEMPORARY_WAVE + "'";
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.directory(mp3dir);
                try {
                    Process p = pb.start();
                    int exitValue = p.waitFor();
                    System.out.println("Converting to a Wave has been done (exit status: " + exitValue + ")");

                    File wav = new File(mp3dir + File.separator + TEMPORARY_WAVE);
                    if (wav.exists()) {
                        dbSaveWorker.addSong(songName);
                        wh.handle(wav, dbSaveWorker);
                    }
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                } catch (InvalidWaveFormatException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }
    }
}
