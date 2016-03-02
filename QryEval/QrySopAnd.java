import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */

public class QrySopAnd extends QrySop {
	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch (RetrievalModel r) {
		if(r instanceof RetrievalModelUnrankedBoolean) {
			return this.docIteratorHasMatchAll (r);
		} else if(r instanceof RetrievalModelRankedBoolean ||
				r instanceof RetrievalModelBM25) {
			return this.docIteratorHasMatchAllScore (r);
		} else if(r instanceof RetrievalModelIndri) {
			return this.docIteratorHasMatchMinScoreIndri (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the OR operator.");
		}
	}
	
	/**
	 *  An instantiation of docIteratorHasMatch that is true if the
	 *  query has a document that matches all query arguments; 
	 *  the score is the lowest score among all docs; Useful for ranked 
	 *  model; some subclasses may choose to use this implementation.   
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	protected boolean docIteratorHasMatchAllScore (RetrievalModel r) {

		boolean matchFound = false;

		// Keep trying until a match is found or no match is possible.

		while (! matchFound) {

			// Get the docid of the first query argument.

			Qry q_0 = this.args.get (0);
			double minScore = Double.MAX_VALUE;

			if (! q_0.docIteratorHasMatch (r)) {
				return false;
			}

			int docid_0 = q_0.docIteratorGetMatch ();
			try {
				minScore = ((QrySop) q_0).getScore(r);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Other query arguments must match the docid of the first query
			// argument.

			matchFound = true;

			for (int i=1; i<this.args.size(); i++) {
				Qry q_i = this.args.get(i);


				q_i.docIteratorAdvanceTo (docid_0);

				if (! q_i.docIteratorHasMatch (r)) {	// If any argument is exhausted
					return false;				// there are no more matches.
				}

				int docid_i = q_i.docIteratorGetMatch ();

				if (docid_0 != docid_i) {	// docid_0 can't match.  Try again.
					q_0.docIteratorAdvanceTo (docid_i);
					matchFound = false;
					break;
				}
				
				double score_i = Double.MAX_VALUE;
				try {
					score_i = ((QrySop) q_i).getScore(r);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if(score_i < minScore) minScore = score_i;
			}

			if (matchFound) {
				docIteratorSetMatchCache (docid_0);
				this.setScoreCache(minScore);
			}
		}
		
		return true;
	}

	/**
	 *  An instantiation of docIteratorHasMatch that is true if the
	 *  query has a document that matches at least one query argument;
	 *  the match is the smallest docid to match; Score will be calculated
	 *  and cached based on the product of every items in the child scoreList,
	 *  where even if there is no match, a default score is called to return
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	protected boolean docIteratorHasMatchMinScoreIndri (RetrievalModel r) {

		int minDocid = Qry.INVALID_DOCID;
		int numOfQry = this.args.size();
		double reversePower = 1.0 / ((double) numOfQry);
		double scoreCombine = 1;

		if(this.docIteratorHasMatchMin(r)) {
			minDocid = this.docIteratorGetMatch();
		} else {
			return false;
		}
		
		for (int i=0; i<this.args.size(); i++) {
			Qry q_i = this.args.get(i);

			if (q_i.docIteratorHasMatch (r)) {
				int q_iDocid = q_i.docIteratorGetMatch ();
				double q_iScore = 1.0;
				
				//if not the same doc, use default score
				if(q_iDocid != minDocid) {
					try {
						q_iScore = ((QrySop) q_i).getDefaultScore(r, minDocid);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					//get the score for this term at this doc
					
					try {
						q_iScore = ((QrySop) q_i).getScore(r);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				scoreCombine *= Math.pow(q_iScore, reversePower);
			} else {
				double q_iScore = 1.0;
				try {
					q_iScore = ((QrySop) q_i).getDefaultScore(r, minDocid);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				scoreCombine *= Math.pow(q_iScore, reversePower);
			}
		}
		
		this.setScoreCache(scoreCombine);
		return true;
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
				r instanceof RetrievalModelBM25) {
			return this.getScoreRankedBoolean (r);
		} else if(r instanceof RetrievalModelIndri) {
			return this.getScoreIndri (r);
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
	
	/**
	 *  getScore for the Indri retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreIndri (RetrievalModel r) throws IOException {
		if (! this.docIteratorHasMatchCache()) {
			return 0.0;
		} else {
			return this.getScoreCache();
		}
	}
	
	public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
		if(r instanceof RetrievalModelIndri) {
			double defaultScore = 1.0;
			double reversePower = 1.0 / ((double) this.args.size());
			for (int i=0; i<this.args.size(); i++) {
				Qry q_i = this.args.get(i);
				defaultScore *= Math.pow(((QrySop)q_i).getDefaultScore(r, docid), reversePower);
			}
			return defaultScore;
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the default score.");
		}
	}
}
