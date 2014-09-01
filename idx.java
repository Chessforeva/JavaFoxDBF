package com.foxdbf;

import java.io.*;

public class idx {

	public RandomAccessFile fidx;	// idx file pointer

	// HEADER
	public long top;				// pointer to top-node
	public long free;				// pointer to free-node
	public long fileend;			// pointer to end of file
	public int key_len;				// length of key
	public byte flags;				// some flag-bits
	public boolean UNIQUE;			// is unique
	public boolean FOR;				// is for clause
	public byte signature;			// file signature
	String keyString;				// index expression
	String forString;				// for expression
	
	// working on PAGE
	private int attrib;				// attribute of page
	//0 - top, 1 - index, 2 - page, 3 - the last page
	boolean LEAF;
	private int key_cnt;			// count of keys in this page
	private long left_page;			// left page pointer or -1
	private long right_page;		// right page pointer or -1
	
	private byte[][] pgC = new byte[100][100];	// max.100 keys with 100 chars
	private long[] pgR = new long[100];
	
	private long nNode;				// current page pointer
	private int nKey;				// current key pointer
	
	byte[] searchKey;				// bytes to search
	
	public boolean found;			// found() on seek
	public long sRecno;				// last record pointer found
	public byte[] nFoundKey;		// this array is a key in index
	
	public long recno;				// current record in database set by upper level
	
	private static long lsh63bit = (1L<<63);
	private static long lsh31bit = (1L<<31);
	
	private void ssByBits(long b, int L)
	{
		searchKey = new byte[L];
		for(int i=L;i>0;i--,b>>=8) searchKey[i-1]=(byte) (b & 0xff);
	}
	
	private void prepLENumb( double d )	// convert to be as simple ASCII bytes to compare by bytes left to right
	{
		long b = Double.doubleToLongBits(d); //b = Long.reverseBytes(b);
		if((b & lsh63bit)!=0) b=~b; else b|=lsh63bit;
		ssByBits(b,8);
	}
	
	public void prepareSeekDateTime( long d )
	{
		prepareSeekByLong( d );
		if(d!=0) searchKey[0]-=2;		// strange but working way
	}
	
	public void prepareSeekBybinInt( long d )
	{
		long b = d;
		if((b & lsh31bit)==0) b|=lsh31bit; else b^=lsh31bit;		
		ssByBits(b,4);
	}

	public void prepareSeekByCurrency( double d )
	{
		long b = (long)d;
		b^=lsh63bit;
		ssByBits(b,8);
	}
	
	public void prepareSeekByString( String s ) { searchKey = s.getBytes(); }
	
	public void prepareSeekByBoolean( char b )
	{
		searchKey = new byte[1]; searchKey[0] = (byte) (b == 'T' ? 'T' : 'F');
	}
	
	public void prepareSeekByLong( long numb ) { double d = numb; prepLENumb(d); }
	
	public void prepareSeekByDouble( double d )	{ prepLENumb(d); }
	
	public void prepareSeekByDateString( String s )
	{ long Jd = (s.trim().length()==0 ? 0 : datestr.toJulian(s)); double d = Jd; prepLENumb(d); }

	public void prepareSeekByReadyKey( long b ) { ssByBits(b,8); }
	
	private long readNumber ( int side, int l, boolean FF )
	{
		long r = 0, b = 1;
		int q=0;
		if(side>0 && l>1) b<<=(8*(l-1));
		for(;l>0;l--)
			{
			try { q = fidx.readUnsignedByte(); } catch (IOException e) { e.printStackTrace(); }
			if(q!=0xff) FF=false;
			r+= b*q;
			if(side>0) b>>=8; else b<<=8;
			}
		return (FF ? -1 : r );
	}
	
	private void writeNumber ( int side, int ln, long numb, boolean FF )
	{
		long r = numb;
		for(int l=ln;l>0;l--)
			{
			if(side>0) r=(l==1? numb : numb>>(8*(l-1)) );
			int b = (int)((FF && numb==-1) ? 0xff : (r & 0xff));
			try { fidx.writeByte(b); } catch (IOException e) { e.printStackTrace(); }
			if(side==0) r>>=8;
			}
	}
	
