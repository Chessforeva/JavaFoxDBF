package com.foxdbf;

import java.io.*;

// used documentations:
// http://www.clicketyclick.dk/databases/xbase/format/dbf.html

public class base {
	
	public field[] Field = new field[254];
	public int fcount = 0;
	
	public long reccount = 0;
	public long recno = 0;
	public boolean bof = false;
	public boolean eof = false;
	public boolean deleted = false;
	
	private RandomAccessFile fdbf, ffpt, fcdx;
	private long data_begin_at = 0;
	private long record_size = 0;
	private long fpt_block_size = 0;
	
	/* set true for DOS 2.6 FOXPRO, default is WIN Visual Foxpro
	 * (this source is not for Clipper or DBase tables)
	 */

	public boolean DOS_FOX = false;
	
	/*
	 *  Chars are held in String objects for convenience.
	 *  It lags for special/regional/DOS chars when reading and replacing data in tables.
	 *  To use byte[] array, set to true.
	 */
	public boolean BINARY_CHARS = false;
	public boolean READ_ONLY = false;
	
	// first byte of the file is signature (ignored on use)
	private byte signature = 0;
	private static byte SIG_NO_MEMO = (byte)0x03,
		SIG_WINFOX_WITH_MEMO = (byte)0x30,
		SIG_DOSFOX_WITH_MEMO = (byte)0xF5;

	// CDX information
	@SuppressWarnings("unused")
	private static byte NO_CDX = (byte)0, CDX_NO_MEMO = (byte)0x01, CDX_WITH_MEMO = (byte)0x02;
	
	/* language driver information when creating database
 		use manual at  http://www.clicketyclick.dk/databases/xbase/format/dbf.html#DBF_STRUCT
	 	important for FoxPro when decoding characters
	 */
	@SuppressWarnings("unused")
	private static byte LANG_RUS_WINDOWS = (byte)0xC9, LANG_RUS_DOS = (byte)0x66,
		LANG_WINDOWS_ANSI = (byte)0x03, LANG_DOS_Multilingual = (byte)0x02;
	public byte language_driver = LANG_RUS_WINDOWS;
	
	private boolean fpt_file = false;
	private boolean cdx_file = false;
	
	public String alias;				// the same as ALIAS()
	
	private String dbfname, fptname, cdxname;
	
	public String order;				// the same as ORDER()
	public idx Idx;
	public cdx Cdx;
	
	private static long lsh31bit = (1L<<31);
	private static long lsh32bit = (1L<<32);
	
	//currency formats
	private static int currency_dec = 4, currency_e10 = 10000;	// 10^4	
	
	private void validateFieldType( char ftype )
	{
	boolean valid = (("CNFDLMG").indexOf(ftype)>=0);
	valid = valid || ( (!DOS_FOX) && (("IBTY0").indexOf(ftype)>=0) );
	if(!valid)
		try { throw new IOException ("What a field type is " + ftype + " ?"); }
		catch (IOException e) { e.printStackTrace();
		}
	}
	private boolean isReadOnly()
	{
		if(cdx_file) READ_ONLY = true;
		if(READ_ONLY)
			{
			try { throw new IOException ("Read only mode!"); }
			catch (IOException e) { e.printStackTrace(); }
			}
		return READ_ONLY;
	}
	
	private String Spaces(int n)
	{	return new String(new char[n]).replace("\0", " ");	}
	
	private String fmode()			// file modes
	{	return "r" + (READ_ONLY ? "" : "w" );	}
	
	private long readNumber ( int db, int l )
	{
		long r = 0, b = 1;
		int q=0;
		RandomAccessFile f = ( db==0 ? fdbf : db==1 ? ffpt : fcdx );
		byte[] g = new byte[l];
		try { f.read(g); }
		catch (IOException e) { e.printStackTrace(); }
		
		if(db>0 && l>1) b<<=((l-1)<<3);
		for(int i=0;i<l;i++)
			{
			q = g[i]; if(q<0) q+=0x100;
			r|= b*q;
			if(db>0) b>>=8; else b<<=8;
			}
		return r;
	}
	
