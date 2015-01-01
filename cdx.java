package com.foxdbf;

import java.io.*;

// used documentations:
// http://www.clicketyclick.dk/databases/xbase/format/cdx.html
// http://harbour-project.org

// TODO:
// I decided not to make CDX modifications part now
// because it is almost impossible to do it without losing data accuracy.
// I hate regular re-indexing as a solution to bad database engine in FoxPro.
// Of course, this code may be improved someday by somebody.

public class cdx {

	public RandomAccessFile fcdx;	// cdx file pointer
	
	// HEADER FOR FILE
	public long root_page;			// pointer to root-page
	public long free_page;			// pointer to free-page (-1 if none)
	public long file_version;		// version 
	public byte file_signature;		// file signature
	public int  key_len;			// 10 by default, as tag names
	
	
	// PAGE
	private int attrib;				// attribute of page: 0 - branch page, 1 - root page , 2 -leaf page
	private int key_cnt;			// count of keys in this page
	private long left_page;			// left page pointer or -1
	private long right_page;		// right page pointer or -1
	private int free;				// Free space available in page
	
	// masks
	private long mRN;					// record number mask (to AND)
	private int mDC;					// duplicate bytes count mask(to AND)
	private int mTC;					// trailing bytes count mask (to AND)	
	// counts of bits
	private int cRN;					// number of bits for record number
	private int cDC;					// number of bits for duplicate count
	private int cTC;					// number of bits for trailing count
	
	private int kBy;					// total number of bytes for recnn/dup/trail info

	// data buffers for encoded/decoded data of pages
	private byte[] bd;				// encoded when reading
	private byte[] kb;				// decoded
	
	// all particular indexes
	public idx[] Idx;				// all indexes inside of the file
	public String[] TagNames;		// all TAGs of indexes in this CDX file
	public int I;					// which tag is current (-1 if none)
	
	private byte[][] pgC;			// keys in page
	private long[] pgR;				// record numbers/nodes in page
	private long[] pgP;				// child pages
	
	private long nNode;				// current page pointer
	private int nKey;				// current key pointer
	
	public boolean found;			// found() on seek
	public long sRecno;				// last record pointer found
	public byte[] nFoundKey;		// this array is a key in index
	
	public long recno;				// current record in database set by upper level
	
	private boolean LEAF;
		
	private long readNumber ( int side, int l, boolean FF )
	{
		long r = 0, b = 1;
		int q=0;
		byte[] g = new byte[l];
		try { fcdx.read(g); }
		catch (IOException e) { e.printStackTrace(); }
		
		if(side>0 && l>1) b<<=((l-1)<<3);
		for(int i=0;i<l;i++)
			{
			q = g[i]; if(q<0) q+=0x100;
			if(q!=0xff) FF=false;
			r|= b*q;
			if(side>0) b>>=8; else b<<=8;
			}
		return (FF ? -1 : r );
	}
	
	private String readChars ( int ln )
	{
		byte[] b = new byte[ln];
		try { fcdx.read(b,0,ln); } catch (IOException e) { e.printStackTrace(); }
		String s = "";
		for(int i=0;i<ln;i++) s+=(char)b[i];
		return s;
	}
	
	private int read1byte()
	{
		int b = 0;
		try { b = fcdx.readUnsignedByte(); }
		catch (IOException e) { e.printStackTrace(); }
		return b;
	}
	
	public void SkipBytes( int l )		// skip bytes in file
	{
		try { fcdx.skipBytes(l); } catch (IOException e) { e.printStackTrace(); }
	}
	
	
	
	public void read_header()
	{
		prepArrs();
		I = -1;
		nNode = 0;			//read 0-th page
		goNode();
		read_IndexHdrPage();
		
		nNode = root_page;
		goNode();
		readPage();		// read data of indexes
	}
	
	// prepare buffer 300 keys 100char long (increase if need)
	private void prepArrs()
	{
		int key_cnt = 300;
		int key_Clen = 100;
		pgC = new byte[key_cnt][key_Clen];
		pgR = new long[key_cnt];
		pgP = new long[key_cnt];
		
		kb = new byte[1024<<3];	// reserve 8Kb for decoded data
	}
	
	public void read_IndexHdrPage()
	{	
		if(I<0)
		{
			//This page contains file main data
			root_page = readNumber(0,4,true);
			free_page = readNumber(0,4,true);
			file_version = readNumber(0,4,false);
			key_len = (int)readNumber(0,2,false);
			SkipBytes(1);
			file_signature = (byte) read1byte();
		}
		else
		{	
			idx o = Idx[I];
			//This page contains index expression for a tag, other parameters
			o.top = readNumber(0,4,true);
			o.free = readNumber(0,4,true);
			o.version = readNumber(0,4,false);
			o.key_len = (int)readNumber(0,2,false);
			o.flags = (byte)read1byte();
			o.UNIQUE = ((o.flags & 1 )>0);
			o.FOR = ((o.flags & 8 )>0);
			o.signature = (byte)read1byte();
			SkipBytes(486);		//now 502
			o.DESCENDING = ( readNumber(0,2,false)>0 );
			int lenExpr = (int) readNumber(0,2,false);
			int lenFOR = (int) readNumber(0,2,false);
			SkipBytes(2);
			int lenKey = (int) readNumber(0,2,false);;
			o.keyString = readChars(lenExpr-1);
			SkipBytes(1);
			o.forString = readChars(lenFOR-1);
			
			//  actually not used, so it removes warnings
			@SuppressWarnings("unused")
			int notUsedVars = cRN + free + lenKey;
			
		}
	
	}
	
	public void goNode()		// goes to the page
	{
		try { fcdx.seek(nNode); } catch (IOException e) { e.printStackTrace(); }
	}

