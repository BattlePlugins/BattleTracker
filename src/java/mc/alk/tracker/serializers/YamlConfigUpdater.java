package mc.alk.tracker.serializers;

import mc.alk.battlepluginupdater.FileUpdater;
import mc.euro.version.Version;
import mc.alk.tracker.Tracker;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class YamlConfigUpdater {


	public void update(YamlConfiguration config, File configFile) {
		File backupDir = new File(Tracker.getSelf().getDataFolder()+"/backups");
		if (!backupDir.exists()) backupDir.mkdir();

		Version curVersion = new Version(config.getString("version", "0"));
		Version newVersion = new Version("1.0");
		try{
			if (curVersion.compareTo(newVersion) < 0){
				curVersion = updateTo1Point0(configFile,backupDir, curVersion, newVersion);}
			newVersion = new Version("1.0.1");
			if (curVersion.compareTo(newVersion) < 0){
				curVersion = updateTo1Point01(configFile,backupDir, curVersion, newVersion);}
            newVersion = new Version("1.0.2");
            if (curVersion.compareTo(newVersion) < 0){
                curVersion = updateTo1Point02(configFile,backupDir, curVersion, newVersion);}
            newVersion = new Version("1.0.3");
            if (curVersion.compareTo(newVersion) < 0){
                curVersion = updateTo1Point03(configFile,backupDir, curVersion, newVersion);}
            newVersion = new Version("1.0.4");
            if (curVersion.compareTo(newVersion) < 0){
                curVersion = updateTo1Point04(configFile,backupDir, curVersion, newVersion);}
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	private Version updateTo1Point0(File oldFile, File backupDir, Version newVersion, Version oldVersion) throws IOException {
		FileUpdater fu = new FileUpdater(oldFile,backupDir,newVersion,oldVersion);
		fu.addBefore(".*showBukkitPVPMessages.*", "version: 1.0");
		fu.addAfter(".*trackPvE:.*", "",
				"### The default messages when top command is used",
				"topHeaderMsg: '&4Top &6{interfaceName}&4 {stat} TeamSize:{teamSize}'",
				"topBodyMsg: '&e#{rank}&4 {name} - {wins}:{losses}&6[{rating}]'",
				"",
				"### ignore the following entities when they kill or are killed",
				"ignoreEntities: []");
		return fu.update();
	}
	private Version updateTo1Point01(File oldFile, File backupDir, Version newVersion, Version oldVersion) throws IOException {
		FileUpdater fu = new FileUpdater(oldFile,backupDir,newVersion,oldVersion);
		fu.replace(".*version:.*", "version: 1.0.1");
		fu.addAfter(".*sendPVEDeathMessages:.*", "",
				"### If server wide PvP or Pve messages are disabled you can turn",
				"### these to true to allow the killer and the dead person",
				"### to still see the messages",
				"sendInvolvedPvPMessages: false",
				"sendInvolvedPvEMessages: false");
		fu.addAfter(".*ignoreEntities:.*", "",
				"### Should we even use leaderboard signs?",
				"allowLeaderboardSigns: true");
		return fu.update();
	}

	private Version updateTo1Point02(File oldFile, File backupDir, Version newVersion, Version oldVersion) throws IOException {
		FileUpdater fu = new FileUpdater(oldFile,backupDir,newVersion,oldVersion);
		fu.replace(".*version:.*", "version: 1.0.2");
		fu.addAfter(".*ignoreEntities:.*", "",
				"### ignore the following worlds",
				"ignoreWorlds: []");
		return fu.update();
	}
    private Version updateTo1Point03(File oldFile, File backupDir, Version newVersion, Version oldVersion) throws IOException {
        FileUpdater fu = new FileUpdater(oldFile,backupDir,newVersion,oldVersion);
        fu.replace(".*version:.*", "version: 1.0.3","");
        fu.addBefore(".*showBukkitPVPMessages:.*", "",
                "autoUpdate: true ## auto update to newer versions found on bukkit");
        return fu.update();
    }
    private Version updateTo1Point04(File oldFile, File backupDir, Version newVersion, Version oldVersion) throws IOException {
        FileUpdater fu = new FileUpdater(oldFile,backupDir,newVersion,oldVersion);
        fu.replace(".*version:.*", "version: 1.0.4");
        fu.replace("autoUpdate:.*",
                "# Updates will be retrieved from the latest plugin on the bukkit site. Valid Options : none, release, beta, all",
                "# none (don't auto update)",
                "# release (only get release versions, ignore beta and alpha)",
                "# beta (get release and beta versions, ignore alpha)",
                "# all (get all new updates)",
                "autoUpdate: release",
                "",
                "# show newer versions. Valid Options: none, console, ops",
                "# none (don't show new versions)",
                "# console (show only to console log on startup)",
                "# ops (announce to ops on join, will only show this message once per server start)",
                "announceUpdate: console");
        return fu.update();
    }
}