	private String readChars ( int db, int ln )
	{
		byte[] b = new byte[ln];
		RandomAccessFile f = ( db==0 ? fdbf : db==1 ? ffpt : fcdx );
		try { f.read(b); } catch (IOException e) { e.printStackTrace(); }
		String s = "";
		for(int i=0;i<ln;i++) s+=(char)b[i];
		return s;
	}
	
	private byte[] readBytes( int db, int ln )
	{
		byte[] b = new byte[ln];
		RandomAccessFile f = ( db==0 ? fdbf : db==1 ? ffpt : fcdx );
		try { f.read(b); } catch (IOException e) { e.printStackTrace(); }
		return b;
	}
	
	private void writeNumber( int db, int ln, long numb )
	{
		long r = numb;
		RandomAccessFile f = ( db==0 ? fdbf : db==1 ? ffpt : fcdx );
		byte[] g = new byte[ln];
		for(int i=0, l=ln;l>0;l--)
			{
			if(db>0) r=(l==1? numb : numb>>((l-1)<<3) );
			int b = (int)(r & 0xff);
			g[i++]=(byte)b;
			if(db==0) r>>=8;
			}
		try { f.write(g); }
		catch (IOException e) { e.printStackTrace(); }
		
	}
	
	private void writeChars( int db, int ln, String sc )
	{
		RandomAccessFile f = ( db==0 ? fdbf : db==1 ? ffpt : fcdx );
		int l = sc.length();
		int ml = Math.min(ln,l);
		try { f.writeBytes( (l==ml ? sc : sc.substring(0,ml)) ); }
		catch (IOException e) { e.printStackTrace(); }
		if(ln>l)
			{
			String s = new String(new char[ln-l]).replace("\0", " ");
			try { f.writeBytes(s); } catch (IOException e) { e.printStackTrace(); }
			}
	}
	
	private void writeBytes( int db, int ln, byte[] b )
	{
		RandomAccessFile f = ( db==0 ? fdbf : db==1 ? ffpt : fcdx );
		int l = b.length;
		try { f.write(b,0,Math.min(ln,l)); }
		catch (IOException e) { e.printStackTrace(); }
		if(ln>l)
			{
			try { f.writeBytes(Spaces(ln-l)); }
			catch (IOException e) { e.printStackTrace(); }
			}
	}
	
	private void writeSpc (int db, int l, boolean spc)
	{
		String s = new String(new char[l]);
		writeChars(db,l, (spc ? s.replace("\0", " ") : s) );
	}
	
	private void getreccount()
	{
		reccount = 0;
		try { fdbf.seek(4); } catch (IOException e) {}
		reccount = readNumber(0,4);
	}
	
	private void upd_reccount()
	{
		if(isReadOnly()) return;
		try { fdbf.seek(4); } catch (IOException e) {}
		writeNumber(0,4, reccount );
	}
	
	private void getdatabeginptr()
	{
		try { fdbf.seek(0); } catch (IOException e) {}
		try { signature = (byte) fdbf.readUnsignedByte(); } catch (IOException e) { e.printStackTrace(); }
		fpt_file = false;	//( signature == (DOS_FOX ? SIG_DOSFOX_WITH_MEMO : SIG_WINFOX_WITH_MEMO) );
		
		try { fdbf.seek(8); } catch (IOException e) {}
		data_begin_at = readNumber(0,2);
		record_size = readNumber(0,2);
		
		try { fdbf.seek(28); } catch (IOException e) {}
		cdx_file = ( readChars(0,1).charAt(0) > 0 );		
	}
	
	private void getfptbeginptr()
	{
		try { ffpt.seek(6); } catch (IOException e) {}
		fpt_block_size = readNumber(1,2);
	}
	
	
	private void getfields()
	{
	fcount = 0;
	for(int k=0;;)
		{
		k+=32;
		try { fdbf.seek(k); } catch (IOException e) {}
		String fn = readChars(0,1);
		if(fn.charAt(0)==0xd) break;
		fn+=readChars(0,10);
		field y = new field();
		y.fname = fn.replace((char)0,' ').trim();
		char f = readChars(0,1).charAt(0);
		y.ftype = f; validateFieldType(f);
		if(f=='M' || f=='G') fpt_file = true;
		y.fpos = readNumber(0,4);
		y.fsize = (int) readNumber(0,1);
		if(f=='B' || f=='Y') y.fsize = 30;	// just increase enough to see
		if(f=='T') y.fsize = 22;
		y.fsize_dec = (int) readNumber(0,1);
		if(f=='Y') y.fsize_dec = currency_dec;
		y.binflag = (readNumber(0,1) == 4);
		Field[ fcount++ ] = y;
		}
	}
	
