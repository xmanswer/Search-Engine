import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;

/**
 * a help class for storing all necessary information for a <query, doc> pair,
 * which is useful in the learning to rank model.
 * Important fields include qid and docid, and the feature vector; also has
 * necessary methods for generating the feature vector values as well as write
 * feature vectors to the file for training and classification
 */
public class FeatureVector implements Comparable<FeatureVector>{
	private String qid;
	private String[] queryTokens;
	private int docid;
	private int relScore;
	private double[] fvector;
	private RetrievalModelletor model;
	private HashMap<String, TermVector> termVectors;
	private double svmScore;
	
	public static final int FEATURE_SIZE = 18;
	public static final double INVALID_FEATURE = Double.MIN_VALUE;
	private static final String WIKI_STR = "wikipedia.org";
	
	public FeatureVector(String qid, String[] queryTokens, int docid, 
			int relScore, double pageRankScore, RetrievalModelletor model) 
					throws NumberFormatException, IOException {
		
		//initialize fields from input
		this.qid = qid;
		this.queryTokens = queryTokens;
		this.docid = docid;
		this.setRelScore(relScore);
		this.model = model;
		
		//generate term vectors for different fields
		this.termVectors = new HashMap<String, TermVector>();
		termVectors.put("body", new TermVector(this.docid, "body"));
		termVectors.put("title", new TermVector(this.docid, "title"));
		termVectors.put("url", new TermVector(this.docid, "url"));
		termVectors.put("inlink", new TermVector(this.docid, "inlink"));
		
		//calculate features and assign to feature vector
		this.fvector = new double[FEATURE_SIZE];
		
		//disable features
		Set<Integer> disabled = model.getFeatureDisable();
		
		//assign values to feature vector
		fvector[0] = disabled.contains(0) ? 0 : Integer.parseInt (Idx.getAttribute ("score", this.docid));
		String rawUrl = Idx.getAttribute ("rawUrl", this.docid);
		fvector[1] = disabled.contains(1) ? 0 : getUrlDepth(rawUrl);
		fvector[2] = disabled.contains(2) ? 0 : (rawUrl.contains(WIKI_STR) ? 1 : 0);
		fvector[3] = disabled.contains(3) ? 0 : pageRankScore;
		fvector[4] = disabled.contains(4) ? 0 : getBM25("body");
		fvector[5] = disabled.contains(5) ? 0 : getIndir("body");
		fvector[6] = disabled.contains(6) ? 0 : getTermOverlap("body");
		fvector[7] = disabled.contains(7) ? 0 : getBM25("title");
		fvector[8] = disabled.contains(8) ? 0 : getIndir("title");
		fvector[9] = disabled.contains(9) ? 0 : getTermOverlap("title");
		fvector[10] = disabled.contains(10) ? 0 : getBM25("url");
		fvector[11] = disabled.contains(11) ? 0 : getIndir("url");
		fvector[12] = disabled.contains(12) ? 0 : getTermOverlap("url");
		fvector[13] = disabled.contains(13) ? 0 : getBM25("inlink");
		fvector[14] = disabled.contains(14) ? 0 : getIndir("inlink");
		fvector[15] = disabled.contains(15) ? 0 : getTermOverlap("inlink");
		fvector[16] = disabled.contains(16) ? 0 : getCustom1();
		fvector[17] = disabled.contains(17) ? 0 : getCustom2(fvector[0], fvector[1], fvector[3]);
	}
	
	//used to add current feature values to the featureVectorSum for future normalization
	public void findMaxMinFeatureValue(double[] featureVectorMax, double[] featureVectorMin) {
		for(int i = 0; i < this.fvector.length; i++) {
			if(this.fvector[i] == FeatureVector.INVALID_FEATURE) continue;
			featureVectorMax[i] = Math.max(this.fvector[i], featureVectorMax[i]);
			featureVectorMin[i] = Math.min(this.fvector[i], featureVectorMin[i]);
		}
	}
	
	//normalize each feature based on the sum of all <q, d> values for each feature
	public void normFeatureVector(double[] featureVectorMax, double[] featureVectorMin) {
		for(int i = 0; i < this.fvector.length; i++) {
			if(featureVectorMax[i] == featureVectorMin[i] 
					|| this.fvector[i] == FeatureVector.INVALID_FEATURE) {
				this.fvector[i] = 0;
			}
			else {
				this.fvector[i] = (this.fvector[i] - featureVectorMin[i]) / 
						(featureVectorMax[i] - featureVectorMin[i]);
			}
		}
	}
	
