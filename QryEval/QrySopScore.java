/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {
	
	/*  
	 * these fields are useful when BM25 or Indri model is applied
	 * */
	private double dfCache = 0.0; //document frequency for specific term and field
	private double ctfCache = 0.0; //collection document frequency
	private double docLenCache = 0.0; //document length for specific field
	private double aveDocLenCache = 0.0; //average document length for specific field
	private double corpLenCache = 0.0; //corpus length for specific field
	private double N = 0.0; //total number of documents in corpus
	private String fieldNameString = "body";
	private long numOfDoc; //total number of documents
	private int docidTracker = Qry.INVALID_DOCID; //track docid for all-document retrieval

	/**
	 *  Document-independent values that should be determined just once.
	 *  Some retrieval models have these, some don't.
	 */

	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch (RetrievalModel r) {
		if(r instanceof RetrievalModelUnrankedBoolean) {
			return this.docIteratorHasMatchFirst (r);
		} else if(r instanceof RetrievalModelRankedBoolean) {
			Boolean hasMatchFirst = this.docIteratorHasMatchFirst (r);
			if(hasMatchFirst) {
				Qry qryIop = this.args.get(0);
				InvList.DocPosting docPo = ((QryIop) qryIop).docIteratorGetMatchPosting();
				this.setScoreCache(docPo.tf);
			}
			return hasMatchFirst;
		} else if(r instanceof RetrievalModelBM25) {
			Boolean hasMatchFirst = this.docIteratorHasMatchFirst (r);
			if(hasMatchFirst) {
				Qry qryIop = this.args.get(0);
				InvList.DocPosting docPo = ((QryIop) qryIop).docIteratorGetMatchPosting();
				this.setScoreCache(docPo.tf); //set tf
				int docid = this.docIteratorGetMatch();
				
				try { //set docLen corresponding to docid
					this.setDocLenCache(Idx.getFieldLength(this.getFieldNameString(), docid));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return hasMatchFirst;
		} else if(r instanceof RetrievalModelIndri) {
			Qry qryIop = this.args.get(0);
			Boolean hasMatchFirst = this.docIteratorHasMatchFirst (r);
			if(hasMatchFirst) {
				InvList.DocPosting docPo = ((QryIop) qryIop).docIteratorGetMatchPosting();
				this.setScoreCache(docPo.tf); //set tf
				int docid = this.docIteratorGetMatch();
				try { //set docLen corresponding to docid
					this.setDocLenCache(Idx.getFieldLength(this.getFieldNameString(), docid));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return hasMatchFirst;
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the OR operator.");
		}		
	}

	/**
	 *  Get a score for the document that docIteratorHasMatch matched.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	public double getScore (RetrievalModel r) throws IOException {

		if (r instanceof RetrievalModelUnrankedBoolean) {
			return this.getScoreUnrankedBoolean (r);
		} else if (r instanceof RetrievalModelRankedBoolean) {
			return this.getScoreRankedBoolean (r);
		}  else if (r instanceof RetrievalModelBM25) {
			return this.getScoreBM25 (r);
		} else if (r instanceof RetrievalModelIndri) {
			return this.getScoreIndri (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the SCORE operator.");
		}
	}

	/**
	 *  getScore for the Unranked retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		} else {
			return 1.0;
		}
	}

	/**
	 *  getScore for the RankedBoolean retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		} else {
			return this.getScoreCache();
		}
	}
	
	/**
	 *  getScore for the BM25 retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreBM25 (RetrievalModel r) throws IOException {
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		} else {
			double tf = this.getScoreCache();
			double df = this.getDfCache();
			double docLen = this.getDocLenCache();
			double aveDocLen = this.getAveDocLenCache();
			double N = this.getN();
			double qtf = 1;
			
			double k_1 = ((RetrievalModelBM25)r).getK_1();
			double k_3 = ((RetrievalModelBM25)r).getK_3();
			double b = ((RetrievalModelBM25)r).getB();
			
			double rsfWeight = Math.max(0,  Math.log((N - df + 0.5) / (df + 0.5)));
			double tfWeight = tf / (tf + k_1 * ((1 - b) + b * docLen / aveDocLen));
			double userWeight = (k_3 + 1) * qtf / (k_3 + qtf);
			
			double finalScore = rsfWeight * tfWeight * userWeight;
			
			return finalScore;
		}
	}
	
	/**
	 *  getScore for the Indri retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreIndri(RetrievalModel r) {
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		} else {
			double mu = ((RetrievalModelIndri) r).getMu();
			double lambda = ((RetrievalModelIndri) r).getLambda();
			
			double ctf = this.getCtfCache();
			double tf = this.getScoreCache();
			double docLen = this.getDocLenCache();
			double corpLen = this.getCorpLenCache();
			double p_q_C = ctf / corpLen; 
			
			double finalScore = (1 - lambda) * (tf + mu * p_q_C) / (docLen + mu) + lambda * p_q_C;
			
			return finalScore;
		}
	}

	/**
	 *  getDefaultScore for the Indri retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @param docid the corresponding docid for score calculation
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */	
	public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
		if(r instanceof RetrievalModelIndri) {
			double mu = ((RetrievalModelIndri) r).getMu();
			double lambda = ((RetrievalModelIndri) r).getLambda();
			double ctf = this.getCtfCache();
			String fieldName = this.getFieldNameString();
			double corpLen = this.getCorpLenCache();
			double docLen = 0.0;
			
			try { //set docLen and corpLen
				docLen = Idx.getFieldLength(fieldName, docid);
			} catch (IOException e) {
				e.printStackTrace();
			}
			double p_q_C = ctf / corpLen;
			
			double defaultScore = (1 - lambda) * mu * p_q_C / (docLen + mu) + lambda * p_q_C;
			return defaultScore;
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the default score.");
		}
	}
	
	/**
	 *  Initialize the query operator (and its arguments), including any
	 *  internal iterators.  If the query operator is of type QryIop, it
	 *  is fully evaluated, and the results are stored in an internal
	 *  inverted list that may be accessed via the internal iterator.
	 *  @param r A retrieval model that guides initialization
	 *  @throws IOException Error accessing the Lucene index.
	 */
	public void initialize (RetrievalModel r) throws IOException {

		Qry q = this.args.get (0);
		q.initialize (r);
		
		//initialize global fields
		
		//set ctf and field name
		this.seCtfCache(((QryIop) q).getCtf()); 
		String fieldName = ((QryIop) q).getField();
		this.setFieldNameString(fieldName);
		
		//total length of documents in corpus
		this.setCorpLenCache(Idx.getSumOfFieldLengths(fieldName));
		
		//total number of documents
		this.setN((double)Idx.getNumDocs());
		
		//average document length for given field
		this.setAveDocLenCache(this.getCorpLenCache() /
				 (double) Idx.getDocCount (fieldName));
		
		//document frequency for this term
		this.setDfCache(((QryIop) q).getDf());
		
		//scoreOp weight should be the same as the QryIop weight it operates on
		this.setWeight(q.getWeight());
		
		//total number of documents used for calculating score for all documents
		this.setNumOfDoc(Idx.getNumDocs());
		
		//start from docid 0 if want to retrieve all documents
		this.setDocidTracker(0);
	}
	
/*
 * getters and setters for private fields
 * */
	public double getDfCache() {
		return dfCache;
	}

	public void setDfCache(double dfCache) {
		this.dfCache = dfCache;
	}
	
	public double getCtfCache() {
		return ctfCache;
	}
	
	public void seCtfCache(double ctfCache) {
		this.ctfCache = ctfCache;
	}

	public double getDocLenCache() {
		return docLenCache;
	}

	public void setDocLenCache(double docLenCache) {
		this.docLenCache = docLenCache;
	}

	public double getAveDocLenCache() {
		return aveDocLenCache;
	}

	public void setAveDocLenCache(double aveDocLenCache) {
		this.aveDocLenCache = aveDocLenCache;
	}
	
	public double getCorpLenCache() {
		return corpLenCache;
	}

	public void setCorpLenCache(double corpLenCache) {
		this.corpLenCache = corpLenCache;
	}
	
	public double getN() {
		return N;
	}

	public void setN(double n) {
		N = n;
	}

	public String getFieldNameString() {
		return fieldNameString;
	}

	public void setFieldNameString(String fieldNameString) {
		this.fieldNameString = fieldNameString;
	}

	public long getNumOfDoc() {
		return numOfDoc;
	}

	public void setNumOfDoc(long numOfDoc) {
		this.numOfDoc = numOfDoc;
	}

	public int getDocidTracker() {
		return docidTracker;
	}

	public void setDocidTracker(int docidTracker) {
		this.docidTracker = docidTracker;
	}

}
