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

import java.awt.RenderingHints.Key;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
	private Map<String, Integer> documentFreq;
	private int nDocs;
	private double minTfIdf;
	
	public ContentBasedCalculator(BookmarkReader reader, int trainSize, int nTopics, double minTfIdf) {
		System.out.println(nTopics);
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
        
        this.minTfIdf = minTfIdf;
        
        model = new ParallelTopicModel(nTopics);
        model.setNumThreads(5);
        model.setNumIterations(526);
        
        documentFreq = new HashMap<String, Integer>();
        nDocs = reader.getBookmarks().size();
        for (Bookmark book : reader.getBookmarks()) {
        	StringBuffer tags = new StringBuffer();
        	for (Integer tagId : book.getTags())
        		tags.append(reader.getTagName(tagId) + " ");
        	String[] words = (book.getTitle() + " " + book.getDescription() + tags.toString()).split(" ");
        	Map<String, Boolean> auxHash = new HashMap<String, Boolean>();
        	for (String word : words) {
        		if (!auxHash.containsKey(word)) {
        			documentFreq.put(word, documentFreq.containsKey(word) ? documentFreq.get(word)+1 : 1);
        			auxHash.put(word, true);
        		}
        	}
        }
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
        	for (IDSorter wordId : tree.get(i)) {
        		int tagId = reader.getTagId(alphabet.lookupObject(wordId.getID()).toString());
        		double score;
        		if (tagId>-1) {
        			score = data[i]*wordId.getWeight()*tfidf(book, alphabet.lookupObject(wordId.getID()).toString());///norm;
        			resultMap.put(tagId, resultMap.containsKey(tagId) ? score+resultMap.get(tagId) : score);
        		}		
        	}
        }
        double norm = 0;
        for (Map.Entry<Integer, Double> entry : resultMap.entrySet())
        	norm += entry.getValue();
        for (Map.Entry<Integer, Double> entry : resultMap.entrySet())
        	resultMap.put(entry.getKey(), entry.getValue()/norm);
        
        Map<Integer, Double> sortedResults = new TreeMap<Integer, Double>(new ValueComparator(resultMap));
        sortedResults.putAll(resultMap);
        System.out.println("\nTitle\n" + book.getTitle());
        System.out.println("Description\n" + book.getDescription());
        System.out.println("Predictions");
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
	
	private double tfidf(Bookmark book, String word) {
		Map<String, Double> tf = new HashMap<String, Double>();
		
		StringBuffer tags = new StringBuffer();
    	for (Integer tagId : book.getTags())
    		tags.append(reader.getTagName(tagId) + " ");
    	String text = book.getTitle() + " " + book.getDescription() + tags.toString();
    	
		for (String w : text.split(" ")) {
			tf.put(w, tf.containsKey(w) ? tf.get(w)+1 : 1.0);
		}
		double maxTF = 0.0;
		for (Double value : tf.values())
			if (value > maxTF)
				maxTF = value;
		for (Map.Entry<String,Double> entry : tf.entrySet()) {
			double idf = nDocs / (documentFreq.containsKey(word) ? documentFreq.get(word)+1 : 1);
			tf.put(entry.getKey(), entry.getValue()/maxTF * idf);
		}
		
		return tf.containsKey(word) ? tf.get(word) : minTfIdf;
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
