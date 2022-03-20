/*

README for FoxDbf java source
*
 * Please, visit our sponsors
 *   http://chessforeva.blogspot.com
 *   
 *   
FOXPRO databases in java. Supports all basic field types: C,N,F,D,L,I,B,M,G,T,Y.
Partly supports IDX,CDX index, bugs inside. Writing .idx on Your risk, really corrupts index files.
Set READ_ONLY to be safe not to lose data/indexes when only data reading needed.

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

db.READ_ONLY = true;		// do not modify
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
db.READ_ONLY = false;		// writing enabled

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


WORKING WITH INDEXES

This code is good enough when working without indexes,
 or if You use indexes without replacing/inserting new data.
Really really, do not take it as a database engine.
This is java, indexing algorithms are too complex and poorly described,
so this simplified java may corrupt the index files.

Anyway, this code is good to search in ordered database by key and scan records.
If writing in indexes required then
 consider using original FoxPro process in background
 or try using harbour project http://harbour-project.org/

Functions similar to FoxPro commands:

IDX file:
set_order_to_idx(< .idx file name>)	- sets index file, similar SET INDEX TO
create_idx(< .idx file name>, <index expression>) - creates empty index file (no FOR, UNIQUE)
Only one .idx file can be opened at a time, no additives.
Order is ascending always, then scan backwards from the bottom. 

CDX file (read only):
set_cdx_order( <tag name> ) - sets index of opened .cdx file
READ_ONLY is set automatically if .cdx file exists.
There is no java code for modifying cdx-indexed database.

SEEK() - does searching according to data in memory variables Field[x]

The PREPARE_KEY_FROM_DATA() procedure prepares searchKey according to data and searching expression
(if key is a field value, it converts automatically,
 otherwise write java code there to prepare user defined keys as DTOS(data)+.. or SYS(15,data..) ,
 this is not original FoxPro :)

Index sample1: create and write indexed database (prepare for lags)

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

Index sample2: use database for scanning by using .idx index

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

Index sample3: use database for scanning by using .cdx index
 Prints all "Bob" records

db.use("/MyBASE.DBF");	// this opens also MyBASE.CDX and sets read-only mode
db.set_cdx_order("name");

db.Field[db.FieldI("Name")].setByString("Bob");
db.SEEK();
while(!db.eof)					// should be numbers in descending order
	{
	String fName = db.Field[ db.FieldI("FullName") ].stringValue();
	if(!fName.startsWith("Bob")) break;
	System.out.print( "Full name is " + fName + "\n");
	db.skip(1);
	}
db.use();

 */

/*
REMARKS 03.2022, noobies faq.

A few notes about real FoxPro and history.
FoxPro was a good fast and very cheap database solution for a PC, or small network. PCs were insufficient, awful quality.
Properly designed expensive databases required SQL - with power reliable server responding to multi-users queries.
So, FoxPro was used as a temporary prototype database that grew up to a large system due to "no time to rewrite it all".
As DBFs,FPT,CDX,IDX files were buffered (wr./read) on each very slow! PC these days by kinda locking-ethernet-hack,
there always were power-supply cuts, bad disk drives, lost networks, that corrupted almost everything.
Errors like "Not a database file", "Index file does not match database", and similar, like time-bombs blasted and 
required the database admin to run and pack, and reindex, use smart hack tools tos restore the database system.
Especially with dozens of users waiting. Never again.

Nevertheless the DBF indexing (CDX complex) worked almost instantly, very fast.
DBF could reach 2GB size limit. And FPT another 2GB. That was huge amount of data then.
It is like a fast JSON request in a read only large table.
Very powerful thing. Binary search and not a full-scan. Opens the file and reads data the right way.
But no writing, this requires a proper database engine. Or write a new one transaction safe.

The other well working solution, especially serving web pages, was a foxpro process running
in background on server that processed appearing request files in folder, and wrote answers
to files for php-servers. This is still reliable.
Anyway there are NoSQL, EasySQL, MySQL, others nowadays. No big reason to revive the dead. 
And btw. the UniCode characters, avatar pictures, video streaming, foxpro has nothing to do with it all.

*/