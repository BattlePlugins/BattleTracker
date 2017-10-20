package mc.alk.tracker.util;

import java.text.SimpleDateFormat;
import java.util.TimeZone;


public class TimeUtil {
	static final String version = "1.1";
	static long lastCheck = 0;
	
	public static String convertMillisToString(long t){
		return convertSecondsToString(t/1000);
	}
	private static String convertSecondsToString(long t){
		long s = t % 60;
		t /= 60;
		long m = t %60;
		t /=60;
		long h = t % 24;
		t /=24;
		long d = t;
		boolean has = false;
		StringBuilder sb = new StringBuilder();
		if (d > 0) {
			has=true;
			sb.append("&6"+d + "&e " + dayOrDays(d) +" ");}
		if (h > 0) {
			has =true;
			sb.append("&6"+h + "&e " + hourOrHours(h)+" ");}
		if (m > 0) {
			has=true;
			sb.append("&6"+m + "&e " + minOrMins(m)+" ");}
		if (s > 0) {
			has = true;
			sb.append("&6"+s + "&e " + secOrSecs(s)+" ");}
		if (!has){
			sb.append("&60 sec");
		}
		return sb.toString();
	}
	
	public static String convertToString(long t){
	    t = t / 1000;  
	    return convertSecondsToString(t);
	}
	
	private static String dayOrDays(long t){
		return t > 1 || t == 0? "days" : "day";
	}

	private static String hourOrHours(long t){
		return t > 1 || t ==0 ? "hours" : "hour";
	}

	private static String minOrMins(long t){
		return t > 1 || t == 0? "minutes" : "minute";
	}
	private static String secOrSecs(long t){
		return t > 1 || t == 0? "sec" : "secs";
	}

	
	public static String convertLongToDate(long time) {
		SimpleDateFormat sdf = new SimpleDateFormat("E hh:mm z");
		sdf.setTimeZone(TimeZone.getTimeZone("America/New_York"));
		return sdf.format(time);
	}

	public static String PorP(int size) {
		return size == 1 ? "person" : "people";
	}
	
}
