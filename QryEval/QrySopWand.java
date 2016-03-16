import java.io.IOException;


public class QrySopWand extends QrySop {
	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch (RetrievalModel r) {
		if(r instanceof RetrievalModelIndri) {
			return this.docIteratorHasMatchMinWeightedScoreIndri(r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the OR operator.");
		}
	}
	
	/**
	 *  An instantiation of docIteratorHasMatch that is true if the
	 *  query has a document that matches at least one query argument;
	 *  the match is the smallest docid to match; Score will be calculated
	 *  and cached based on the weighted product of every items in the child scoreList,
	 *  where even if there is no match, a default score is called to return
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	protected boolean docIteratorHasMatchMinWeightedScoreIndri (RetrievalModel r) {

		int minDocid = Qry.INVALID_DOCID;
		double totalWeight = 0;
		double scoreCombine = 1;

		if(this.docIteratorHasMatchMin(r)) {
			minDocid = this.docIteratorGetMatch();
		} else {
			return false;
		}
		
		for (int i=0; i<this.args.size(); i++) {
			Qry q_i = this.args.get(i);

			if (q_i.docIteratorHasMatch(r)) {
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
				totalWeight += q_i.getWeight();
				scoreCombine *= Math.pow(q_iScore, q_i.getWeight());
			} else {
				double q_iScore = 1.0;
				try {
					q_iScore = ((QrySop) q_i).getDefaultScore(r, minDocid);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				totalWeight += q_i.getWeight();
				scoreCombine *= Math.pow(q_iScore, q_i.getWeight());
			}
		}
		scoreCombine = Math.pow(scoreCombine, 1.0 / totalWeight);
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

		if(r instanceof RetrievalModelIndri) {
			if (! this.docIteratorHasMatchCache()) {
				return 0.0;
			} else {
				return this.getScoreCache();
			}
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the OR operator.");
		}
	}
	
	/**
	 *  getDefaultScore for the Indri retrieval model. This takes the weight
	 *  of each child operation into account
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @param docid the corresponding docid for score calculation
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */	
	public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
		if(r instanceof RetrievalModelIndri) {
			double defaultScore = 1.0;
			double totalWeight = 0;
			for (int i=0; i<this.args.size(); i++) {
				Qry q_i = this.args.get(i);
				defaultScore *= Math.pow(((QrySop)q_i).getDefaultScore(r, docid), q_i.getWeight());
				totalWeight += q_i.getWeight();
			}
			defaultScore = Math.pow(defaultScore, 1.0 / totalWeight);
			return defaultScore;
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the default score.");
		}
	}
	
	/**
	 *  Get a string version of #wand operator.  
	 *  @return The string version of this query operator.
	 */
	@Override public String toString(){

		String result = new String ();

		for (int i=0; i<this.args.size(); i++)
			result += this.args.get(i).getWeight() + " " + this.args.get(i) + " ";

		return (this.getDisplayName() + "( " + result + ")");
	}
}
