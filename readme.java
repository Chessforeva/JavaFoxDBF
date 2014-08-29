/*

README for FoxDbf java source
*
 * Please, donate by visiting our sponsors
 *   http://chessforeva.blogspot.com
 *   
 *   
FOXPRO databases in java. Supports all basic field types: C,N,F,D,L,I,B,M,G,T,Y.

db = new base();
db.Field[x] contain structures of fields
 and data of the current record (in memory, debug values)
 
db.eof, db.bof, db.reccount, db.recno, db.deleted -
 the same as functions in FoxPro

Functions similar to FoxPro commands:

use( <database file name> ) - to open database
use()  - to close database
create( <database file name>, <create string> ) - to create database
go_recno( <new_recno> ) -  set current record
go_top(), go_bottom() - set pointer to first or last record
append_blank() - to add new empty record
replace_from_memory() - the same as "gather memvar"
replace_field_from_memory(<field I>) - gather memvar field
insert_from_memory() - the same as "insert memvar"
delete(), recall() - to delete and recall current record
skip(<+/- number of records>) - skip/scan database records

additions:
readBinaryFromFile(<filename>) - read byte array from file (see Sample4)
writeBinaryToFile(<filename>,<byte array>) - write byte array to file

Sample1: to create a database

db.create("SAMPLE1.DBF", "Npk N(10), Name C(20), Rating F(5,2), Birth D, Notes M, ieee B(8,4), Id I, Salary Y");
db.Field[0].setByLong(1);
db.Field[1].setByString("Mickey");
db.Field[2].setByDouble( 3.15 );
db.Field[3].setByString("20050328");
db.Field[4].setByString("Some notes about.");
db.Field[5].setByDouble( -9.1678 );
db.Field[6].setByLong( -2999999 );
db.Field[7].setByDouble( 3000.25 );
db.insert_from_memory();	// replace all fields in database

int r = db.FieldI("RATING");	//=2
db.Field[r].setByDouble( 6.25 );
db.replace_field_from_memory(r);	// replace particular field
db.use();

Sample2: to scan database

db.use("SAMPLE1.DBF");
while(!db.eof)
	{
	long npk = db.Field[ db.FieldI("Npk") ].longValue();
	String Name = db.Field[ db.FieldI("Name") ].stringValue();
	double Rating = db.Field[ db.FieldI("Rating") ].doubleValue();
	String Notes = db.Field[ db.FieldI("Notes") ].stringValue();
	String Birth = db.Field[ db.FieldI("Birth") ].stringValue();	//"YYYYMMDD"
	double Salary = db.Field[ db.FieldI("Salary") ].doubleValue();
	db.skip(1);
	}
db.use();

Sample3: DOS table

db.DOS_FOX = true;
db.create("SAMPLE3.DBF","MyMemo M, Logic L, Birth D");
db.use();


Sample4: Binary data, or bytes to be precise
  for special or regional characters in C,M fields (data are byte[] elements)

db.BINARY_CHARS = true;		// for G-type General fields binary mode is by default
db.create("SAMPLE4.DBF", "Name C(10), Picture M");
// store spec.chars in name field
byte[] bArray = { 8, 'M','A',13, 0, 1 };
db.Field[ db.FieldI("Name") ].setBinaries( bArray );
// save picture file to memo field (or G-type)
byte[] bPicArray = db.readBinaryFromFile( "profile.jpg" );
db.Field[ db.FieldI("Picture") ].setBinaries( bPicArray );
db.append_blank();
db.replace_from_memory();

// create a new picture file from memo field
byte[] bPicArray2 = db.Field[ db.FieldI("Picture") ].Binaries();
db.writeBinaryToFile( "profile2.jpg", bPicArray2 );
db.use();


WORKING WITH IDX INDEXES (NOT CDX)

This code is good enough when working without indexes,
 or if You use indexes in read-only mode without replacing/inserting new data.
Sorry, this is java. Otherwise index algorithms are too complex, so java may corrupt the .idx file.
Anyway, this code is good to search database by key and scan some records.

Functions similar to FoxPro commands:

set_order_to_idx(< .idx file name>)	- sets index file, similar SET INDEX TO
create_idx(< .idx file name>, <index expression>) - creates empty index file (no FOR, UNIQUE)
SEEK() - does searching from data in memory variables Field[x]

The PREPARE_KEY_FROM_DATA() procedure prepares searchKey according to data and searching expression
(if key is a field value, it converts automatically,
 otherwise write java code there to prepare user defined keys as DTOS(data)+.. or SYS(15,data..) ,
 this is not original FoxPro)

Sample1: create and write indexed database (prepare for lags)

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
	else
		{
	 	System.out.print("Not found?! Should be.\n");
		// db.order.sRecno contains nearest record, so go to this and skip
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

Sample2: use database for scanning by using index

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

// Verify in FoxPro by commands
//  USE C:\I
//  SET INDEX TO C:\I.IDX
//  BROW

 */