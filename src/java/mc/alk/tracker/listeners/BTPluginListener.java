package mc.alk.tracker.listeners;

import mc.alk.tracker.Tracker;
import mc.alk.tracker.controllers.MessageController;

import mc.alk.tracker.plugins.BTPlaceholderExtension;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import com.dthielke.herochat.Herochat;

/**
 * 
 * @author alkarin
 *
 */
public class BTPluginListener implements Listener {

	@EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
		loadPlugins();
    }
	public static void loadPlugins(){
        ///HeroChat
		Herochat hc = MessageController.getHeroChat();
        if (hc == null) {
        	Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("HeroChat");
        	
            hc = ((Herochat) plugin);
            if (hc != null){
            	MessageController.setHeroChat(hc);
                PluginDescriptionFile pDesc = plugin.getDescription();
                System.out.println("[" + Tracker.getSelf().getVersion() + "] loaded " + pDesc.getName() +
                		" version " + pDesc.getVersion());            	
            }
        }

        // PlaceholderAPI
        if (Bukkit.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BTPlaceholderExtension().register();
        }
	}
}