	private void geteof()
	{
		if(recno>reccount)
			{
			recno = reccount+1;
			if(Idx!=null) Idx.recno = recno;
			else if(cdx_file) Cdx.recno = recno;
			}
		eof = (recno>reccount);
	}
	
	private void getbof()
	{
		if(recno<0)
			{
			recno = 0;
			if(Idx!=null) Idx.recno = recno;
			else if(cdx_file) Cdx.recno = recno;
			}
		bof = (recno<1);
	}	

	public boolean used()	{
		return (fdbf!=null);
	}
	
	private void setfnames( String filename )
	{
		if( used() ) use();		// close previous files
		
		String f = filename;
		
		int l = f.length();
		if( l>4 && f.toLowerCase().substring(l-4).equals(".dbf") )
			{ dbfname = f; f = f.substring(0,l-4);  }
		else dbfname = f + ".DBF";
		
		fptname = f + ".FPT";
		cdxname = f + ".CDX";
		for(int i=f.length(); i>0;)
			{
			char c = f.charAt(--i);
			if(c=='/' || c=='\\') { f=f.substring(i+1); break; }
			}
		alias = f.toUpperCase();
		order = "";
	}
	
	public void use ( String filename )
	{		
		setfnames( filename );	
		try {
			fdbf = new RandomAccessFile( new File( dbfname ), fmode());
			} catch (FileNotFoundException e1) { e1.printStackTrace(); }
		
		getdatabeginptr();
		getreccount();
		getfields();
		
		if(fpt_file)
			{
				try {
				ffpt = new RandomAccessFile( new File(fptname ), fmode());
				} catch (FileNotFoundException e1) { e1.printStackTrace(); }
				
				getfptbeginptr();
			}
		if(cdx_file)
			{
			READ_ONLY = true;
			try {

				fcdx = new RandomAccessFile( new File( cdxname ), fmode());
				} catch (FileNotFoundException e1) { e1.printStackTrace(); }
			Cdx = new cdx();
			Cdx.fcdx = fcdx;
			Cdx.read_header();
			
			}
		go_recno(1);
	}

	public void use()
	{
		try { fdbf.close(); } catch (IOException e) { e.printStackTrace(); }
		if(fpt_file) try { ffpt.close(); } catch (IOException e) { e.printStackTrace(); }
		if(cdx_file) try { fcdx.close(); } catch (IOException e) { e.printStackTrace(); }
		set_order_to();
		alias = "";
		Field = new field[254];
		fcount = 0;
		recno = 0;
		if(Idx!=null) Idx.recno = recno;
		else if(cdx_file) Cdx.recno = recno;
		reccount = 0;
		geteof(); getbof();
	}
	