	//write the current feature vector to file
	public void writeFeatureVector(PrintWriter fvWriter) throws IOException {
		fvWriter.print(Integer.toString(this.relScore) + " ");
		fvWriter.print("qid:" + this.qid + " ");
		for(int i = 0; i < this.fvector.length; i++) {
			fvWriter.print(Integer.toString(i + 1) + ":" + Double.toString(this.fvector[i]) + " ");
		}
		fvWriter.print("# " + Idx.getExternalDocid(this.docid) + "\n");
	}
	
	//count the depth for given url (number of /), taking out http:// and the last /
	private double getUrlDepth(String rawUrl) {
		double cnt = 0;
		for(int i = 0; i < rawUrl.length(); i++) {
			if(rawUrl.charAt(i) == '/') cnt = cnt + 1.0;
		}
		return cnt;
	}
	
	//get the accumulated BM25 score for this <q, d> and given field
	private double getBM25(String field) throws IOException {
		double score = 0;
		TermVector termVector = this.termVectors.get(field);
		
		if(termVector.stemsLength() == 0) { //no such field exist for this doc
			return FeatureVector.INVALID_FEATURE;
		}
		
		for(String stem : this.queryTokens) { //go through each query stem
			for(int i = 0; i < termVector.stemsLength(); i++) { //go through terms in doc
				if(stem.equals(termVector.stemString(i))) { //add to BM25 score if match
					score += BM25Score(i, field, termVector);
					break;
				}
			}
		}
		return score;
	}
	
	//get the accumulated Indir score for this <q, d> and given field
	private double getIndir(String field) throws IOException {
		double score = 1;
		TermVector termVector = this.termVectors.get(field);
		
		if(termVector.stemsLength() == 0) { //no such field exist for this doc
			return FeatureVector.INVALID_FEATURE;
		}
		
		double reversePower = 1.0 / ((double) queryTokens.length); //reverse of query stems size
		int matchCnt = 0; //count number of matched terms
		for(String stem : this.queryTokens) { //go through each query stem
			int i = 0;
			//go through each term in doc and check if match the current stem in query
			for(; i < termVector.stemsLength(); i++) {
				if(stem.equals(termVector.stemString(i))) {
					//if match, multiply it to the final indri score
					score *= Math.pow(indriScore(i, field, termVector), reversePower);
					matchCnt++;
					break;

				}
			}
			//if not break before i exceeds doc length, doc does not contain the stem
			//calculate default score instead
			if(i == termVector.stemsLength()) {
				score *= Math.pow(defaultIndriScore(stem, field, termVector), reversePower);
			}
		}
		return (matchCnt == 0) ? 0.0 : score; //if no match found, return 0 instead of default
	}

	//get the percentage of term overlap for this <q, d> and given field
	private double getTermOverlap(String field) {
		double cnt = 0;
		TermVector termVector = this.termVectors.get(field);
		
		if(termVector.stemsLength() == 0) { //no such field exist for this doc
			return FeatureVector.INVALID_FEATURE;
		}
		
		for(String stem : this.queryTokens) { //go through each stem in query
			
			//go through terms in doc
			for(int i = 0; i < termVector.stemsLength(); i++) {
				if(stem.equals(termVector.stemString(i))) { //increment cnt if a match
					cnt++;
					break;
				}
			}
		}
		return cnt / ((double) this.queryTokens.length);
	}
	
	//customized F17 score, calculate the VSM lnc.ltc scores, use body field
	private double getCustom1() throws IOException {
		TermVector termVector = this.termVectors.get("body");
		if(termVector.stemsLength() <= 1) { //no such field exist for this doc
			return FeatureVector.INVALID_FEATURE;
		}
		
		double score = 0;
		double queryNormLen = 0;
		double docNormLen = 0;
		double N = (double)Idx.getNumDocs();
		
		for(int i = 1; i < termVector.stemsLength(); i++) { //accumulate the doc length for norm
			double docTermWeight = Math.log(termVector.stemFreq(i)) + 1;
			docNormLen += docTermWeight * docTermWeight;
		}
		
		for(String stem : this.queryTokens) { //go through each stem in query
			double queryTermWeight = Math.log(N / Idx.INDEXREADER.docFreq(new Term("body", stem)));
			queryNormLen += queryTermWeight * queryTermWeight;
			
			//go through terms in doc
			for(int i = 1; i < termVector.stemsLength(); i++) {
				if(stem.equals(termVector.stemString(i))) { //increment cnt if a match
					score += (Math.log(termVector.stemFreq(i)) + 1) * queryTermWeight;
				}
			}
		}
		return score / Math.sqrt(queryNormLen * docNormLen);
	}
	
