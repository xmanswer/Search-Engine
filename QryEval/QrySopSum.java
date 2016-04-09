import java.io.*;

/**
 *  The SUM operator for all retrieval models.
 */

public class QrySopSum extends QrySop {
	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch(RetrievalModel r) {
		if(r instanceof RetrievalModelBM25) {
			return this.docIteratorHasMatchMinSumScore (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the OR operator.");
		}
	}
	
	public boolean docIteratorHasMatchMinSumScore(RetrievalModel r) {
		int minDocid = Qry.INVALID_DOCID;
		double scoreSum = 0.0; //sum of scores for all child score lists

		//get the min docid if found at least a match
		if(this.docIteratorHasMatchMin(r)) {
			minDocid = this.docIteratorGetMatch();
		} else {
			return false;
		}

		for (int i=0; i<this.args.size(); i++) {
			Qry q_i = this.args.get(i);

			if (q_i.docIteratorHasMatch (r)) {
				int q_iDocid = q_i.docIteratorGetMatch ();
				
				//if not the same doc, skip this one
				if(q_iDocid != minDocid) continue; 
				
				//get the score (term frequency) for this term at this doc
				double q_iScore = 0.0;
				try {
					q_iScore = ((QrySop) q_i).getScore(r);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				scoreSum += q_iScore;
			}
		}
		
		this.setScoreCache(scoreSum);
		return true;
	}
	
	
	/**
	 *  Get a score for the document that docIteratorHasMatch matched.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	public double getScore (RetrievalModel r) throws IOException {

		if (r instanceof RetrievalModelBM25) {
			return this.getScoreBM25 (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the OR operator.");
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
			return this.getScoreCache();
		}
	}

	public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
		throw new IllegalArgumentException
		(r.getClass().getName() + " doesn't support the default score");
	}

}