	public void create( String filename, String struct )
	{
		if(isReadOnly()) return;
		
		setfnames( filename );
		try {
			fdbf = new RandomAccessFile( new File( dbfname ), fmode());
			} catch (FileNotFoundException e1) { e1.printStackTrace(); }
		try { fdbf.setLength(0); } catch (IOException e) { e.printStackTrace(); }
		writeSpc(0,32,false);
		fpt_file = false;
		
		String s[] = struct.toUpperCase().split(",");
		int fcount=0, fpos = 1, fsize, fsize_dec;
		char ftype;
		String fname;
		for(int i=0,a,b; i<s.length; i++)
			{
			String sp = s[i].trim();
			a = sp.indexOf(" ");
			fname = sp.substring(0,a);
			if(fname.length()>11) fname = fname.substring(0,11);
			fsize = 0; fsize_dec = 0;
			sp = sp.substring(a+1).trim();
			ftype = sp.charAt(0);
			validateFieldType(ftype);
			if(ftype=='M' || ftype=='G')
				{ fsize=(DOS_FOX ? 10 : 4); fpt_file = true; }
			else if(ftype=='I') fsize=4;
			else if(ftype=='L') fsize=1;
			else if(("DBTY").indexOf(ftype)>=0) fsize=8;
			if(ftype=='Y') fsize_dec = currency_dec;
			a = sp.indexOf("(");
			if(a>0)
			{
				sp = sp.substring(a+1).trim();
				b = sp.indexOf(")");
				if(b>=0) sp = sp.substring(0,b);
				fsize = Integer.parseInt(sp.trim());
				if(b<0)
					{
					i++; sp = s[i].trim();
					b = sp.indexOf(")");
					if(b>=0) sp = sp.substring(0,b);
					fsize_dec = Integer.parseInt(sp.trim());	
					}
				}
				writeChars(0,fname.length(),fname);
				writeSpc(0,11-fname.length(),false);
				
				writeChars(0,1,""+ftype);
				writeNumber(0,4,fpos); fpos += fsize;
				writeNumber(0,1,fsize);
				int dc1 = ((ftype=='B' || ftype=='Y') ? 4 : fsize_dec);
				writeNumber(0,1,dc1);
				int dc2 = ((ftype=='B' || ftype=='Y' || ftype=='I') ? 4 : 0);
				writeNumber(0,1,dc2);
				writeSpc(0,13,false);
				fcount++;
			}
		writeChars(0,1,""+(char)0xd);		// the end of structure
		
		int emp_spc = (1<<9);				// add some bytes
		writeSpc(0, emp_spc, false);		// empty block space
		
		try { fdbf.seek(0); } catch (IOException e) {}
		signature = ( fpt_file ?  (DOS_FOX ? SIG_DOSFOX_WITH_MEMO : SIG_WINFOX_WITH_MEMO) : SIG_NO_MEMO );
		try { fdbf.writeByte(signature); } catch (IOException e) { e.printStackTrace(); }

		try { fdbf.seek(8); } catch (IOException e) {}
		writeNumber(0,2,((fcount+1)<<5)+emp_spc+1);				// data begin at
		writeNumber(0,2,fpos);									// record size
		
		if(!DOS_FOX)
		{
		try { fdbf.seek(29); } catch (IOException e) {}
		writeChars(0,1,""+(char)language_driver);				// Language driver
		}
		

		reccount = 0;	//upd_reccount();
		getdatabeginptr();
		getfields();

		if(fpt_file)
			{
				if(!DOS_FOX)
				{
					try { fdbf.seek(28); } catch (IOException e) {}
					writeChars(0,1,"" + (char)0x02);		// MDX flag
				}
			
				try {
					ffpt = new RandomAccessFile( new File( fptname ), fmode());
				} catch (FileNotFoundException e1) { e1.printStackTrace(); }
				
				try { ffpt.setLength(0); } catch (IOException e) { e.printStackTrace(); }
				fpt_block_size = 64;	// default block size for memo
				writeNumber(1,4,(512/fpt_block_size) );		// the next free block
				writeNumber(1,2,0);
				writeNumber(1,2,fpt_block_size);
				writeSpc(1,504,false);				
			}
		go_recno(1);
	}

	public int FieldI( String fname )
	{
		String fd = fname.toUpperCase();
		for(int i=0;i<fcount;i++)
			if( Field[i].fname.equals(fd) ) return i;
		return -1;
	}

	private long recordpos() { return data_begin_at + ((recno-1)*record_size); }
	
