/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch (RetrievalModel r) {
		if(r instanceof RetrievalModelUnrankedBoolean) {
			return this.docIteratorHasMatchMin (r);
		} else if(r instanceof RetrievalModelRankedBoolean ||
				r instanceof RetrievalModelBM25 || 
				r instanceof RetrievalModelIndri) {
			return this.docIteratorHasMatchMinScore (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the OR operator.");
		}
	}
	
	/**
	 *  An instantiation of docIteratorHasMatch that is true if the
	 *  query has a document that matches at least one query argument;
	 *  the match is the smallest docid to match, for the same docid, 
	 *  the score is the highest among all matched doc; some subclasses  
	 *  may choose to use this implementation. Useful for ranked model
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 * @throws IOException 
	 */
	protected boolean docIteratorHasMatchMinScore(RetrievalModel r) {
		int minDocid = Qry.INVALID_DOCID;
		double maxScore = 0.0;

		for (int i=0; i<this.args.size(); i++) {
			Qry q_i = this.args.get(i);

			if (q_i.docIteratorHasMatch (r)) {
				int q_iDocid = q_i.docIteratorGetMatch ();
				double q_iScore = maxScore;
				try {
					q_iScore = ((QrySop) q_i).getScore(r);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				if ((minDocid > q_iDocid) ||
						(minDocid == Qry.INVALID_DOCID) ||
						(minDocid == q_iDocid && q_iScore > maxScore)) {
					minDocid = q_iDocid;
					maxScore = q_iScore;
				}
			}
		}

		if (minDocid != Qry.INVALID_DOCID) {
			this.docIteratorSetMatchCache (minDocid);
			this.setScoreCache(maxScore);
			return true;
		} else {
			return false;
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
		} else if (r instanceof RetrievalModelRankedBoolean ||
				r instanceof RetrievalModelBM25 || 
				r instanceof RetrievalModelIndri) {
			return this.getScoreRankedBoolean (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the OR operator.");
		}
	}

	/**
	 *  getScore for the UnrankedBoolean retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
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

	public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
		throw new IllegalArgumentException
		(r.getClass().getName() + " doesn't support the default score");
	}

}
