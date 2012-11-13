package kr.ac.ajou.dv.musicfinder.android;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.google.gson.Gson;
import kr.ac.ajou.dv.musicfinder.lib.FftHelper;
import kr.ac.ajou.dv.musicfinder.lib.ResultFormat;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class MusicFinderActivity extends Activity {
    private static final String TAG = "MusicFinderClient";
    private static final String MUSIC_DB_SERVER = "http://dv.ajou.ac.kr:8080/music-finder/query.json";

    private TextView progress;
    private AudioRecord audioRecord;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        progress = (TextView) findViewById(R.id.progressText);
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.stop();
            audioRecord.release();
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public void startRecording(View view) {
        new RecordingTask().execute();
    }

    private class RecordingTask extends AsyncTask<Void, Integer, List<List<Integer>>> {
        private static final int ERROR_MIN_BUFFER_SIZE = -2001;
        private static final int ERROR_RECORDING_CANCEL = -2002;

        private static final int sampleRate = 32000;
        private static final int bufferSize = 4096;
        private static final int sampleSize = 4096 / (Short.SIZE / Byte.SIZE);
        private static final long recordingTime = 30 * 1000;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            findViewById(R.id.record_button).setEnabled(false);
        }

        @Override
        protected List<List<Integer>> doInBackground(Void... voids) {
            int minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            Log.i(TAG, "The minimum buffer size for recording: " + minBufferSize);
            if (bufferSize < minBufferSize) {
                Log.e(TAG, "Required minimum buffer size is greater than 4096 bytes!!");
                publishProgress(ERROR_MIN_BUFFER_SIZE);
                return null;
            }
            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);
                if (audioRecord == null) {
                    Log.e(TAG, "Failed to initialize an AudioRecord instance!!");
                    return null;
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal arguments for initializing an AudioRecord instance!!");
                return null;
            }

            if (audioRecord.getState() != AudioRecord.STATE_UNINITIALIZED &&
                    audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED)
                audioRecord.stop();

            short[] audio = new short[sampleSize];
            List<List<Integer>> intensivePoints = new ArrayList<List<Integer>>();
            long startTime = System.currentTimeMillis();
            audioRecord.startRecording();

            int count = 0;
            while (System.currentTimeMillis() - startTime < recordingTime) {
                int read = audioRecord.read(audio, 0, sampleSize);
                if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                    publishProgress(read);
                    break;
                }
                if (isCancelled()) {
                    publishProgress(ERROR_RECORDING_CANCEL);
                    break;
                }
                publishProgress(++count);

                int[] points = FftHelper.getIntensivePointsAfterFft(audio);
                List<Integer> heapPoints = new ArrayList<Integer>(4);
                heapPoints.add(points[0]);
                heapPoints.add(points[1]);
                heapPoints.add(points[2]);
                heapPoints.add(points[3]);
                intensivePoints.add(heapPoints);
            }
            audioRecord.stop();
            audioRecord.release(); // You must release audioRecord, esp. in Samsung Galaxy series.

            return (intensivePoints.isEmpty()) ? null : intensivePoints;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            int code = values[0];
            String msg;
            switch (code) {
                case AudioRecord.ERROR_INVALID_OPERATION:
                    msg = "The AudioRecord instance wasn't properly initialized.";
                    break;
                case AudioRecord.ERROR_BAD_VALUE:
                    msg = "The parameters for the audio recording don't resolve to valid data and indexes.";
                    break;
                case ERROR_RECORDING_CANCEL:
                    msg = "The recording has been cancelled.";
                    break;
                case ERROR_MIN_BUFFER_SIZE:
                    msg = "The required minimum buffer size is greater than 4096 bytes.";
                    break;
                default:
                    msg = String.format("%03d audio chunks", code);
            }
            progress.setText(msg);
        }

        @Override
        protected void onPostExecute(List<List<Integer>> points) {
            super.onPostExecute(points);
            progress.append(" read.\nRecording is done.");
            Log.i(TAG, "# of extracted hints: " + points.size());
            if (points != null && points.size() > 0) new QueryingTask().execute(points);
        }
    }

    private class QueryingTask extends AsyncTask<List<List<Integer>>, Integer, ResultFormat> {
        private static final int CODE_SEND_HINTS = 1;

        @Override
        protected ResultFormat doInBackground(List<List<Integer>>... lists) {
            List<List<Integer>> hints = lists[0];
            Gson gson = new Gson();
            String hintsJson = gson.toJson(hints);

            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(MUSIC_DB_SERVER);

            ResultFormat result = null;
            try {
                List<NameValuePair> params = new ArrayList<NameValuePair>();
                params.add(new BasicNameValuePair("hints", hintsJson));
                httpPost.setEntity(new UrlEncodedFormEntity(params));
                HttpResponse response = httpClient.execute(httpPost);
                publishProgress(CODE_SEND_HINTS);
                String returnStr = EntityUtils.toString(response.getEntity());
                result = gson.fromJson(returnStr, ResultFormat.class);
                Log.i(TAG, "The result: " + returnStr);
            } catch (UnsupportedEncodingException e) {
            } catch (ClientProtocolException e) {
            } catch (IOException e) {
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (values == null) return;
            switch (values[0]) {
                case CODE_SEND_HINTS:
                    progress.append("\n" + "The extracted hints from the recording has been sent. Waiting for the result...");
                    break;
            }
        }

        @Override
        protected void onPostExecute(ResultFormat result) {
            super.onPostExecute(result);
            if (result != null) {
                for (int i = 0; i < result.count; i++) {
                    progress.append("\n" + result.top5names[i] + ": " + result.top5percentage[i] + "%");
                }
            }
            findViewById(R.id.record_button).setEnabled(true);
        }
    }
}