	private void getfieldsValues()
	{
		if(recno>0 && recno<=reccount)
		{
		try { fdbf.seek( recordpos() ); } catch (IOException e) {}
		deleted = (readChars(0,1).charAt(0)=='*');
		for(int i=0;i<fcount;i++)
			{
			field y = Field[i];
			y.setByString("");
			
			if(y.ftype=='0') y.setByLong( readNumber(0,y.fsize) );
			else if(y.ftype=='I')
			{
				long q = readNumber(0,4);	// signed integer			
				if((q & lsh31bit)!=0) q-=lsh32bit;		// if negative
				y.setByLong(q);
			}
			else if(y.ftype=='B') y.setByLongIEEEforBfield( readNumber(0,8), 0 );	//read binary
			else if(y.ftype=='Y') 
				{
				double dou = readNumber(0,8);
				dou /= currency_e10;
				y.setByDouble( dou );
				}
			else if(y.ftype=='T')
			{
				long dt = readNumber(0,4), ms = readNumber(0,4);
				y.setByString( datestr.stringFromDateTime(dt,ms) );
			}
			else if(y.ftype=='C')
			{
				if(BINARY_CHARS || y.binflag) y.setBinaries( readBytes(0,(int)y.fsize) );
				else y.setByString( readChars(0,y.fsize) );
			}
			else if(y.ftype=='M' || y.ftype=='G')
			{
				long mptr = 0;
				if(DOS_FOX)
					{
					String p = readChars(0,10).trim();
					if(p.length()>0) mptr = Long.parseLong(p);
					}
				else mptr = readNumber(0,4);
				if(mptr!=0)
					{
					try { ffpt.seek( mptr * fpt_block_size ); } catch (IOException e) {}	
					readNumber(1,4);		// ignoring, 0-template, 1 if text string
					long len = readNumber(1,4);
					if(len>0)
						{
						if(BINARY_CHARS || y.ftype=='G' || y.binflag) y.setBinaries( readBytes(1,(int)len) );
						else y.setByString( readChars(1,(int)len) );
						}
					}
			}
			else y.setByString( readChars(0,y.fsize) );
			}
			updateseek();
		}
		geteof(); getbof();

	}
	
	public void go_top()
	{
		if(order.length()>0)
			{
			if(Idx!=null)
				{
				Idx.goTop();
				go_recno( Idx.found ? Idx.sRecno : 0 );
				}
			else 
				{
				Cdx.goTop();
				go_recno( Cdx.found ? Cdx.sRecno : 0 );
				}
			}
		else go_recno(1);
	}
	
	public void go_recno( long new_recno )
	{
		recno = new_recno;
		if(Idx!=null) Idx.recno = recno;
		else if(cdx_file) Cdx.recno = recno;
		geteof(); getbof();
		getfieldsValues();
	}
	
	public void skip(long n)
	{
		if(n==0) n=1;
		if(order.length()>0)
			{		
			if(Idx!=null)
				{
				Idx.skip(n);
				if(Idx.found) go_recno(Idx.sRecno);
				else go_recno(n<0 ? 0 : reccount+1);
				}
			else
				{
				Cdx.skip(n);
				if(Cdx.found) go_recno(Cdx.sRecno);
				else go_recno(n<0 ? 0 : reccount+1);
				}
			}
		else go_recno( recno+n );
	}
	
	public void go_bottom()
	{		
		if(order.length()>0)
			{
			if(Idx!=null)
				{
				Idx.goBottom();
				go_recno( Idx.found ? Idx.sRecno : reccount+1 );
				}
			else 
				{
				Cdx.goBottom();
				go_recno( Cdx.found ? Cdx.sRecno : reccount+1 );
				}
			}
		else go_recno( reccount );
	}

	private void addreccount()
	{
		getreccount();		// update
		recno = reccount+1;
		reccount = recno;
		if(Idx!=null) Idx.recno = recno;
		else if(cdx_file) Cdx.recno = recno;
		upd_reccount();		
	}
	
	public void append_blank()
	{
		if(isReadOnly()) return;
		
		addreccount();
		try { fdbf.seek( recordpos() ); } catch (IOException e) {}
		writeChars(0,1," ");	// not deleted
		for(int i=0;i<fcount;i++)
		{
			field y = Field[i];
			if(y.ftype=='0') writeNumber(0,y.fsize, 0 ); //write binary 0
			if(y.ftype=='I') writeNumber(0,4, 0 ); //write binary 0
			if(("BTY").indexOf(y.ftype)>=0) writeNumber(0,8, 0 ); //write binary 0
			if(y.ftype=='M' || y.ftype=='G')
				{
				if(DOS_FOX) writeChars(0,10,"");
				else writeNumber(0,4, 0 ); //write binary, not string value
				}
			else writeChars(0,y.fsize, "");		//string of char		
		}
		getfieldsValues();
	}
	
