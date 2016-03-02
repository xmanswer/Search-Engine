import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *  The Near operator for all retrieval models.
 */
public class QryIopNear extends QryIop {
	private int nearInt; //define that max window among terms

	/**
	 *  constructors
	 */
	public QryIopNear(int nearInt) {
		this.nearInt = nearInt;
		this.field = "body";
	}
	
	public QryIopNear(int nearInt, String field) {
		this.nearInt = nearInt;
		this.field = field;
	}

	public QryIopNear(int nearInt, String field, InvList invertedList) {
		this.nearInt = nearInt;
		this.field = field;
		this.invertedList = invertedList;
	}

	/**
	 *  setter for nearInt
	 */
	public void setNearInt(int nearInt) {
		this.nearInt = nearInt;
	}

	/**
	 *  getter for nearInt
	 */
	public int getNearInt() {
		return this.nearInt;
	}

	/**
	 *  Evaluate the query operator; the result is an internal inverted
	 *  list that may be accessed via the internal iterators.
	 *  @throws IOException Error accessing the Lucene index.
	 */
	protected void evaluate () throws IOException {
		QryIop q_0 = (QryIop) this.args.get(0);
		
		//greedy algorithm to match every child QryIops 
		for(int i = 1; i < this.args.size(); i++) {
			QryIop q_1 = (QryIop) this.args.get(i);
			q_0 = mergeQryIop(q_0, q_1);
			
			/*
			 * if two Qrys does not match near criteria, 
			 * don't continue, evaluate it as an empty InvList
			 * with doc frequency 0 so that hasMatch method will
			 * return false for this QryIopNear
			 */
			if(q_0 == null) {
				this.invertedList = new InvList();
				this.invertedList.df = 0;
				this.resetIteratorIndex();
				return;
			}
		}
		
		//this QryIop invertedList will copy the merged invertedList
		this.invertedList = q_0.invertedList;
		this.field = q_0.field;
		this.resetIteratorIndex();		
	}

	/**
	 *  merge the inverted list of two QryIop inverted lists
	 *  if in the same document, q_1 term is on the right hand 
	 *  side of q_0 term, and if the distance is no bigger than 
	 *  nearInt
	 */
	private QryIop mergeQryIop(QryIop q_0, QryIop q_1) {
		//no match found due to different field
		if(!q_0.field.equals(q_1.field)) return null; 
		
		RetrievalModel r = null; //to make docIterator happy
		InvList mergedInvList = new InvList(q_0.field);


		while(q_0.docIteratorHasMatch(r)) {

			//advance docid_1 to docid_0, break if no more docs
			int docid_0 = q_0.docIteratorGetMatch();
			
			q_1.docIteratorAdvanceTo(docid_0);
			if(!q_1.docIteratorHasMatch(r)) break;

			int docid_1 = q_1.docIteratorGetMatch();
			if(docid_0 != docid_1) {
				//advance docid_0 to docid_1 if not equal
				q_0.docIteratorAdvanceTo(docid_1);
				continue;
			}
			
			List<Integer> positions = new ArrayList<Integer>();
			List<Integer> pos0 = new ArrayList<Integer>();

			//found the occurrence of both Qrys in the same document
			while(q_0.locIteratorHasMatch()) {
				int loc_0 = q_0.locIteratorGetMatch();
				
				//System.out.println("loc_0: " + Integer.toString(loc_0));

				//make sure q_1 on the right of q_0
				q_1.locIteratorAdvancePast(loc_0); 

				if(q_1.locIteratorHasMatch()) {
					int loc_1 = q_1.locIteratorGetMatch();

					if((loc_1 - loc_0) <= this.nearInt) { //match found within range

						pos0.add(loc_0);
						positions.add(loc_1);
					} 
					q_0.locIteratorAdvance(); //advance q_0 loc pointer
				} else {
					break;
				}
			}
			
			//appendPosting will increase ctf, which can be used to 
			//check if a match is found, also need to skip empty list
			if(positions.size() > 0)
				mergedInvList.appendPosting(docid_1, positions);

			q_0.docIteratorAdvancePast(docid_0); //increment q_0 docid			
		}
		
		if(mergedInvList.ctf == 0) return null; //no match found
		
		QryIopNear result = new QryIopNear(this.nearInt, q_0.field, mergedInvList);
		result.resetIteratorIndex();

		return result;
	}
}