	public void readPage()
	{
		attrib = (int)readNumber(0,2,false);		// 0 - branch page, 1 - root page , 2 -leaf page (3=2+1)
		LEAF = (attrib>1);
		key_cnt = (int)readNumber(0,2,false);
		left_page = readNumber(0,4,true);	// left node or -1
		right_page = readNumber(0,4,true);	// right node or -1
		
		if(!LEAF)
			{
			idx o = Idx[I];		// I should be >=0
			key_len = o.key_len;
			for(int i=0;i<key_cnt;i++)
				{
				// read keys
				try { fcdx.read( pgC[i], 0, key_len); }
				catch (IOException e) { e.printStackTrace(); }
				
				long R = readNumber(1,4,false);		// read record numbers
				R &= mRN;
				pgR[i] = R;
				pgP[i] = readNumber(1,4,false);		// read child page pointers
				}
			}
		else
			{
			free = (int)readNumber(0,2,false);
			mRN = readNumber(0,4,false);
			mDC = read1byte();
			mTC = read1byte();
			cRN = read1byte();
			cDC = read1byte();
			cTC = read1byte();
			kBy = read1byte();
			
			int L = 488;	// constant
			byte trailC = ( I>=0 && Idx[I].sType=='C' ? (byte)' ' : 0);
			
			// DECODE encoded data of keys

			int bI = ( 16 - cTC - cDC );
			int u = key_len+6;
			int dst = 0, src = L;
			int d,m,l,j,i,z = 0;
			
			bd = new byte[L];
			try { fcdx.read(bd); }
			catch (IOException e) { e.printStackTrace(); }

			for(i=0; i<key_cnt; i++,z+=kBy)
			{
				int e0 = bd[z+kBy-2]; if(e0<0) e0+=0x100;
				int e1 = bd[z+kBy-1]; if(e1<0) e1+=0x100;
				int E = ((e1<<8) | e0);
				int Q = E >> bI;
				int dup = (i == 0) ? 0 : ( Q & mDC );	// duplicated
				int trl = ( Q >> cDC ) & mTC;	// trail
				int nwb = key_len - dup - trl;	// new bytes
				
				d = dst;
				if(dup>0)
				{
					for(j=0; j<dup; j++) kb[dst] = kb[(dst++)-u];
				}
				if(nwb>0)
				{
					src-=nwb;
					for(j=0; j<nwb; j++) kb[(dst++)] = bd[src+j];
				}
				if(trl>0)
				{
					for(j=0; j<trl; j++) kb[(dst++)] = trailC;
				}
				
				m = dst-d;
				for(j=0; j<m; j++) pgC[i][j] = kb[d+j];
				
				int k0 = bd[z+0]; if(k0<0) k0+=0x100;
				int k1 = bd[z+1]; if(k1<0) k1+=0x100;
				int k2 = bd[z+2]; if(k2<0) k2+=0x100;
				int k3 = bd[z+3]; if(k3<0) k3+=0x100;
				
				long r = (k3<<24) | (k2<<16) | (k1<<8) | k0;
				r &= mRN;
				pgR[i] = r;
									
				for(l=4;l>0;l--,r>>=8) kb[(dst++)] = (byte)(r & 0xff);
				kb[dst++] = (byte)dup;
				kb[dst++] = (byte)trl;
				
			}
			
			if(nNode == root_page)
				{
				//This is page of roots for all indexes
				Idx = new idx[key_cnt];
				TagNames = new String[key_cnt];

				for(i=0,z = 0; i<key_cnt; i++, z+=u)
					{
					String s="";
					for(j=0; j<key_len; j++)
						{
						byte c = kb[z+j];
						if(c!=0) s+=(char)c;
						}
					TagNames[i] = s;
					Idx[i] = new idx();
					I = i;
					nNode = pgR[i];
					goNode();
					read_IndexHdrPage();
					}
				I = -1;
				}
			}

	}
	
	public void setOrdByTag( String tag )
	{
		I = -1;
		for(int i=0; i<TagNames.length; i++)
			if(tag.toUpperCase().equals(TagNames[i])) I=i;
		nNode = 0;
		nKey = 0;
		sRecno = 0;
		found = false;
	}
	
	private boolean isKeyCurrent()
	{
		return ( nNode>0 && sRecno>0 && nKey>=0 && sRecno==recno && pgR[nKey] == recno);
	}
	
	private void updidx()		// update values in index object after seekings
	{
		idx o = Idx[I];
		o.found = found;
		o.sRecno = sRecno;
		o.nFoundKey = nFoundKey;
	}
	
	private int compare( idx o, int i )		// this compares keys 
	{
		int r=0;
		for(int n=0; r==0 && n<o.searchKey.length; n++ )
			{
			int a = pgC[i][n], b = o.searchKey[n];
			if(a<0) a+=0x100; if(b<0) b+=0x100;		// to unsigned
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
		
		nNode = Idx[I].top; 
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
				if(LEAF) 
					{
					sRecno = pgR[nKey];				// last processed record
					}
				else nNode = pgP[nKey];						// next child page pointer
				
				i = compare(Idx[I], nKey);			// compare keys
				
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
		updidx();
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
		updidx();
	}
	
	// goes top in index
	public void goTop()
	{
		nNode = Idx[I].top; 
		sRecno = 0;
		found = false;
		nKey = 0;
			
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
				else nNode = pgP[nKey];	// go deeper in child node
				}
			}
		updidx();		
	}

	public void goBottom()
	{
		nNode = Idx[I].top;
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
				else nNode = pgP[nKey];	// go deeper in child node
				}
			}
		updidx();
	}
	
	// skips n-records in index
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
		updidx();
	}
}