	private void replacefielddata( int i )
	{
		if(isReadOnly()) return;
		
		field y = Field[i];
		if(y.ftype=='0') writeNumber(0,y.fsize,y.longValue());
		else if(y.ftype=='I') writeNumber(0,4,y.longValue()); //write binary, not string value
		else if(y.ftype=='B') writeNumber(0,8,y.longValueIEEEforBfield());
		else if(y.ftype=='Y')
			{
			double dou = y.doubleValue();
			dou *= currency_e10;
			writeNumber(0,8, (long)dou );
			}
		else if(y.ftype=='T')
			{
			if(y.value.trim().length()==0) writeNumber(0,8,0);
			else
				{
				writeNumber(0,4,datestr.dateFromDateTime(y.value));
				writeNumber(0,4,datestr.timeFromDateTime(y.value));
				}
			}
		else if(y.ftype=='C')
			{
			if((BINARY_CHARS || y.binflag) && y.binValue!=null) writeBytes(0,y.fsize,y.binValue);
			else writeChars(0,y.fsize,y.value);
			}
		else if(y.ftype=='M' || y.ftype=='G')
			{
			long len = ( ((BINARY_CHARS || y.ftype=='G' || y.binflag ) && y.binValue!=null) ?
						y.binValue.length : y.value.length() );
			if(len==0)
				{
				if(DOS_FOX) writeChars(0,10,"");
				else writeNumber(0,4, 0 ); //write binary, not string value
				}
			else
				{
				try { ffpt.seek(0); } catch (IOException e) {}
				long free_block = readNumber(1,4);
				if(DOS_FOX) writeChars(0,10,String.format("%10d",free_block));
				else writeNumber(0,4,free_block );
					
				try { ffpt.seek( free_block * fpt_block_size ); } catch (IOException e) {}
				writeNumber(1,4,1);		// ignoring, 0-template, 1 if text string
				writeNumber(1,4,len);
				if((BINARY_CHARS || y.ftype=='G' || y.binflag) && y.binValue!=null)
							writeBytes(1,(int)len,y.binValue);
				else writeChars(1,(int)len,y.value);
					
				long f = 0, fs = (8+len), fn = 0;
				for(;fn<fs;f++) fn+= fpt_block_size;					
				if(fn>fs) writeSpc(1, (int)(fn-fs) ,false);	// add 0-bytes till block end
				try { ffpt.seek(0); } catch (IOException e) {}
				writeNumber(1,4,free_block+f);	// new pointer to free block
				}
			}
		else writeChars(0,y.fsize, y.value);		
	}
	
	private void replacedata( boolean repl )
	{
		if(isReadOnly()) return;
		
		try { fdbf.seek( recordpos() ); } catch (IOException e) {}
		writeChars(0,1,(repl && deleted ? "*" : " "));
		for(int i=0;i<fcount;i++) replacefielddata(i);
		update_key();
		getfieldsValues();
	}
	
	public void replace_from_memory() { replacedata( true ); }
	
	public void replace_field_from_memory( int i )
	{
		if(isReadOnly()) return;
		
		try { fdbf.seek( recordpos() + Field[i].fpos ); } catch (IOException e) {}
		replacefielddata(i);
		update_key();
		getfieldsValues();
	}
	
	public void insert_from_memory()
	{
		if(isReadOnly()) return;
		
		addreccount(); replacedata( false );
	}
	
	public void delete()
	{
		if(isReadOnly()) return;
		
		try { fdbf.seek( recordpos() ); } catch (IOException e) {}
		writeChars(0,1,"*"); deleted = true;
	}

	public void recall()
	{
		if(isReadOnly()) return;
		
		try { fdbf.seek( recordpos() ); } catch (IOException e) {}
		writeChars(0,1," "); deleted = false;
	}
	
	public byte[] readBinaryFromFile( String filename )
	{
		RandomAccessFile f = null;
		try { f =  new RandomAccessFile( new File( filename ),"r"); }
		catch (FileNotFoundException e1) { e1.printStackTrace(); }
		byte[] b = null;
		try { b = new byte[ (int) f.length() ]; } catch (IOException e) { e.printStackTrace(); }
		try { f.read(b); } catch (IOException e) { e.printStackTrace(); }
		try { f.close(); } catch (IOException e) { e.printStackTrace(); }
		return b;
	}
	public void writeBinaryToFile( String filename, byte[] b )
	{
		if(isReadOnly()) return;
		
		RandomAccessFile f = null;
		try { f =  new RandomAccessFile( new File( filename ), fmode()); }
		catch (FileNotFoundException e1) { e1.printStackTrace(); }
		try { f.setLength(0); } catch (IOException e1) { e1.printStackTrace(); }
		try { f.write(b); } catch (IOException e) { e.printStackTrace(); }
		try { f.close(); } catch (IOException e) { e.printStackTrace(); }
	}

