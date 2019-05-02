package mc.alk.tracker.serializers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mc.alk.tracker.controllers.SignController;
import mc.alk.tracker.objects.StatSign;
import mc.alk.v1r7.serializers.BaseConfig;
import mc.alk.v1r7.util.SerializerUtil;

import mc.euro.bukkitadapter.MaterialAdapter;
import org.bukkit.Location;
import org.bukkit.Material;

public class SignSerializer extends BaseConfig{
	SignController sc;

	public SignSerializer(SignController sc) {
		this.sc = sc;
	}


	public void saveAll() {
		Map<String,StatSign> map = sc.getTopSigns();
		if (map != null){
			List<StatSign> l = new ArrayList<StatSign>(map.values());
			config.set("topSigns", l);
		}
		map = sc.getPersonalSigns();
		if (map != null){
			List<StatSign> l = new ArrayList<StatSign>(map.values());
			config.set("personalSigns", l);
		}

		save();
	}

	public void loadAll(){
		String[] types = new String[]{"topSigns","personalSigns"};
		sc.clearSigns();
		for (String type: types){
			List<?> signs = config.getList(type);
			if (signs == null)
				continue;
			for (Object o : signs){
				if (o == null || !(o instanceof StatSign))
					continue;
				if (!stillSign((StatSign)o))
					continue;
				sc.addSign((StatSign) o);
			}
		}
	}


	public static boolean stillSign(StatSign o) {
		String l = o.getLocationString();
		if (l == null)
			return false;
		try{
			Location loc = SerializerUtil.getLocation(l);
			if (loc == null)
				return false;
			Material mat = loc.getWorld().getBlockAt(loc).getType();
			if ( mat != Material.SIGN && mat != MaterialAdapter.getMaterial("SIGN_POST") && mat != Material.WALL_SIGN)
				return false;
		} catch( Exception e){
			return false;
		}
		return true;
	}
}
