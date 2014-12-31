package com.foxdbf;

/**
 * Sample Java class for FoxPro DBF files.
 *  version BETA.2 (2014.dec)
 * 
 * Please, donate by visiting our sponsors
 *   http://chessforeva.blogspot.com
 *   
 */

public class foxdbf {

	
	public static void main(String[] args) {

		base db = new base();

		db.create("/I.DBF", "Numb N(10)");
		db.create_idx("/I.IDX","Numb");	// INDEX ON Numb without re-indexing records
		for(int k=0;k<100;k++)		// append 100 indexed records
			{
			db.Field[0].setByLong(k);
			db.insert_from_memory();
			}
		for(int k=99;k>=0;k--)		// find them all by key and replace with (5000-value)
			{
			db.Field[0].setByLong(k);
			db.SEEK();
			if(!db.eof)
				{
				db.Field[0].setByLong(5000-k);
				db.replace_from_memory();
				}
			else
				{
			 	System.out.print("Not found?! Should be.\n");
				// db.Idx.sRecno contains nearest record, so go to this and skip
			 	}
			}

		for(int k=4901;k<=5000;k++)		// find new values to verify index
			{
			db.Field[0].setByLong(k);
			db.SEEK();
			if(!db.eof) System.out.print("Seek " + String.valueOf(k)  + ", record " + String.valueOf(db.recno) + "\n");
			else System.out.print("Not found?! Should be.\n");	
			}
		db.use();
	
	};
}


