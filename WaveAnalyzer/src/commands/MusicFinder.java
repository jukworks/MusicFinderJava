package commands;

import kr.ac.ajou.dv.musicfinder.analyzer.InvalidWaveFormatException;
import kr.ac.ajou.dv.musicfinder.analyzer.WaveHandler;
import kr.ac.ajou.dv.musicfinder.lib.DbQueryWorker;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.SortedMap;

public class MusicFinder {
    public static void main(String[] args) {
        File queryFile = new File("query/query-shining.wav");

        WaveHandler wh = new WaveHandler();
        DbQueryWorker dbQueryWorker = new DbQueryWorker();

        try {
            wh.handle(queryFile, dbQueryWorker);
            SortedMap<Integer, List<Integer>> ranking = dbQueryWorker.getPoints();
            for (int point : ranking.keySet()) {
                System.out.print(point + " points: ");
                for (int songId : ranking.get(point)) {
                    System.out.print(songId + " ");
                }
                System.out.println();
            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InvalidWaveFormatException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}