	public void set_order_to()
	{
		if(Idx!=null)
			{
			if(Idx.fidx!=null)
				{
				try { Idx.fidx.close(); }
				catch (IOException e) { e.printStackTrace(); }
				}
			Idx = null;
			}
		order = "";
		if(Cdx!=null) Cdx.I = -1;
	}

	private boolean canIfNoCdx()	// if CDX is there, IDX disabled
	{
		if(cdx_file)
			{
			try { throw new IOException ("Do not use IDX over CDX!"); }
			catch (IOException e) { e.printStackTrace(); }
			return false;
			}
		return true;
	}
	
	public void set_order_to_idx( String idxFileName )
	{
		if(canIfNoCdx())
		{
		order = "[idx]";
		Idx = new idx();
		try { Idx.fidx =  new RandomAccessFile( new File( idxFileName ), fmode()); }
		catch (FileNotFoundException e1) { e1.printStackTrace(); }
		Idx.read_header();
		Idx.recno = recno;
		updateseek();
		}
	}
	
	public void set_cdx_order( String tag )
	{
		if(Cdx!=null)
			{
			Cdx.setOrdByTag(tag);
			if(Cdx.I>=0) order = Cdx.TagNames[Cdx.I];
			Cdx.recno = recno;
			updateseek();
			}
	}

	public void create_idx( String idxFileName, String indexOnKeyString )
	{
		if(isReadOnly()) return;
		
		if(canIfNoCdx())
		{
		order = "[idx]";
		Idx = new idx();
		try { Idx.fidx =  new RandomAccessFile( new File( idxFileName ), fmode()); }
		catch (FileNotFoundException e1) { e1.printStackTrace(); }
		try { Idx.fidx.setLength(0); } catch (IOException e) { e.printStackTrace(); }
		
		Idx.keyString = indexOnKeyString.toUpperCase().trim();
		Idx.forString = "";
		
		// to obtain key length
		// be careful if self written
		PREPARE_KEY_FROM_DATA( Idx );
				
		int keyLen = Idx.searchKey.length;
				
		Idx.writeHeaderToEmptyFile( keyLen );
		
		try { Idx.fidx.close(); } catch (IOException e) { e.printStackTrace(); }
		
		set_order_to_idx( idxFileName );
		}
	}

	byte[] cSKey;				// holds current index key to know that index modified
	
	// finds pointer in index for the current record by using data in memory (field values)
	// Prepares for skips/modifying data
	private void updateseek()
	{
		if(order.length()>0 && recno>0 && recno<=reccount)
			{
			idx O = ( Cdx==null ? Idx :
				( Cdx.I>=0 ? Cdx.Idx[ Cdx.I ] : null ));			

			if(O!=null)
			{		
			PREPARE_KEY_FROM_DATA( O );		// =EVALUATE( KEY() ) gives current result
			
			if(O.searchKey!=null)
				{
				cSKey = O.searchKey.clone();
				
				if( Cdx==null ) Idx.seek(true);
				else Cdx.seek(true);
				
				// unique is the same as "FOR=the first element only"
				// So, in case of .F., there is no pointer to the record in index for the data
				if(!(O.UNIQUE || O.FOR))
					{
					if( O.sRecno!=recno )
						{
						// If record doesn't match* by seek, try finding backwards from the bottom.
						// Of course, this record finding isn't norm. Mostly for debugging only.
						//  *keys for overflow numbers sometimes also are incorrect
						// (but databases should not contain data like that)
						if( Cdx==null ) Idx.findFullscanIndexByRecno();
						else Cdx.findFullscanIndexByRecno();
						}
					if(O.sRecno!=recno)		// Idx.recno is equal
						{
						try { throw new IOException ("Index doesn't match! For recno=" + 
								String.valueOf(recno) + ",  index recno=" + String.valueOf(O.sRecno) ); }
						catch (IOException e) { e.printStackTrace(); }
						}
					}
				}
			}
			}
	}
	
