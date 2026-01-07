package boxenluther.emulia;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

public class Device {

	final private String tag = "ENV";
	private void doLog(String txt) {
		if (doLog)
			Helper.doLog(tag, txt);
	}

	private Boolean doLog = false;
	public Device() {
		this(false);
	}
	public Device(Boolean doLog) {
		super();
		this.doLog = doLog;
		readEnv(Helper.getConfigFile());
	}

	private String minLen(String arg, int len) {
		arg = arg + " ";
		while (arg.length() < len)
			arg = arg + " ";
		return arg;
	}
	private String minLen22(String arg) {
		return minLen(arg,22);
	}

	private LinkedList<String> listEnv = new LinkedList<String>();
	private LinkedHashMap<String, String> mapEnv = new LinkedHashMap<>();
	private void readEnv(String fileName) {
		File f = new File(fileName);
		if (!f.canRead()) {
			doLog("XX Can not read environement from file " + f.getAbsolutePath());
			System.exit(1);
			return;
		}
		else
			doLog("XX " + minLen22("Environement-file") + f.getAbsolutePath().substring(f.getAbsolutePath().lastIndexOf("Conf" + File.separator)));

		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
			String line = null;
			int i = -1;
			int j = -1;
			String key;
			String val;
			while ((line = br.readLine()) != null) {
//				System.out.println(line);
				// comments
				i = line.indexOf("# ");
				if (i != -1)
					line = line.substring(0, i);
				i = line.indexOf("//");
				if (i != -1)
					line = line.substring(0, i);
				line = line.trim();
				// empty
				if (line.isEmpty())
					continue;
				// split
				i = line.indexOf('\t');
				j = line.indexOf(' ');
				if (i == -1)
					i=j;
				if (i != -1 && j != -1) {
					if (i > j)
						i = j;
				}
				if (i == -1) {
					key = line;
					val = "";
				} else {
					key = line.substring(0, i);
					val = line.substring(i);
				}
				key=key.replace(":","").trim();
				val=val.replace(":*", ":12:34:56").replace("*", "123456").trim();


				//TODO dynamic default device-config: HW -> autogen -> discarded ^^
				
				// vars
//				val=val;

				// vars 3020
//				val=val
//						.replace("%dHWR%", "60")
//						;

				// vars 7539
//				val=val
//						.replace("%dHWR%", "256")
//						;

				// vars 7590
//				val=val
//						.replace("%dHWR%", "205")
//						;

				// vars 7390
//				val=val
//						.replace("%dHWR%", "156")
//						;

//				val=val
//						.replace("%dBranding%", "avm")
//						;

				
//				System.out.println(key +" -- "+ val);
				mapEnv.put(key, val);
			}
			
			// urlader
			final String dLader = dLader();
			if (dLader!=null)
				mapEnv.put("urlader-version", dLader);
			else
				mapEnv.remove("urlader-version");

			// emulia
			mapEnv.put("emulia__emulator", "true");

			// known
			listEnv.addAll(mapEnv.keySet());
			
			// output
//			doLog("-- Loaded " + mapEnv.size() + " environement items");
			doLog("## " + minLen22("FTP-Identification") + dEva());
			for (String k: getEnv()) {
				if (k.startsWith("debagger__user "))
					continue;
				if (k.startsWith("emulia__emulator "))
					continue;
				doLog("oo " + k);
			}
			
//			for (Map.Entry<String, String> e : mapEnv.entrySet())
//				doLog("oo " + minLen22(e.getKey()) +"'"+ e.getValue() + "'");
//			String v;
//			for (String k: listEnv) {
//				v=mapEnv.get(k);
//				if (v!=null)
//					doLog("oo " + minLen22(k) +"'"+ mapEnv.get(k) + "'");
//			}
//			for (String item : listEnv)
//				doLog("~~ " + item);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			br.close();
		} catch (Exception e) {
		}
	}
	public Boolean hadEnvVar(String key) {
		return ( listEnv.contains(key) || Helper.allEnvVars.contains(key) );
	}
	public Boolean hasEnvVar(String key) {
		return mapEnv.containsKey(key);
	}
	public String getEnvVal(String key) {
		return mapEnv.get(key);
	}
	public String getEnvVar(String key) {
		final String val = mapEnv.get(key);
		if (val != null)
			return minLen22(key) + val;
		return null;
	}
	public void setEnvVar(String key, String val) {
		if (key.equals("debagger__user"))
			return;
		if (key.equals("emulia__emulator"))
			return;
		if (!listEnv.contains(key))
			if (Helper.allEnvVars.contains(key))
				listEnv.add(key);
		mapEnv.put(key, val);
	}
	public void delEnvVar(String key) {
		if (key.equals("debagger__user"))
			return;
		if (key.equals("emulia__emulator"))
			return;
		mapEnv.remove(key);
	}
	private void addEnv(List<String> content, String key) {
		final String val=mapEnv.get(key);
		if (val!=null)
			content.add(minLen22(key) + mapEnv.get(key));
	
	}
	public List<String> getEnv() {
		List<String> content = new ArrayList<String>();

		// ontop
		List<String> sorting = new ArrayList<String>();
		sorting.add("debagger__user");
		sorting.add("emulia__emulator");
		sorting.add("DMC");
		sorting.add("HardwareFeatures");
		sorting.add("HWRevision");
		sorting.add("HWSubRevision");
		sorting.add("ProductID");
		sorting.add("SerialNumber");

		List<String> sortedEnv = new ArrayList<String>(listEnv);
		java.util.Collections.sort(sortedEnv);
		
		// head
		for (String key: sorting) {
			if (listEnv.contains(key))
				addEnv(content,key);
		}

		//body
		for (String key: sortedEnv) {
			if (key.startsWith("ftp__"))
				continue;
			if (key.startsWith("counter__"))
				continue;
			if (!sorting.contains(key))
				addEnv(content,key);
		}
		
//		for (Map.Entry<String, String> e : mapEnv.entrySet())
//		content.add(minLen22(e.getKey()) + e.getValue());

		return content;
	}
	public List<String> getCount() { 
		String reboot_major = "9";
		String reboot_minor = "9";
		String run_hours = "1";
		String run_days = "2";
		String run_mounths = "3";
		String run_years = "4";

		// from DieFlashe
		if (mapEnv.containsKey("counter__reboot_major"))
			reboot_major = mapEnv.get("counter__reboot_major");
		if (mapEnv.containsKey("counter__reboot_minor"))
			reboot_minor = mapEnv.get("counter__reboot_minor");
		if (mapEnv.containsKey("counter__run_hours"))
			run_hours = mapEnv.get("counter__run_hours");
		if (mapEnv.containsKey("counter__run_days"))
			run_days = mapEnv.get("counter__run_days");
		if (mapEnv.containsKey("counter__run_mounths"))
			run_mounths = mapEnv.get("counter__run_mounths");
		if (mapEnv.containsKey("counter__run_years"))
			run_years = mapEnv.get("counter__run_years");

		List<String> content = new ArrayList<String>();
		content.add(minLen22("reboot_major") + reboot_major);
		content.add(minLen22("reboot_minor") + reboot_minor);
		content.add(minLen22("run_hours") + run_hours);
		content.add(minLen22("run_days") + run_days);
		content.add(minLen22("run_mounths") + run_mounths);
		content.add(minLen22("run_years") + run_years);
		return content;
	}
	public String dAdam() {

		// from DieFlashe
		if (mapEnv.containsKey("ftp__banner"))
			return mapEnv.get("ftp__banner");

		return "ADAM2 FTP Server ready";
	}
	public String dEva() {
	
		// from DieFlashe
		if (mapEnv.containsKey("ftp__system"))
			return mapEnv.get("ftp__system");
		
		// read from env
		String bootloader = "1.234";
		if (mapEnv.containsKey("bootloaderVersion"))
			bootloader = mapEnv.get("bootloaderVersion");
		
		// svn commit
		int bootSvn = 234;
		try {
			bootSvn = Integer.parseInt(bootloader.substring(2));
		} catch (Exception e) {
			e.printStackTrace();
		}

		// git commit
		String bootGit = "0x1234";
		if (bootSvn > 2900)
			bootGit = "0x51000";
		if (bootSvn > 11700)
			bootGit = "0x54000";

		// avm special
//		if (bootSvn < 1000)
//			bootloader=bootloader.replace("1.", "1.1");

		return "AVM EVA Version " + bootloader + " 0x0 " + bootGit;
	}
	private String dLader() {
		int bootSvn = 234;
		try {
			bootSvn = Integer.parseInt(mapEnv.get("bootloaderVersion").substring(2));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		if (bootSvn<1000)
			return "1" + bootSvn; 
		if (bootSvn<2000)
			return "2" + (bootSvn-1000); 
		return null;
	}


}
