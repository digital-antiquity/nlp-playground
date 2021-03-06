package org.tdar.nlp.nlp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tdar.nlp.TermWrapper;
import org.tdar.nlp.Utils;
import org.tdar.nlp.document.NlpPage;
import org.tdar.nlp.result.Section;

public class ResultAnalyzer {

    private static final int _20 = 20;
    private static final int _1000 = 1000;
    private static final int _200 = 200;
    private static final int OVERRIDE_RELEVANCY_WITH_OCURRENCE_HIGHER_THAN = 20;
    public static final int SKIP_PHRASES_LONGER_THAN = 5;
    private final Logger log = LogManager.getLogger(getClass());
    private String regexBoost = null;
    private Double minProbability = .5;
    private List<String> boostValues = new ArrayList<>();

    private Map<String, TermWrapper> ocur = new HashMap<>();
    private String type;
    
    public ResultAnalyzer(NLPHelper helper) {
        this.type = helper.getType();
        this.boostValues = helper.getBoostValues();
        this.regexBoost = helper.getRegexBoost();
    }
    
    public void addPage(NlpPage page) {
        Map<String, TermWrapper> map = page.getReferences().get(type);
        if (map == null) {
            return;
        }
        for (String key : map.keySet()) {
            TermWrapper wrap_ = map.get(key);
            TermWrapper wrap = ocur.getOrDefault(key.toLowerCase(),wrap_);

            // if they're not the same object, combine them
            if (System.identityHashCode(wrap) != System.identityHashCode(wrap_)) {
                wrap.combine(wrap_);
            }
            ocur.put(key.toLowerCase(), wrap);
        }
    }

    public void printOccurrence(Section section) {
        int avg = 0;
        Map<String, TermWrapper> multiWord = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // split into cleaned single and multi-word phrase
        for (Entry<String, TermWrapper> entry : ocur.entrySet()) {
            TermWrapper value = entry.getValue();
            log.trace("{} prob:{} ocur:{}", value.getTerm(), value.getProbabilty(), value.getOccur());
            if (value.getProbabilty() < getMinProbability() && value.getOccur() < OVERRIDE_RELEVANCY_WITH_OCURRENCE_HIGHER_THAN) {
                continue;
            }

            String key = entry.getKey();
            key = Utils.cleanString(key);
            avg += value.getOccur();
            TermWrapper termWrapper = multiWord.get(key);
            if (termWrapper != null) {
                termWrapper.combine(value);
                continue;
            }
            int numSpaces = StringUtils.countMatches(key, " ");
            if (numSpaces < SKIP_PHRASES_LONGER_THAN) {
                multiWord.put(key, value);
            } else {
                // log.debug("\t--" + key);
            }
        }
        if (ocur.size() > 0) {
            avg = avg / ocur.size();
        }
        Map<Integer, List<String>> reverse = new HashMap<>();
        int weightedAvg = sortByOccurrence(multiWord, reverse);

        printResults(type, avg, reverse, section);
    }

    private void printResults(String type, int avg, Map<Integer, List<String>> reverse, Section section) {
        // output
        List<Integer> list = new ArrayList<Integer>(reverse.keySet());
        Collections.sort(list);
        Collections.reverse(list);
        Map<String,Integer> results = section.getResults().getOrDefault(type, new HashMap<String,Integer>());
        for (Integer key : list) {
            for (String val : reverse.get(key)) {
                String header = type;
                if (StringUtils.isNotBlank(type)) {
                    header += " ";
                }
                if ((key > avg || list.size() < _20) && key > 0) {
                        log.debug(header + key + " | " + val);
                        results.put(val, key);
                }
            }
        }
        section.getResults().put(type, results);
    }

    /**
     * Flip a hash by the # of ocurrences as opposed to the term
     * 
     * @param multiWord
     * @return
     */
    private int sortByOccurrence(Map<String, TermWrapper> multiWord, Map<Integer, List<String>> reverse) {

        // reverse the hash by count
        int total = 0;
        for (Entry<String, TermWrapper> entry : multiWord.entrySet()) {
            TermWrapper value = entry.getValue();
            String key = entry.getKey();
            Integer weightedOccurrence = value.getWeightedOccurrence();
            for (String boost : boostValues) {
                if (StringUtils.containsIgnoreCase(key, boost)) {
                    weightedOccurrence += _200;
                }
                if (StringUtils.equalsIgnoreCase(key, boost)) {
                    weightedOccurrence -=_1000;
                }
            }
            
            // for people and institutions, weight multi-word terms as higher
            if ((StringUtils.equalsIgnoreCase(type, "person") || StringUtils.equalsIgnoreCase(type, "institution"))&& StringUtils.countMatches(key, " ") < 1) {
                weightedOccurrence -= _200;
            }
            
            if (regexBoost != null && key.matches(regexBoost)) {
                weightedOccurrence += _200;
            }
            List<String> results = reverse.getOrDefault(weightedOccurrence, new ArrayList<String>());
            total += weightedOccurrence;
            results.add(key);
            reverse.put(weightedOccurrence, results);
        }
        if (multiWord.size() > 0) {
            return Utils.toPercent(total, multiWord.size());
        }
        return 0;
    }

    public Double getMinProbability() {
        return minProbability;
    }

    public void setMinProbability(Double minProbability) {
        this.minProbability = minProbability;
    }

}