	private String readChars ( long l )
	{
		String s = "";
		for(;l>0;l--)
			{
			try {
				byte c = (byte) fidx.readUnsignedByte(); s+=(char)c;
				} catch (IOException e) { e.printStackTrace(); }
			}
		return s;
	}
	
	private String writeChars ( int ln, String s )
	{
		for(int l=0;l<ln;l++)
			{
			try { fidx.writeByte( s.length()>l ? s.charAt(l) : 0 ); } catch (IOException e) { e.printStackTrace(); }
			}
		return s;
	}
	
	public void read_header()
	{
		try { fidx.seek(0); } catch (IOException e) { e.printStackTrace(); }
		top = readNumber(0,4,true);
		free = readNumber(0,4,true);
		fileend = readNumber(0,4,false);
		key_len = (int)readNumber(0,2,false);
		try { flags = (byte) fidx.readUnsignedByte(); }
		catch (IOException e) { e.printStackTrace(); }
		UNIQUE = ((flags & 1 )>0);
		FOR = ((flags & 8 )>0);
		try { signature = (byte) fidx.readUnsignedByte(); }
		catch (IOException e) { e.printStackTrace(); }
		keyString = readChars(220).replace("\0", " ").trim();
		forString = readChars(220).replace("\0", " ").trim();
	}
	
	private void readPage()		// reads page information
	{
		attrib = (int)readNumber(0,2,false);		// attribute of node 0 - index node, 1 - start page , 2 - end page
		LEAF = (attrib>1);
		key_cnt = (int)readNumber(0,2,false);		// count of keys in this block
		left_page = readNumber(0,4,true);	// left node or -1
		right_page = readNumber(0,4,true);	// right node or -1
		for(int i=0;i<key_cnt;i++)
			{
			// read keys
			for(int k=0; k<key_len; k++ )
					try { pgC[i][k] = (byte) fidx.readUnsignedByte(); }
					catch (IOException e) { e.printStackTrace(); }

			pgR[i] = readNumber(1,4,false);		// read record numbers and pointers
			}
		nKey = 0;
	}
	
	private void writePage()		// writes page information
	{
		writeNumber(0,2,attrib,false);
		writeNumber(0,2,key_cnt,false);		// count of keys in this block
		writeNumber(0,4,left_page,true);	// left node or -1
		writeNumber(0,4,right_page,true);	// right node or -1
		for(int i=0;i<key_cnt;i++)
			{
			// write keys
			for(int k=0; k<key_len; k++ )
					try { fidx.writeByte(pgC[i][k]); }
					catch (IOException e) { e.printStackTrace(); }

			writeNumber(1,4,pgR[i],false);
			}
		writeChars(500-(key_cnt*(key_len+4)),"");
	}
		
	public void goNode()		// goes to the node page
	{
		try { fidx.seek(nNode); } catch (IOException e) { e.printStackTrace(); }
	}
	
	private int compare( int i )		// this compares keys 
	{
		int r=0;
		for(int n=0; r==0 && n<searchKey.length; n++ )
			{
			int a = pgC[i][n], b = searchKey[n];
			if(a<0) a+=256; if(b<0) b+=256;		// to unsigned
			if(a<b) r=-1; else if(a>b) r=1;
			}
		return r;
	}
	
