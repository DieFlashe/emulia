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


	static private Object getDEBUG(String method) throws Exception {
		return Class.forName(Helper.class.getPackage().getName() + ".DEBUG").getMethod(method).invoke(null);
	}
	static public String confFile(String confFile) {
		try {
			return (String) getDEBUG("confFile");
		} catch (Exception e) {}
		return confFile;
	}
	static public Boolean writeLogs() {
		try {
			return (Boolean) getDEBUG("writeLogs");
		} catch (Exception e) {}
		return true;
	}
	static public Boolean writeBins() {
		try {
			return (Boolean) getDEBUG("writeBins");
		} catch (Exception e) {}
		return true;
	}


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
		if (!Helper.writeLogs()) // DEBUG
			return;
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
		"annex",                 // 425
		"autoload",              // 385
		"AutoMDIX",              // 431
		"bluetooth",             // 388
		"bluetooth_key",         // 428
		"bootloaderVersion",     // 386
		"bootserport",           // 387
		"companion_kernel_args", // 462
		"country",               // 424
		"cpufrequency",          // 389
		"crash",                 // 417
		"DMC",                   // 259
		"dtbsuffix",             // 463
		"ethaddr",               // ???
		"firmware_info",         // 430
		"firmware_version",      // 422
		"firstfreeaddress",      // 390
		"flashsize",             // 391
		"gpon_serial",           // 457
		"HardwareFeatures",      // 459
		"http_key",              // ???
		"HWRevision",            // 256
		"HWSubRevision",         // 260
		"jffs2_size",            // 441
		"kernel_args",           // 416
		"kernel_args1",          // 415
		"kernel_args_tmp",       // ???
		"language",              // 423
		"linux_fs_start",        // 408
		"linux_fs_status",       // 461
		"linuxip",               // ???
		"maca",                  // 392
		"macb",                  // 393
		"macc",                  // ???
		"macd",                  // ???
		"macdsl",                // 395
		"macwlan",               // 394
		"macwlan1",              // ???
		"macwlan2",              // 406
		"macwlan3",              // 458
		"macwlan4",              // ???
		"memsize",               // 396
		"mesh_id",               // 464
		"modetty0",              // 397
		"modetty1",              // 398
		"modulation",            // ???
		"modulemem",             // 452
		"mtd0",                  // 432
		"mtd1",                  // 433
		"mtd2",                  // 434
		"mtd3",                  // 435
		"mtd4",                  // 436
		"mtd5",                  // 437
		"mtd6",                  // 438
		"mtd7",                  // 439
		"mtd8",                  // 442
		"mtd9",                  // 443
		"mtd10",                 // 444
		"mtd11",                 // 445
		"mtd12",                 // 446
		"mtd13",                 // 447
		"mtd14",                 // 454
		"mtd15",                 // 455
		"my_ipaddress",          // 399
		"nfs",                   // 411
		"nfsroot",               // 412
		"oam_lb_timeout",        // ???
		"plc_dak_nmk",           // 453
		"ProductID",             // 257
		"prompt",                // 400
		"provider",              // 451
		"ptest",                 // 426
		"req_fullrate_freq",     // 402
		"reserved",              // 401
		"SerialNumber",          // 258
		"SoftwareFeatures",      // 460
		"subsys_id",             // ???
		"sysfrequency",          // 403
		"systype",               // ???
		"tr069_passphrase",      // 449
		"tr069_serial",          // 448
		"urlader-version",       // 509
		"usb_board_mac",         // 404
		"usb_device_id",         // 418
		"usb_device_name",       // 420
		"usb_manufacturer_name", // 421
		"usb_revision_id",       // 419
		"usb_rndis_mac",         // 405
		"webgui_pass",           // 450
		"wlan_cal",              // 440
		"wlan_key",              // 427
		"wlan_ssid"              // 456
	);


}
