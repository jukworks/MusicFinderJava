package kr.ac.ajou.dv.musicfinder.lib;

public class DbSaveWorker extends DbWorker {
    private int songId;

    public DbSaveWorker() {
        super();
        songId = 0;
        redis.select(SONG_NAME_DB);
        redis.flushDB();
        redis.select(SONG_HINTS_DB);
        redis.flushDB();
    }

    public void addSong(String songName) {
        redis.select(SONG_NAME_DB);
        redis.set(String.valueOf(++songId), songName);
    }

    @Override
    public void work(int offset, String fuzz) {
        redis.select(SONG_HINTS_DB);
        redis.rpush(fuzz, songId + "|" + offset);
    }
}
