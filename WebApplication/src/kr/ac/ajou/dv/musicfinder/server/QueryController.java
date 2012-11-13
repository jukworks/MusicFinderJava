package kr.ac.ajou.dv.musicfinder.server;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import kr.ac.ajou.dv.musicfinder.lib.DbQueryWorker;
import kr.ac.ajou.dv.musicfinder.lib.DbWorker;
import kr.ac.ajou.dv.musicfinder.lib.ResultFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.SortedMap;

@Controller
@RequestMapping("/query.json")
public class QueryController {
    private static final String FAIL_STRING = "No Found.";
    public static final int NUM_HINTS = 4;

    @RequestMapping(method = RequestMethod.POST)
    public ModelAndView query(@RequestParam("hints") String hintsJson) {
        ModelAndView mav = new ModelAndView();
        Gson gson = new Gson();
        boolean failFlag = true;

        mav.setViewName("queryResult");

        List<List<Integer>> ret;
        analysis:
        {
            try {
                ret = gson.fromJson(hintsJson, new TypeToken<List<List<Integer>>>() {
                }.getType());

                DbQueryWorker dbQueryWorker = new DbQueryWorker();
                int offset = 0;
                int[] hint = new int[NUM_HINTS];
                for (List<Integer> sample : ret) {
                    if (sample == null || sample.size() != NUM_HINTS) break analysis;
                    for (int i = 0; i < NUM_HINTS; i++) {
                        hint[i] = sample.get(i);
                    }
                    dbQueryWorker.work(offset++, DbWorker.fuzz(hint));
                }
                SortedMap<Integer, List<Integer>> ranking = dbQueryWorker.getPoints();
                if (ranking == null || ranking.size() < 1) break analysis;

                ResultFormat rf = new ResultFormat();
                rf.count = (ranking.size() > 5) ? 5 : ranking.size();
                int totalPoints = 0;
                for (int score : ranking.keySet()) {
                    totalPoints += score;
                }
                rf.top5names = new String[rf.count];
                rf.top5percentage = new int[rf.count];
                for (int i = 0; i < 5 && !ranking.isEmpty(); ) {
                    List<Integer> songs = ranking.get(ranking.firstKey());
                    for (int songId : songs) {
                        if (i >= 5) break;
                        rf.top5names[i] = dbQueryWorker.getSongName(songId);
                        rf.top5percentage[i++] = (int) (((double) ranking.firstKey() / totalPoints) * 100);
                    }
                    ranking.remove(ranking.firstKey());
                }
                mav.addObject("result", gson.toJson(rf));
                failFlag = false;
            } catch (JsonSyntaxException e) {
                ;
            }
        }
        if (failFlag) mav.addObject("result", FAIL_STRING);
        return mav;
    }
}
