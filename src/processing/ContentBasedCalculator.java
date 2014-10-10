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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.*;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.*;

import com.google.common.primitives.Ints;

import file.PredictionFileWriter;
import file.BookmarkReader;
import file.stemming.englishStemmer;
import common.Bookmark;

public class ContentBasedCalculator {

	private final static int REC_LIMIT = 10;
	
	private BookmarkReader reader;
	private List<Bookmark> trainList;
	
	public ContentBasedCalculator(BookmarkReader reader, int trainSize) {
		this.reader = reader;
		this.trainList = this.reader.getBookmarks().subList(0, trainSize);
	}	
	
	public Map<Integer, Double> getRankedTagList(int userID, int resID) {
		Map<Integer, Double> resultMap = new LinkedHashMap<Integer, Double>();

		// TODO: calculate your recommendations here and return the top-10 (=REC_LIMIT) tags with probability value
		// have also a look on the other calculator classes!
		
		// TODO: in order to improve your content-based recommender, you can merge your results with other approaches like the ones from the LanguageModelCalculator or ActCalculator
		
		return resultMap;
	}
	
	//TODO: implement
	/* Pre-procesing: steam and lematization
	 * Features: TF-IDF
	 * Matrix factorization: SVD / LSA
	 * Implement prediction (testing)
	 * */
	
	/* Implements pre-processing task by applying stop-words, lower case transform
	 * and stemming.
	 * */
	public void preProcessTrainSet() {
		englishStemmer stemmer = new englishStemmer();
		for (Bookmark b : trainList) {
			// Apply lucene stop-words
			StringBuffer newDescription = new StringBuffer();
			Tokenizer tokenizer = new StandardTokenizer(new StringReader(b.getTitle() + " " + b.getDescription()));
			StopFilter stopFilter = new StopFilter(tokenizer, StopAnalyzer.ENGLISH_STOP_WORDS_SET);
			CharTermAttribute charTermAttribute = tokenizer.addAttribute(CharTermAttribute.class);
			try {
				stopFilter.reset();
				while (stopFilter.incrementToken()) {
					newDescription.append(charTermAttribute.toString().toString() + " ");
				}
				b.setDescription(newDescription.toString().toLowerCase());
				stopFilter.end();
				stopFilter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Steam bookmark description
			stemmer.setCurrent(b.getDescription());
			stemmer.stem();
			b.setDescription(stemmer.getCurrent());
			
			// Steam bookmark title
			stemmer.setCurrent(b.getTitle());
			stemmer.stem();
			b.setTitle(stemmer.getCurrent());
		}
	}
	
	/* For each bookmark on training set, the method perform a tokenization for the 
	 * text and description (concatenated) and a TF-IDF feature transformation. 
	 */
	public void tfIdfTransformationTrainSet() {
		
	}
	
	public void train() {
		System.out.println("====== TRAIN =========");
		preProcessTrainSet();
		for (Bookmark b : trainList) {
			System.out.println(b.getDescription());
			System.out.println(b.getTitle());
			break;
		}
	}
	
	// ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
	
	public static List<Map<Integer, Double>> startContentBasedCreation(BookmarkReader reader, int sampleSize) {
		int size = reader.getBookmarks().size();
		int trainSize = size - sampleSize;
		
		ContentBasedCalculator calculator = new ContentBasedCalculator(reader, trainSize);
		List<Map<Integer, Double>> results = new ArrayList<Map<Integer, Double>>();
		if (trainSize == size) {
			trainSize = 0;
		}
		
		for (int i = trainSize; i < size; i++) { // the test-set
			Bookmark data = reader.getBookmarks().get(i);
			Map<Integer, Double> map = calculator.getRankedTagList(data.getUserID(), data.getWikiID());
			results.add(map);
		}
		return results;
	}
	
	public static void predictSample(String filename, int trainSize, int sampleSize) {

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
