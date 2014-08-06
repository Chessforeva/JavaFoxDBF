package com.foxdbf;

public class datestr {

	public static long toJulian(String DateYYYYMMDD )		// String of date to Julian number
    {
    int d = Integer.valueOf( DateYYYYMMDD.substring(6,8) );
    int m = Integer.valueOf( DateYYYYMMDD.substring(4,6) );
    int y = Integer.valueOf( DateYYYYMMDD.substring(0,4) );

    long Jd = ( 1461 * ( y + 4800 + ( m - 14 ) / 12 ) ) / 4 +
     ( 367 * ( m - 2 - 12 * ( ( m - 14 ) / 12 ) ) ) / 12 -
     ( 3 * ( ( y + 4900 + ( m - 14 ) / 12 ) / 100 ) ) / 4 +
     d - 32075;
    	    
    return Jd;
    }

	public static String fromJulian( long Jd )		// Julian date to string
	{
	long l = Jd + 68569;
	long n = ( 4 * l ) / 146097;
	l = l - ( 146097 * n + 3 ) / 4;
	long i = ( 4000 * ( l + 1 ) ) / 1461001;
	l = l - ( 1461 * i ) / 4 + 31;
	long j = ( 80 * l ) / 2447;
	int d = (int) (l - ( 2447 * j ) / 80);
	l = j / 11;
	int m = (int) (j + 2 - ( 12 * l ));
	int y = (int) (100 * ( n - 49 ) + i + l);
	return String.valueOf(y) +
			(m<10 ? "0" : "") + String.valueOf(m) +
			(d<10 ? "0" : "") + String.valueOf(d);
	}
	
	public static String stringFromDateTime( long dt, long ms )
	{
		String rs = "";
		if( dt>0 || ms>0 )
		{
		String ds = fromJulian(dt);
		rs += ds.substring(6,8) + "." + ds.substring(4,6) + "." + ds.substring(0,4);
		int h = (int) ((ms / 3600000) % 24);
		int m = (int) ((ms / 60000) % 60);
		int s = (int) ((ms / 1000) % 60);
		rs += " " + (h<10 ? "0" : "") + String.valueOf(h) + ":" + 
					(m<10 ? "0" : "") + String.valueOf(m) + ":" + 
					(s<10 ? "0" : "") + String.valueOf(s);
		}
		return rs;
	}
	
	public static long dateFromDateTime( String ds )
	{ return toJulian( ds.substring(6,10) + ds.substring(3,5) + ds.substring(0,2) ); }
	
	public static long timeFromDateTime( String ds )
	{
		int h = Integer.parseInt( ds.substring(11,13) );
		int m = Integer.parseInt( ds.substring(14,16) );
		int s = Integer.parseInt( ds.substring(17,19) );
		return (3600000 * h) + (60000 * m) + (1000 * s) +1;
	}
}