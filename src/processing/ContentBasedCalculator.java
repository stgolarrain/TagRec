/*
 TagRecommender:
 A framework to implement and evaluate algorithms for the recommendation
 of tags.
 Copyright (C) 2013 Dominik Kowald
 
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.
 
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.
 
 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package processing;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import com.google.common.primitives.Ints;

import file.PredictionFileWriter;
import file.BookmarkReader;
import common.Bookmark;
import common.Utilities;

public class ContentBasedCalculator {

	private final static int REC_LIMIT = 10;
	
	private BookmarkReader reader;
	private List<Bookmark> trainList;
	private ParallelTopicModel model;
	private SerialPipes serialPipes;
	private Alphabet alphabet;
	private List<Map<Integer,Integer>> userMap;
	private List<Map<Integer,Integer>> resMap;
	
	public ContentBasedCalculator(BookmarkReader reader, int trainSize) {
		this.reader = reader;
		this.trainList = this.reader.getBookmarks().subList(0, trainSize);
		userMap = Utilities.getUserMaps(trainList);
		resMap = Utilities.getResMaps(trainList);
		
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
		pipeList.add( new CharSequenceLowercase() );
        pipeList.add( new CharSequence2TokenSequence());
        pipeList.add( new TokenSequenceRemoveStopwords(new File("data/stoplist/en.txt"), "UTF-8", false, false, false) );
        pipeList.add( new TokenSequenceRemoveStopwords(new File("data/stoplist/ge.txt"), "UTF-8", false, false, false) );
        pipeList.add( new TokenSequence2FeatureSequence());
        serialPipes = new SerialPipes(pipeList);
        
        model = new ParallelTopicModel(526);
        model.setNumThreads(5);
        model.setNumIterations(1000);
	}
	
	// TODO: calculate your recommendations here and return the top-10 (=REC_LIMIT) tags with probability value
	// have also a look on the other calculator classes!
	// TODO: in order to improve your content-based recommender, you can merge your results with other approaches like the ones from the LanguageModelCalculator or ActCalculator	
	public Map<Integer, Double> getRankedTagList(int userID, int resID, Bookmark book) {
		Map<Integer, Double> resultMap = new LinkedHashMap<Integer, Double>();

		ArrayList<TreeSet<IDSorter>> tree = model.getSortedWords();
        Instance instance = new Instance(book.getTitle() + " " + book.getDescription(), null, book.getTitle(), book);
        Instance input = serialPipes.instanceFrom(instance);
        double[] data = model.getInferencer().getSampledDistribution(input, 10, 1, 5);
        for (int i=0; i<data.length; i++) {
        	double norm = 0;
        	for (IDSorter wordId : tree.get(i)) {
        		int tagId = reader.getTagId(alphabet.lookupObject(wordId.getID()).toString());
        		if (tagId > -1) {
        			if (filterByUserAndResource(userID, resID, tagId))
            			norm += wordId.getWeight()*100;
        			else 
        				norm += wordId.getWeight();
        		}
        	}
        	for (IDSorter wordId : tree.get(i)) {
        		int tagId = reader.getTagId(alphabet.lookupObject(wordId.getID()).toString());
        		double score;
        		if (tagId>-1) {
        			if (filterByUserAndResource(userID, resID, tagId)) {
        				score = data[i]*wordId.getWeight()*100/norm;
        			} else
        				score = data[i]*wordId.getWeight()/norm;
        			resultMap.put(tagId, resultMap.containsKey(tagId) ? score+resultMap.get(tagId) : score);
        		}		
        	}
        }
        
        Map<Integer, Double> sortedResults = new TreeMap<Integer, Double>(new ValueComparator(resultMap));
        sortedResults.putAll(resultMap);
        System.out.println("\n Predictions");
        int i=0;
        for (Map.Entry<Integer,Double> entry : sortedResults.entrySet()) {
        	System.out.print(" [(" + entry.getKey() + ") " + alphabet.lookupObject(entry.getKey()).toString() + " : " + entry.getValue() + "] ");
        	i++;
        	if (i == REC_LIMIT)
        		break;
        }
        System.out.print("\n Tags\n");
        for (Integer tagId : book.getTags()) {
        	System.out.print(" [(" + tagId + ") " + reader.getTags().get(tagId) + "] ");
        }
        System.out.print("\n");
        	
		return sortedResults;
	}
	
	private boolean filterByUserAndResource(int userId, int resId, int tagId) {
		if (resMap.size()<=resId || !resMap.get(resId).containsKey(tagId) || resMap.get(resId).get(tagId) == 0)
			return false;
		/*if (!userMap.get(userId).containsKey(tagId) || userMap.get(userId).get(tagId) == 0)
			return false;*/
		return true;
	}

	public void train() {
        ArrayList<Instance> instances = new ArrayList<Instance>();
        for (Bookmark book : trainList) {
        	StringBuffer tags = new StringBuffer();
        	for (Integer tagId : book.getTags())
        		tags.append(reader.getTagName(tagId) + " ");
        	instances.add(new Instance(book.getTitle() + " " + book.getDescription() + tags.toString(), null, book.getTitle(), book));
        }
        InstanceList instanceList = new InstanceList(serialPipes);
        instanceList.addThruPipe(instances.iterator());
        model.addInstances(instanceList);
        try {
        	model.estimate();
        } catch (Exception e) {
        	e.printStackTrace();
        }
        alphabet = instanceList.getAlphabet();
	}
	
	// ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
	
	public List<Map<Integer, Double>> startContentBasedCreation(BookmarkReader reader, int sampleSize) {
		int size = reader.getBookmarks().size();
		int trainSize = size - sampleSize;
		
		List<Map<Integer, Double>> results = new ArrayList<Map<Integer, Double>>();
		if (trainSize == size) {
			trainSize = 0;
		}
		
		for (int i = trainSize; i < size; i++) { // the test-set
			Bookmark data = reader.getBookmarks().get(i);
			Map<Integer, Double> map = getRankedTagList(data.getUserID(), data.getWikiID(), data);
			results.add(map);
		}
		return results;
	}
	
	public void predictSample(String filename, int trainSize, int sampleSize) {

		BookmarkReader reader = new BookmarkReader(trainSize, false);
		reader.readFile(filename);

		List<Map<Integer, Double>> modelValues = startContentBasedCreation(reader, sampleSize);
		
		List<int[]> predictionValues = new ArrayList<int[]>();
		for (int i = 0; i < modelValues.size(); i++) {
			Map<Integer, Double> modelVal = modelValues.get(i);
			predictionValues.add(Ints.toArray(modelVal.keySet()));
		}
		String suffix = "_cb";
		reader.setUserLines(reader.getBookmarks().subList(trainSize, reader.getBookmarks().size()));
		PredictionFileWriter writer = new PredictionFileWriter(reader, predictionValues);
		String outputFile = filename + suffix;
		writer.writeFile(outputFile);
	}
}

class ValueComparator implements Comparator<Integer> {
	 
    Map<Integer, Double> map;
 
    public ValueComparator(Map<Integer, Double> base) {
        this.map = base;
    }
 
    public int compare(Integer a, Integer b) {
        if (map.get(a) >= map.get(b)) {
            return -1;
        } else {
            return 1;
        } // returning 0 would merge keys 
    }
}
