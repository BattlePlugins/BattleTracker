package mc.alk.tracker.controllers;

import mc.alk.battlepluginupdater.PluginUpdater.AnnounceUpdateOption;
import mc.alk.battlepluginupdater.PluginUpdater.UpdateOption;
import mc.alk.tracker.Defaults;
import mc.alk.tracker.listeners.BTEntityListener;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
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
		Defaults.PVE_MESSAGES = config.getBoolean("sendPVEDeathMessages",Defaults.PVE_MESSAGES);
		Defaults.PVP_MESSAGES = config.getBoolean("sendPVPDeathMessages",Defaults.PVP_MESSAGES);
		Defaults.BUKKIT_PVE_MESSAGES = config.getBoolean("showBukkitPVEMessages",Defaults.BUKKIT_PVE_MESSAGES);
		Defaults.BUKKIT_PVP_MESSAGES = config.getBoolean("showBukkitPVPMessages",Defaults.BUKKIT_PVP_MESSAGES);
		Defaults.INVOLVED_PVE_MESSAGES = config.getBoolean("sendInvolvedPvEMessages", Defaults.INVOLVED_PVE_MESSAGES);
		Defaults.INVOLVED_PVP_MESSAGES= config.getBoolean("sendInvolvedPvPMessages",Defaults.INVOLVED_PVP_MESSAGES);
		Defaults.RADIUS 	= config.getInt("msgRadius", 0);
		Defaults.MSG_TOP_HEADER = config.getString("topHeaderMsg",Defaults.MSG_TOP_HEADER);
		Defaults.MSG_TOP_BODY = config.getString("topBodyMsg",Defaults.MSG_TOP_BODY);
		Defaults.USE_SIGNS = config.getBoolean("useSigns",Defaults.USE_SIGNS);
        UpdateOption o = UpdateOption.fromString(config.getString("autoUpdate", "none"));
        Defaults.AUTO_UPDATE =  o != null ? o : UpdateOption.NONE;
        AnnounceUpdateOption ao = AnnounceUpdateOption.fromString(config.getString("announceUpdate", "none"));
        Defaults.ANNOUNCE_UPDATE =  ao != null ? ao : AnnounceUpdateOption.NONE;

        BTEntityListener.setIgnoreEntities(config.getStringList("ignoreEntities"));
		BTEntityListener.setIgnoreWorlds(config.getStringList("ignoreWorlds"));
	}

	public static File getFile() {
		return f;
	}
	public static YamlConfiguration getConfig() {
		return config;
	}
}
