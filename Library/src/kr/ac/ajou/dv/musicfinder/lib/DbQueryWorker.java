package kr.ac.ajou.dv.musicfinder.lib;

import java.util.*;

public class DbQueryWorker extends DbWorker {
    private Map<Integer, Integer> songPoints;

    public DbQueryWorker() {
        super();
        songPoints = new HashMap<Integer, Integer>();
    }

    public String getSongName(int songNo) {
        redis.select(SONG_NAME_DB);
        return redis.get(String.valueOf(songNo));
    }

    public SortedMap<Integer, List<Integer>> getPoints() {
        TreeMap<Integer, List<Integer>> ranking = new TreeMap<Integer, List<Integer>>();
        for (int songId : songPoints.keySet()) {
            int score = songPoints.get(songId);
            if (ranking.containsKey(score)) ranking.get(score).add(songId);
            else {
                List<Integer> list = new ArrayList<Integer>();
                list.add(songId);
                ranking.put(score, list);
            }
        }
        return ranking.descendingMap();
    }

    @Override
    public void work(int offset, String fuzz) {
        redis.select(SONG_HINTS_DB);
        List<String> songAndOffsets = redis.lrange(fuzz, 0, -1);

        for (String cs : songAndOffsets) {
            String[] info = cs.split("[|]");
            int songId = Integer.parseInt(info[0]);
            int songOffset = Integer.parseInt(info[1]);
//            System.out.println("Song ID: " + String.format("%3d", songId) + ", Offset: " + String.format("%6d", songOffset));
            if (songPoints.containsKey(songId)) {
                songPoints.put(songId, songPoints.get(songId) + 1);
            } else {
                songPoints.put(songId, 1);
            }
        }
    }
}