	// SEEK function with 0 / or RECNO for current record seeking in index
	public void seek( boolean exact_recno )
	{
		if(exact_recno && isKeyCurrent())
			{
			found = true;		// not search if it's ready
			return;
			}
		
		nNode = top; 
		sRecno = 0;
		int i=0, went=0,stop=0;
		found = false;
		
		for(;stop==0;)
			{
			
			for(;;)		// find non-empty page
				{
				goNode();
				readPage();
				if(key_cnt>0) break;
				else if( went<=0 && left_page>0 )
					{
					nNode = left_page; went = -1;
					}
				else if( went>=0 && right_page>0)
					{
					nNode = right_page; went = 1;
					}
				else break;			// should be only on empty index
				}
			
			stop = 1;
			i = -1;
			
			for( nKey=0; nKey<key_cnt; nKey++)
				{
				if(LEAF) sRecno = pgR[nKey];				// last processed record
				else nNode = pgR[nKey];						// next page pointer
				
				i=compare(nKey);						// compare keys
				
				if(i==0 && LEAF && exact_recno && sRecno!=recno)
					{
					if(nKey==0 && went<=0)		// unfortunately, we should try left keys
						{
						if(left_page>0) i=1;
						}
					else i=-1;		// not this, skip to the right
					}
				
				if(nKey==0 && i>0 && went<=0 && left_page>0)	// try go to the left
					{
					nNode = left_page; went = -1; stop = 0; break;
					}
				if(nKey==key_cnt-1 && i<0 && went>=0 && right_page>0)	// try go to the right
					{
					nNode = right_page; went = 1; stop = 0; break;
					}
			
				if(LEAF)					// data of records
					{						
					if(i==0 || (exact_recno && sRecno==recno) )
						{
						found = true;		// YES!
						break;
						}
					}
				else { stop = 0; went = 0;  }		// indexes, go next page
				if(i>=0) break;		// save nKey, currentKey>=searchKey in index
				}
			}
		if(nKey<0) nKey = 0;
		nFoundKey = pgC[nKey];
	}
		
	// This finds RECNO in index slowly
	public void findFullscanIndexByRecno()
	{
		goBottom();
		found = false;
		
		for(;;)
			{
			if(key_cnt==0) break;
			if(!LEAF)	// index page again???
				{
				try { throw new IOException ("Bad index, leaf page points to index!"); }
				catch (IOException e) { e.printStackTrace(); }
				break;
				}
			else
				{
				for( nKey=key_cnt-1; nKey>=0; nKey--)
					{
					sRecno = pgR[nKey];				// last processed record
					if(sRecno==recno) 
						{
						found = true;
						break;
						}
					}
				if(found) break;
				else {
					if(left_page>0)
						{
						for(;;)
							{
							nNode=left_page;
							goNode();
							readPage();
							if(key_cnt>0 || left_page<0) break;
							}
						}
					}
				}
			}
		if(nKey<0) nKey = 0;
		nFoundKey = pgC[nKey];
	}
	
	// goes top in index
	public void goTop()
	{
		nNode = top; 
		sRecno = 0;
		found = false;
		
		for(;;)
			{
			goNode();
			readPage();
			if(left_page>0) nNode=left_page;
			else
				{
				for(;;)
					{
					if(key_cnt>0 || right_page<0) break;
					nNode = right_page;
					goNode();
					readPage();
					}
				nKey = 0;
				if(LEAF)
					{
					found = true;
					sRecno = pgR[nKey];
					break;
					}
				else nNode = pgR[nKey];
				}
			}
	}

	// goes bottom in index
	public void goBottom()
	{
		nNode = top; 
		sRecno = 0;
		found = false;
		
		for(;;)
			{
			goNode();
			readPage();
			if(right_page>0) nNode=right_page;
			else
				{
				for(;;)
					{
					if(key_cnt>0 || left_page<0) break;
					nNode = left_page;
					goNode();
					readPage();
					}
				nKey = key_cnt-1;
				if(LEAF)
					{
					found = true;
					sRecno = pgR[nKey];
					break;
					}
				else nNode = pgR[nKey];
				}
			}
	}

	// goes bottom in index
	public void skip(long n)
	{
		int d = ((n<0) ? -1 : 1);
		for(long q=0;q!=n;q+=d)
			{		
			found = false;
			if(d<0)		// skip back
				{
				if(nKey==0)
					{
					if(left_page>0)
						{
						for(;;)
							{
							nNode = left_page;
							goNode();
							readPage();
							if(key_cnt>0 || left_page<0) break;
							}
						if(key_cnt==0) break;
						nKey = key_cnt-1; sRecno = pgR[nKey]; found = true;
						}
					else break;
					}
				else
					{
					nKey--; sRecno = pgR[nKey]; found = true;
					}
				
				}
			else		// skip forward
				{
				if(nKey>=key_cnt-1)
					{
					if(right_page>0)
						{
						for(;;)
							{
							nNode = right_page;
							goNode();
							readPage();
							if(key_cnt>0 || right_page<0) break;
							}
						if(key_cnt==0) break;
						nKey = 0; sRecno = pgR[nKey]; found = true;
						}
					else break;
					}
				else
					{
					nKey++; sRecno = pgR[nKey]; found = true;
					}
				
				}
			}
	}
	
