package com.foxdbf;

/**
 * Simple Java class for FoxPro DBF files.
 *  version BETA.2 (2014.dec)
 * 
 * Please, visit our sponsors
 *   http://chessforeva.blogspot.com
 *   
 */

public class foxdbf {

	
	public static void main(String[] args) {

		base db = new base();

		db.create("/SAMPLE1.DBF", "Npk N(10), Name C(20), Rating F(5,2), Birth D, Notes M, ieee B(8,4), Id I, Salary Y");

		db.Field[0].setByLong(1);
		db.Field[1].setByString("Mickey");
		db.Field[2].setByDouble( 3.15 );
		db.Field[3].setByString("20050328");
		db.Field[4].setByString("Some notes about.");
		db.Field[5].setByDouble( -9.1678 );
		db.Field[6].setByLong( -2999999 );
		db.Field[7].setByDouble( 3000.25 );
		db.insert_from_memory();	// replace all fields in database
		
		db.DOS_FOX = true;
		db.create("/SAMPLE2.DBF", "Npk N(10), Name C(20), Rating F(5,2), Birth D, Notes M");

		db.Field[0].setByLong(1);
		db.Field[1].setByString("Billy");
		db.Field[2].setByDouble( 8.15 );
		db.Field[3].setByString("20150101");
		db.Field[4].setByString("This is for DOS FOXPRO 2.6x");
		db.insert_from_memory();	// replace all fields in database
		
		db = new base();
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
			if(db.SEEK())
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


		db.READ_ONLY = true;			// to be sure for reading only
		db.use("/I.DBF");
		db.set_order_to_idx("/I.IDX");
		db.go_top();					// scan them sorted all and print values and record numbers
		while(!db.eof)					// should be numbers in descending order
			{
			long V = db.Field[ db.FieldI("Numb") ].longValue();
			System.out.print( "Numb = " + String.valueOf(V) + ", record " + String.valueOf(db.recno) + "\n");
			db.skip(1);
			}
		db.use();
		db.READ_ONLY = false;			// set writing mode on


		// Verify in FoxPro by commands
		//  USE C:\I
		//  SET INDEX TO C:\I.IDX
		//  BROW
		
	
	};
}