	// this is index expression to data connection
	private void PREPARE_KEY_FROM_DATA( idx I )
	{
		I.searchKey = null;
		
		String ss = I.keyString.toUpperCase();		// keys FIELD+... too
		int i=-1;
		for(i=fcount-1; i>=0; i--)
			{
			String s = Field[i].fname;
			if(ss.equals(s) || ss.startsWith(s+"+")) break;
			}
		boolean cant = (i<0);
		if(!cant)	// field is a key
			{
			field y = Field[i];
			I.sType = y.ftype;
							
			if( (("NFBIY").indexOf(y.ftype)>=0) &&
					((y.value.indexOf("*")>=0) || (y.value.indexOf("NaN")>=0)))	// overflow keys
			{	// always at the end of the index
				I.prepareSeekByReadyKey( 0x8000000000000000L );
				// sometimes  0xfff0000000000000L;
			}				
			else	// normal values
			{
			if(y.ftype=='C')
				{
				y.padr();
				I.prepareSeekByString( y.value );
				}
			else if(("NFB").indexOf(y.ftype)>=0)
				{
				if(y.fsize_dec>0) I.prepareSeekByDouble( y.doubleValue() );
				else I.prepareSeekByLong( y.longValue() );
				}
			else if (y.ftype=='D') I.prepareSeekByDateString( y.value );
			else if (y.ftype=='Y')
				{
				double dou = y.doubleValue();
				dou *= currency_e10;
				I.prepareSeekByCurrency( dou );
				}
			else if (y.ftype=='I') I.prepareSeekBybinInt( y.longValue() );
			else if (y.ftype=='L') I.prepareSeekByBoolean( y.value.charAt(0) );		
			else if (y.ftype=='T')
				{
				long dt = 0;
				if(y.value.trim().length()>0) dt = datestr.dateFromDateTime(y.value)<<32;
				I.prepareSeekDateTime( dt );
				}
			else cant = true;	// can not anyway			
			}
			
		if((!cant) && I.FOR)
			{
			// don't know how to make FOR filter from expression, write code here
			// if .T., key is good already
			// if .F., simply set key to null and don't search/apply
			try { throw new IOException ("Don't know FOR() result from expression " + I.forString  + "!"); }
			catch (IOException e) { e.printStackTrace(); }
			
			//if(false) I.searchKey = null;
			}

		if(cant)
			{		
			
				// don't know how to make index key from expression, write code here
				try { throw new IOException ("Don't know key from index expression " + I.keyString  + "!"); }
				catch (IOException e) { e.printStackTrace(); }
				
				//==== template to prepare I.searchKey
				//====   for sample index   INDEX ON DTOS(Birth) TAG BirthD
				//====    to seek this record in database
				//if(alias.equals("MYBASE") && order.equals("BIRTHD"))
				//  I.prepareSeekByString( db.Field[ db.FieldI("Birth") ].stringValue() );

				}
			}
	}
	
	public boolean SEEK()
	{	
		if(order.length()>0)
			{
			idx O = ( Cdx==null ? Idx :
				( Cdx.I>=0 ? Cdx.Idx[ Cdx.I ] : null ));
			
			if(O!=null)
				{
				PREPARE_KEY_FROM_DATA( O );
				
				if(Idx!=null) Idx.seek(false);
				else Cdx.seek(false);
				
				go_recno( O.found || (O.sRecno>0) ? O.sRecno : reccount+1 );
				// otherwise sRecno contains nearest
				
				return O.found;
				}
			}

		return false;
	}
	
	public void update_key()
	{	
		if(isReadOnly()) return;
		
		if(Idx!=null)
			{
			PREPARE_KEY_FROM_DATA( Idx );
			if(cSKey==null || (!cSKey.equals(Idx.searchKey)))
				Idx.replace_key();	// this is a hack too much
			}
		if(cdx_file)
			{
			// TODO:
			// update all key values for this record for all tags
			}
	}
}