	//customized F18 score, calculate the KL divergence with prior, use body field
	//prior contains page rank score and date (the larger the better), 
	//spam and url depth (the lower the better)
	private double getCustom2(double spam, double urlDepth, double prScore) throws IOException {
		double score = 0;
		TermVector termVector = this.termVectors.get("body");
		
		if(termVector.stemsLength() == 0) { //no such field exist for this doc
			return FeatureVector.INVALID_FEATURE;
		}
		
		double date = Double.parseDouble(Idx.getAttribute("date", this.docid));
		
		//make sure prior is greater than 0
		double prior = (prScore + 2) * date / ((spam + 1) * urlDepth);
		prior = Math.log(prior);
		
		for(String stem : this.queryTokens) { //go through each query stem
			int i = 0;
			for(; i < termVector.stemsLength(); i++) { //go through terms in doc
				if(stem.equals(termVector.stemString(i))) { //use indri score for p(q|d)
					score += Math.log(indriScore(i, "body", termVector));
					break;
				}
			}
			
			//if not break before i exceeds doc length, doc does not contain the stem
			//calculate default score instead
			if(i == termVector.stemsLength()) {
				score += Math.log(defaultIndriScore(stem, "body", termVector));
			}
		}
		
		return score + prior;
	}
	
	//calculate BM25 score for given term string (index) and the doc term vector
	private double BM25Score(int stemIndex, String field, TermVector termVector) throws IOException {
		double tf = (double) termVector.stemFreq(stemIndex);
		double df = (double) termVector.stemDf(stemIndex);
		double docLen = (double) termVector.positionsLength();
		double aveDocLen = Idx.getSumOfFieldLengths(field) / (double)Idx.getDocCount (field);
		double N = (double)Idx.getNumDocs();
		double qtf = 1;
		
		double k_1 = this.model.getK_1();
		double k_3 = this.model.getK_3();
		double b = this.model.getB();
		
		double rsfWeight = Math.max(0,  Math.log((N - df + 0.5) / (df + 0.5)));
		double tfWeight = tf / (tf + k_1 * ((1 - b) + b * docLen / aveDocLen));
		double userWeight = (k_3 + 1) * qtf / (k_3 + qtf);
		
		return rsfWeight * tfWeight * userWeight;
	}
	
	//calculate Indri score for given term string (index) and the doc term vector
	private double indriScore(int stemIndex, String field, TermVector termVector) throws IOException {
		double mu = this.model.getMu();
		double lambda = this.model.getLambda();
		
		double ctf = (double) termVector.totalStemFreq(stemIndex);
		double tf = (double) termVector.stemFreq(stemIndex);
		double docLen = (double) termVector.positionsLength();
		double corpLen = (double) Idx.getSumOfFieldLengths(field);
		double p_q_C = ctf / corpLen; 
		
		return (1 - lambda) * (tf + mu * p_q_C) / (docLen + mu) + lambda * p_q_C;
	}
	
	//calculate default Indri score if a stem is not in this doc
	private double defaultIndriScore(String stem, String field, TermVector termVector) throws IOException {
		double mu = this.model.getMu();
		double lambda = this.model.getLambda();
		
		double ctf = (double) Idx.INDEXREADER.totalTermFreq(new Term(field, stem));
		double docLen = (double) termVector.positionsLength();
		double corpLen = (double) Idx.getSumOfFieldLengths(field);
		double p_q_C = ctf / corpLen; 
		
		return (1 - lambda) * (mu * p_q_C) / (docLen + mu) + lambda * p_q_C;	
	}

	
	//compareTo method compare the qid between two FeatureVectors, then docid
	@Override
	public int compareTo(FeatureVector other) {
		int thisqid = Integer.parseInt(this.qid);
		int otherqid = Integer.parseInt(other.getQid());
		if(thisqid == otherqid) {
			return this.docid - other.getDocid();
		} else {
			return thisqid - otherqid;
		}
	}
	
	//getters and setters
	public int getDocid() {
		return docid;
	}

	public void setDocid(int docid) {
		this.docid = docid;
	}

	
	public double[] getFvector() {
		return fvector;
	}

	public void setFvector(double[] fvector) {
		this.fvector = fvector;
	}

	public int getRelScore() {
		return relScore;
	}

	public void setRelScore(int relScore) {
		this.relScore = relScore;
	}
	
	public String getQid() {
		return qid;
	}

	public void setQid(String qid) {
		this.qid = qid;
	}

	public double getSvmScore() {
		return svmScore;
	}

	public void setSvmScore(double svmScore) {
		this.svmScore = svmScore;
	}
}
