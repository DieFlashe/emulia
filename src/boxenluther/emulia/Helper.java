package boxenluther.emulia;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public final class Helper {

	private Helper() {}

	
	static private String workDir = null;
	static public String getWorkDir() {
		if (workDir == null)
			workDir = new File("").getAbsoluteFile().toString();
		return workDir;
	}


	static private String logDate = null;
	static public String getLogDate() {
		if (logDate == null)
			logDate = Long.toString(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() / 1000L);
		return logDate;
	}

	static private String logFile = null;
	static public String getLogFile() {
		if (logFile == null)
			logFile = getWorkDir() + File.separator + "Logs" + File.separator + getLogDate() + ".log";
		return logFile;
	}

	static private PrintWriter logWriter = null;
	static public void doWriteLog(String txt) {
		if (logWriter == null) {
			try {
				logWriter = new PrintWriter(new BufferedWriter(new FileWriter(getLogFile(), true)));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (logWriter != null) {
			logWriter.println(txt);
			logWriter.flush();
		}
	}

	static public void doLog() {
		doLog(null, "");
	}
	static public void doLog(String tag, String txt) {
		String now = "";
		if (txt.length() > 0)
			now = new SimpleDateFormat("HH:mm:ss.SSS").format(Calendar.getInstance().getTime()) + " ";
//			now = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss.SSS").format(Calendar.getInstance().getTime()) + " ";

		if (tag == null)
			tag = "";
		else
			tag = "[" + tag + "]" + " ";

		final String msg = now + tag + txt;
		System.out.println(msg);
		doWriteLog(msg);
	}


	static private String outPath = null;
	static public String getOutPath() {
		if (outPath == null)
			outPath = getWorkDir() + File.separator + "Bins" + File.separator;
		return outPath;
	}


	static private String cfgPath = null;
	static public String getCfgPath() {
		if (cfgPath == null)
			cfgPath = getWorkDir() + File.separator + "Conf";
		return cfgPath;
	}
	static private String npPath = null;
	static public String getNpPath() {
		if (npPath == null)
			npPath = getCfgPath() + File.separator + "np";
		return npPath;
	}

	static private String configFile = null;
	static public String getConfigFile() {
		if (configFile == null)
			return getCfgPath() + "Device.txt";
		return configFile;
	}
	static public void setConfigFile(String fileName) {
		configFile = getNpPath() + File.separator + fileName;
		if (!new File(configFile).exists())
			configFile = getCfgPath() + File.separator + fileName;
	}
	static public String chkConfigFile(String fileName) {
		String retFile = fileName;

		String tmpFile = getNpPath() + File.separator + retFile;
		if (!new File(tmpFile).exists())
			tmpFile = getCfgPath() + File.separator + retFile;
		if (!new File(tmpFile).exists())
			return retFile;
		File f = new File(tmpFile);
		if (!f.canRead())
			return retFile;

		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(tmpFile)));
			String line = null;
			while ((line = br.readLine()) != null) {
				if (line.length() <= 5)
					break;
				if (!line.toUpperCase().startsWith("LINK:"))
					break;
				retFile = line.substring(5);
				break;
			}
		} catch (Exception e) {
		}
		try {
			br.close();
		} catch (Exception e) {
		}

		return retFile;
	}


	static public byte[] string2bytes(String txt) {
		return txt.getBytes(StandardCharsets.UTF_8);
	}
	static public String bytes2string(byte[] data) throws CharacterCodingException {
		return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
	}


	static public String beautifyIP(String ip, Integer port) {
		if (ip.indexOf('.') >= 0)
			return ip + ":" + port;
		else
			return "[" + beautifyIP(ip) + "]" + ":" + port;
	}
	static public String beautifyIP(String ip) {

		// ignore ipv4
		if (ip.indexOf('.') >= 0)
			return ip;

		// already compressed
		if (ip.contains("::"))
			return ip;

		// interface scope
		String scope = "";
		int p = ip.indexOf('%');
		if (p >= 0) {
			scope = ip.substring(p);
			ip = ip.substring(0, p);
		}

		String[] groups = ip.split(":", -1);

		// leading zeros
		for (int i = 0; i < groups.length; i++) {
			if (groups[i].isEmpty())
				continue;
			groups[i] = Integer.toHexString(Integer.parseInt(groups[i], 16));
		}

		// zero groups
		int bestStart = -1, bestLen = 0;
		for (int i = 0; i < groups.length;) {
			if (!groups[i].equals("0")) {
				i++;
				continue;
			}

			int start = i;
			while (i < groups.length && groups[i].equals("0"))
				i++;

			if (i - start > bestLen) {
				bestStart = start;
				bestLen = i - start;
			}
		}

		// double colon
		if (bestLen < 2)
			bestStart = -1;

		String result = "";
		for (int i = 0; i < groups.length; i++) {
			if (i == bestStart) {
				result += "::";
				i += bestLen - 1;
			} else {
				if (result.length() > 0 && result.charAt(result.length() - 1) != ':')
					result += ':';
				result += groups[i];
			}
		}

		return result + scope;
	}


	static public List<String> allEnvVars = Arrays.asList(
		"annex",
		"autoload",
		"AutoMDIX",
		"bluetooth",
		"bluetooth_key",
		"bootloaderVersion",
		"bootserport",
		"companion_kernel_args",
		"country",
		"cpufrequency",
		"crash",
		"DMC",
		"dtbsuffix",
		"ethaddr",
		"firmware_info",
		"firmware_version",
		"firstfreeaddress",
		"flashsize",
		"gpon_serial",
		"HardwareFeatures",
		"http_key",
		"HWRevision",
		"HWSubRevision",
		"jffs2_size",
		"kernel_args",
		"kernel_args1",
		"kernel_args_tmp",
		"language",
		"linux_fs_start",
		"linux_fs_status",
		"linuxip",
		"maca",
		"macb",
		"macc",
		"macd",
		"macdsl",
		"macwlan",
		"macwlan1",
		"macwlan2",
		"macwlan3",
		"macwlan4",
		"memsize",
		"mesh_id",
		"modetty0",
		"modetty1",
		"modulation",
		"modulemem",
		"mtd0",
		"mtd1",
		"mtd2",
		"mtd3",
		"mtd4",
		"mtd5",
		"mtd6",
		"mtd7",
		"mtd8",
		"mtd9",
		"mtd10",
		"mtd11",
		"mtd12",
		"mtd13",
		"mtd14",
		"mtd15",
		"my_ipaddress",
		"nfs",
		"nfsroot",
		"oam_lb_timeout",
		"plc_dak_nmk",
		"ProductID",
		"prompt",
		"provider",
		"ptest",
		"req_fullrate_freq",
		"reserved",
		"SerialNumber",
		"SoftwareFeatures",
		"subsys_id",
		"sysfrequency",
		"systype",
		"tr069_passphrase",
		"tr069_serial",
		"urlader-version",
		"usb_board_mac",
		"usb_device_id",
		"usb_device_name",
		"usb_manufacturer_name",
		"usb_revision_id",
		"usb_rndis_mac",
		"webgui_pass",
		"wlan_cal",
		"wlan_key",
		"wlan_ssid"
	);


}