	// sets the new index pointer for modified data record
	// searchKey should contain new index key value
	public void replace_key()
	{
		if( isKeyCurrent() ) remove_key();		// simply, without optimal space saving algorithms
		if(searchKey!=null) append_key();
	}
	
	private boolean isKeyCurrent()
	{
		return ( nNode>0 && sRecno>0 && nKey>=0 && sRecno==recno && pgR[nKey] == recno);
	}
	
	private void remove_key()
	{
		goNode();
		for(int i=nKey+1; i<=key_cnt; i++)
			{
			pgC[i-1] = pgC[i].clone();
			pgR[i-1] = pgR[i];	
			}
		key_cnt--;
		writePage();
		
		if(nNode==top && key_cnt==0)		// top node should not be empty
		{
			for(int w=-1;w!=0 && key_cnt==0;)
			{
				if(w<0)
					{
					if(left_page>0) nNode = left_page;
					else w=1;
					}
				if(w>0)
					{
					if(right_page>0) nNode = right_page;
					else w=0;
					}
				goNode();
				readPage();
			}
			if(key_cnt>0)
				{
				top = nNode;
				try { fidx.seek(0); } catch (IOException e) { e.printStackTrace(); }
				writeNumber(0,4,top,true);	// save this node as top node
				}
		}

	}

