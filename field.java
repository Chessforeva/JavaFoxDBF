package com.foxdbf;

public class field {

	public String fname = "";			// field name
	public String value = "";			// current data from table as string
	
	/* to work with memo fields containing non-char information as binaries,
	 *   value should be array of byte elements (byte[]),
	 *   because String is too dependent on language settings 
	 */
	public byte[] binValue;				// field type C,M,G
	
	public char ftype = ' ';			// field type C,M,N,F,I,L,D,B,G,T,Y
	public int fsize = 0;				// field size
	public int fsize_dec = 0;			// dec.point size
	public long fpos = 0;				// position in record
	public boolean binflag = false;		// table contains for C,M
	
	public String stringValue() { return value; }	// returns String type value of data C,D,M
	public long longValue()							// returns Long type value of data N,I
	{
		String s = value.trim();
		return ( s.length()==0 || s.indexOf("*")>=0 ? 0 : Long.parseLong(s) );
	}
	public double doubleValue()						// returns Double type value of data N,F
	{
		String s = value.trim();
		return ( s.length()==0 || s.indexOf("*")>=0 ? 0 : Double.parseDouble(s) );
	}
	
	public long longValueIEEEforBfield()				// returns Long value from IEEE double
	{
		String s = value.trim();
		long r = 0;
		if(s.length()==0 || s.indexOf("*")>=0) {}
		else
			{
			double d = Double.parseDouble(s);
			r = Double.doubleToLongBits(d);
			}
		return r;
	}
	public boolean booleanValue() { return value.equals("T"); }		// returns logical type value of data L
	public byte[] Binaries() { return binValue; }
	
		// prepares field variables for replacing in table
	public void setByLong( long new_value ) { value = String.format(formatstr(false),new_value); padl(); }	
	public void setByDouble( double new_value ) { value = String.format(formatstr(true),new_value); padl(); }
	public void setByString( String new_value ) { value = new_value; padr(); }
	public void setByBoolean( boolean new_value ) { value = (new_value ? "T":"F"); padr(); }
	public void setBinaries( byte[] new_value ) { binValue = new_value.clone(); }
	
	public void setByLongIEEEforBfield( long new_value, int dec )	// sets IEEE double value
	{
		double d = Double.longBitsToDouble(new_value);
		value = String.format(formatstr(true),d); padl();
	}
	
	private String formatstr(boolean flo)
	{ return "%" + Long.toString(fsize) + (flo ? "." + Long.toString(fsize_dec) + "f" : "d" ); }
	
	private void addc(boolean left, String c)
	{
		int l = value.length();
		int n = fsize-l;
		if(n>0)
			{
			String s = new String(new char[n]).replace("\0", c);
			value = (left ? s+value : value+s);
			}
		else if(n<0) value=value.substring(-n);
	}
	public void padl()
	{
		if(("NFBY").indexOf(ftype)>=0)
			{
			value = value.replace(',', '.'); addc(true, " ");
			}
	}
		
	public void padr()
	{
		if (("MI").indexOf(ftype)>=0) {/*do nothing */}
		else if (("CLD").indexOf(ftype)>=0) addc(false," ");
		else addc(false,"\0");
	}
	

}
