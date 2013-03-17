package mc.alk.tracker.controllers;

import java.io.File;

import mc.alk.tracker.Defaults;
import mc.alk.tracker.listeners.BTEntityListener;

import org.bukkit.configuration.file.YamlConfiguration;
/**
 *
 * @author alkarin
 *
 */
public class ConfigController {
	static YamlConfiguration config = new YamlConfiguration();
	static File f = null;

	public static boolean getBoolean(String node) {return config.getBoolean(node, false);}
	public static boolean getBoolean(String node,boolean b) {return config.getBoolean(node, b);}
	public static  String getString(String node) {return config.getString(node,null);}
	public static  String getString(String node,String def) {return config.getString(node,def);}
	public static int getInt(String node,int i) {return config.getInt(node, i);}
	public static double getDouble(String node, double d) {return config.getDouble(node, d);}

	public static void setConfig(File f){
		ConfigController.f = f;
		loadAll();
	}

	public static void loadAll(){
		try {config.load(f);} catch (Exception e){e.printStackTrace();}
		Defaults.RAMPAGE_TIME = config.getInt("rampageTime", 7);
		Defaults.STREAK_EVERY = config.getInt("streakMessagesEvery", 15);
		Defaults.DISABLE_PVE_MESSAGES = !config.getBoolean("sendPVEDeathMessages");
		Defaults.DISABLE_PVP_MESSAGES= !config.getBoolean("sendPVPDeathMessages");
		Defaults.RADIUS 	= config.getInt("msgRadius", 0);
		Defaults.MSG_TOP_HEADER = config.getString("topHeaderMsg",Defaults.MSG_TOP_HEADER);
		Defaults.MSG_TOP_BODY = config.getString("topBodyMsg",Defaults.MSG_TOP_BODY);
		BTEntityListener.setIgnoreEntities(config.getStringList("ignoreEntities"));
	}
	public static File getFile() {
		return f;
	}
	public static YamlConfiguration getConfig() {
		return config;
	}
}