	private void append_key()
	{
		int i,j,kc,nk;
		long kNd;
		
		seek(false);		// find nearest

		// go to the right
		for(;(nKey<0 || nKey>=key_cnt) && right_page>0;)
			{
			nNode = right_page;
			goNode();
			readPage();
			nKey = key_cnt-1;
			}
		for(;(nKey>=0 && nKey<key_cnt);)
			{
			i=compare(nKey);						// compare keys
			if(i>0 || (i==0 && pgR[nKey]>sRecno ))
				{
				nKey--;
				if(nKey<0)
					{
					if(left_page>0)
						{
						nNode = left_page;
						goNode();
						readPage();
						nKey = key_cnt-1;
						}
					else break;		// no left page
					}
				}
			else break;		// this is the place to insert this key
			}
		nk = nKey;
		
		if(attrib>1)		// this is always LEAF
		{
			// if should divide by split this page
			if(((key_len+8)*(key_cnt+1))>=480)
				{							
				kc = key_cnt>>1;
				
				kNd = nNode;	//current node
				
				// When modifying data, index sometimes result to 0-data page that should be removed.
				// Java makes simply key count to 0, that is incorrect for FoxPro.
				// But, proper index for fast searching should be a binary tree with index pages inside.
				// Set to true when deciding not to SEEK data in FoxPro later.
				boolean INDEX_PAGES_INSIDE_IDX = false;
				
				if(INDEX_PAGES_INSIDE_IDX)
				{
					// This is better for binary search, but Fox sometimes can't find
					
				long nwR = fileend;
				long nwL = fileend+512;

				// adjust pointers to pages
				
				if(right_page>0)		// right	
				{
					nNode = right_page; goNode();
					try { fidx.skipBytes(4); } catch (IOException e) { e.printStackTrace(); }
					writeNumber(0,4,nwR,true);	// pointer to left node or -1
					nNode = kNd; goNode();
				}
				
				if(left_page>0)			// left
				{
					nNode = left_page; goNode();
					try { fidx.skipBytes(8); } catch (IOException e) { e.printStackTrace(); }
					writeNumber(0,4,nwL,true);	// pointer to right node or -1
					nNode = kNd; goNode();
				}
				
				// Create a new page for right side above middle key
				
				for(i=0; i<key_cnt; i++)
					{
					j = (i<=kc ? i+kc : key_cnt);
					pgC[i] = pgC[j].clone();
					pgR[i] = pgR[j];	
					}
				key_cnt -= kc;
				left_page = nwL;		// pointer to new left part
				nNode = nwR; goNode();
				writePage();		// write right half
				
				// Create a new page for left side below middle key
				
				nNode = kNd;
				goNode();
				readPage();
				for(i=kc, j=key_cnt; i<key_cnt; i++)
					{
					pgC[i] = pgC[j].clone();
					pgR[i] = pgR[j];	
					}
				key_cnt = kc;
				right_page = nwR;		// pointer to new right part
				nNode = nwL;
				goNode(); writePage();		// write left half
				
				fileend+=1024;		// added 2 new pages
				try { fidx.seek(8); } catch (IOException e) { e.printStackTrace(); }
				writeNumber(0,4,fileend, true);
				
				// Make current page to index

				nNode = kNd;
				goNode();
				readPage();
				for(i=0; i<key_cnt; i++)
					{
					j = (i==0 ? kc-1 : (i==1 ? key_cnt-1 : key_cnt));
					pgC[i] = pgC[j].clone();
					pgR[i] = (i==0 ? nwL : (i==1 ? nwR : pgR[j]));	
					}
				key_cnt = 2;
				right_page = -1;
				left_page = -1;
				attrib = (top==nNode ? 0 : 1);
				goNode();
				writePage();		// write left half
				
				nNode = (kc<=nk ? nwR : nwL);
				goNode(); readPage();
				nKey = (kc<=nk ? nk-kc : nk);
				
				}
				else	//INDEX_PAGES_INSIDE_IDX == false
				{
						// Don't use index pages at all
				if(right_page>0)
				{
					nNode = right_page; goNode();
					try { fidx.skipBytes(4); } catch (IOException e) { e.printStackTrace(); }
					writeNumber(0,4,fileend,true);	// left node or -1
					nNode = kNd; goNode();
				}
				
				for(i=0; i<key_cnt; i++)
					{
					j = (i<=kc ? i+kc : key_cnt);
					pgC[i] = pgC[j].clone();
					pgR[i] = pgR[j];	
					}
				key_cnt -= kc;
				nNode = fileend;
				goNode();
				
				left_page = kNd;
				writePage();		// write right half
				
				nNode = kNd;
				goNode();
				readPage();
				for(i=kc, j = key_cnt; i<key_cnt; i++)
					{
					pgC[i] = pgC[j].clone();
					pgR[i] = pgR[j];	
					}
				key_cnt = kc;
				right_page = fileend;
				goNode();
				writePage();		// write left half
				
				nNode = (kc<=nk ? fileend : kNd);
				goNode(); readPage();
				nKey = (kc<=nk ? nk-kc : nk);
				
				fileend+=512;
				try { fidx.seek(8); } catch (IOException e) { e.printStackTrace(); }
				writeNumber(0,4,fileend, true);
				
				}

				}
		
			for(i=key_cnt-1; i>nKey; i--)	// insert one item
				{
				pgC[i+1] = pgC[i].clone();
				pgR[i+1] = pgR[i];	
				}
			if(nKey<key_cnt) nKey++;
			else if(nKey>key_cnt) nKey--;
			
			key_cnt++;
			pgC[nKey] = searchKey.clone();
			pgR[nKey] = recno;				// new record pointer
			sRecno = recno;
			goNode();
			writePage();
		}
		
	}
	
	public void writeHeaderToEmptyFile( int keyLength )
	{
		top = 512;
		free = -1;
		fileend = 1024;
		key_len = keyLength;
		flags = 0;
		UNIQUE = false;
		FOR = false;
		signature = 1;
		
		writeNumber(0,4,top, true);
		writeNumber(0,4,free, true);
		writeNumber(0,4,fileend, false);
		writeNumber(0,2,key_len,false);
		writeNumber(0,1,flags,false);
		writeNumber(0,1,signature,false);
		writeChars(220,keyString);
		writeChars(220,forString);
		
		writeChars(56,"");		
		
		attrib = 3;
		key_cnt = 0;
		left_page = -1;
		right_page = -1;
		
		writeNumber(0,2,attrib,false);
		writeNumber(0,2,key_cnt,false);
		writeNumber(0,4,left_page,true);
		writeNumber(0,4,right_page,true);

		writeChars(500,"");

	}
	
}
