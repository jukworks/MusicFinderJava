package kr.ac.ajou.dv.musicfinder.lib;

import redis.clients.jedis.Jedis;

public abstract class DbWorker {
    public static final int FUZZ_FACTOR = 2;

    protected static final String REDIS_SERVER_ADDR = "localhost";
    protected static final int SONG_HINTS_DB = 1;
    protected static final int SONG_NAME_DB = 0;

    protected Jedis redis;

    public DbWorker() {
        redis = new Jedis(REDIS_SERVER_ADDR);
    }

    public abstract void work(int offset, String fuzz);

    public static String fuzz(int[] points) {
        StringBuffer sb = new StringBuffer("H");
        for (int p : points) {
            sb.append(String.format("%04d", p - p % FUZZ_FACTOR));
        }
        return sb.toString();
    }
}
