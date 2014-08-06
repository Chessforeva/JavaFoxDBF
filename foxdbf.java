package com.foxdbf;

/**
 * Sample Java class for FoxPro DBF files.
 * 
 * Please, donate by visiting our sponsors
 *   http://chessforeva.blogspot.com
 *   
 */

public class foxdbf {

	
	public static void main(String[] args) {

		base db = new base();
		
		db.create("/I.DBF", "Numb N(10)");
		db.create_idx("/I.IDX","Numb");	// INDEX ON Numb
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
			else System.out.print("Not found?! Should be.\n");	
			}

		for(int k=4901;k<=5000;k++)		// find new values to verify index
			{
			db.Field[0].setByLong(k);
			db.SEEK();
			if(!db.eof)
				System.out.print("Seek " + String.valueOf(k)  + ", record " + String.valueOf(db.recno) + "\n");
			else System.out.print("Not found?! Should be.\n");	
			}
				
		db.go_top();					// scan them sorted all and print values and record numbers
		while(!db.eof)
			{
			long V = db.Field[ db.FieldI("Numb") ].longValue();
			System.out.print( "Numb = " + String.valueOf(V) + ", record " + String.valueOf(db.recno) + "\n");
			db.skip(1);
			}
		db.use();
		
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
		
		System.out.print("FoxDbf sample :)\n");

	};
}


