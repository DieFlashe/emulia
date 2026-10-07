package boxenluther.emulia;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;

public class Fastboot extends Thread {

	public volatile boolean ready = false;

	final private int udpFastbootPacketMax = 1452; // MTU1500=1452 - MTU1280=1232 - AVM=1024
	private final Map<String, Session> sessions = new HashMap<>();
	private final Map<Integer, byte[]> tffs = new HashMap<>();

	private final Device device;
	public Fastboot(Device device) {
		super();
		this.device = device;
	}

	static final private String tag = "FST";
	static private void doLog(String txt) {
		Helper.doLog(tag, txt);
	}
	private void doLog(Session session, String txt) {
		Helper.doLog("P" + session.remotePort, txt);
	}

	private enum SessionMODE {
		COMMAND, // fastboot and oem commands
		DOWNLOAD, // recovery -> device eg firmware upload
		UPLOAD, // device -> recovery eg environment download
	}
	private class Session {
		Device device;
		int sequence = 1;
		int packetSize = udpFastbootPacketMax;
		boolean initialized = false;
		long lastSeen = System.nanoTime();
		boolean expiring = false;
		long expireStarted;
		byte[] lastRequest;
		byte[] lastResponse;
		final Queue<byte[]> replies = new ArrayDeque<>();
		SessionMODE mode = SessionMODE.COMMAND;
		long downloadSize;
		long downloadReceived;
		File downloadFile;
		boolean downloadNamed;
		File downloadTarget;
		BufferedOutputStream downloadOut;
		final int remotePort;
		final String remoteIP;
		byte[] uploadData = new byte[0];
		int uploadOffset;
		byte[] selectedData = new byte[0];
		String selectedName = "empty";

		Session(Device device, String remoteIP, int remotePort) {
			this.device = device != null ? device : new Device();
			this.remoteIP = remoteIP;
			this.remotePort = remotePort;
		}

		void closeDownload() {
			try {
				if (downloadOut != null)
					downloadOut.close();
			} catch (Exception e) {}
			downloadOut = null;
			downloadTarget = null;
		}

	}

	private void sessionExpireReset(Session session) {
		session.expiring = false;
	}
	private void sessionExpireForce(Session session) {
		if (session.expiring)
			return;
		session.expireStarted = System.nanoTime();
		session.expiring = true;
	}

	private void result_RAW(Session session, String code, String text) {
		doLog(session, ">> " + code + text);
		session.replies.add(Helper.string2bytes(code + text));
	}
	private void resultDATA(Session session, long size) {
		result_RAW(session, "DATA", String.format("%08x", size));
	}
	private void resultFAIL(Session session, String txt) {
		result_RAW(session, "FAIL", txt);
	}
	private void resultOKAY(Session session, String txt) {
		result_RAW(session, "OKAY", txt);
	}
	private void resultOKAY(Session session) {
		resultOKAY(session, "");
	}

	private boolean envAllowed(String key) {
		if (key.equals("emulia__emulator"))
			return false;
		if (key.equals("debagger__user"))
			return false;
		if (key.startsWith("oem__"))
			return false;
		if (key.startsWith("ftp__"))
			return false;
		if (key.startsWith("counter__"))
			return false;
		return true;
	}
	private String envGet(Session session, String key) {
		synchronized (session.device) {
			return session.device.getEnvVal(key);
		}
	}
	private boolean envSet(Session session, String key, String val) {
		synchronized (session.device) {
			if (key.equals("debagger__user") || key.equals("emulia__emulator"))
				return false;
			if (!session.device.hadEnvVar(key))
				return false;

			if (val.isEmpty())
				session.device.delEnvVar(key);
			else
				session.device.setEnvVar(key, val);
			return true;
		}
	}
	private LinkedHashMap<String, String> envAll(Session session) {
		LinkedHashMap<String, String> ret = new LinkedHashMap<>();
		synchronized (session.device) {
			for (String line : session.device.getEnv()) {
				int i = 0;
				while (i < line.length() && !Character.isWhitespace(line.charAt(i)))
					i++;

				String key = line.substring(0, i);
				if (envAllowed(key))
					ret.put(key, session.device.getEnvVal(key));
			}
		}
		return ret;
	}
	private LinkedHashMap<String, String> envParse(byte[] data) throws IOException {
		LinkedHashMap<String, String> ret = new LinkedHashMap<>();
		for (String line : Helper.bytes2string(data).split("\n", -1)) {
			if (line.endsWith("\r"))
				line = line.substring(0, line.length() - 1);
			if (line.isEmpty())
				continue;

			int i = 0;
			while (i < line.length() && !Character.isWhitespace(line.charAt(i)))
				i++;
			if (i == 0)
				throw new IOException("Invalid environment key");

			String key = line.substring(0, i);
			while (i < line.length() && Character.isWhitespace(line.charAt(i)))
				i++;
			ret.put(key, line.substring(i));
		}
		return ret;
	}

	private long counterNumber(Session session, String key, long maximum) {
		String val = envGet(session, key);
		long number = Long.parseLong(val);
		if (number < 0 || number > maximum)
			throw new IllegalArgumentException("Counter out of range: " + key);
		return number;
	}
	private long counterNumber(Session session, String key) {
		return counterNumber(session, key, 0xffffffffL);
	}
	private boolean counterVersionNew(Session session, String key) {
		try { return counterNumber(session, key) >= 3; }
		catch (IllegalArgumentException e) { return false; }
	}
	private byte[] counterTffs(Session session, int id) {
		String key;
		int size = 4;
		long maximum = 0xffffffffL;
		boolean bitCounter = true;
		switch (id) {
			case 1030:
				return null;
			case 1031:
				key = "counter__reboot_ver";
				bitCounter = false;
				break;
			case 1024:
				if (counterVersionNew(session, "counter__reboot_ver")) {
					return null;
				} else {
					key = "counter__reboot_major";
					size = 8;
					maximum = 64;
				}
				break;
			case 1025:
				if (counterVersionNew(session, "counter__reboot_ver")) {
					key = "counter__reboot_cnt";
					bitCounter = false;
				} else {
					key = "counter__reboot_minor";
					maximum = 32;
				}
				break;
			case 1026:
				key = "counter__run_hours";
				maximum = 24;
				break;
			case 1027:
				key = "counter__run_days";
				maximum = 31;
				break;
			case 1028:
				key = "counter__run_mounths";
				maximum = 12;
				break;
			case 1029:
				key = "counter__run_years";
				maximum = 32;
				break;
			default:
				return null;
		}

		// never ever
		if (envGet(session, key) == null)
			return null;

		// Count of 0 bits from the least significant bit.
		// 0= all bits set to 1 - 1= ..10 - 2= ..100 - 3= ..1000 
		long number = counterNumber(session, key, maximum);
		long raw = bitCounter ? (number == size * 8 ? 0 : (-1L << (int) number)) : number;
		ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
		if (size == 8)
			buffer.putLong(raw);
		else
			buffer.putInt((int) raw);
		return buffer.array();
	}
	private void counterReset(Session session) {
		synchronized (session.device) {
			envSet(session, "counter__run_hours", "0");
			envSet(session, "counter__run_days", "0");
			envSet(session, "counter__run_mounths", "0");
			envSet(session, "counter__run_years", "0");

			if (counterVersionNew(session, "counter__reboot_ver")) {
				envSet(session, "counter__reboot_cnt", "0");
			} else {
				envSet(session, "counter__reboot_major", "0");
				envSet(session, "counter__reboot_minor", "0");
			}
		}
	}

	private void downloadStart(Session session, String arg) throws IOException {
		long size = Long.parseLong(arg, 16);
		if (size < 0) // max 4GB
			throw new IOException("Invalid download size");
		if (size > 384 *1024 *1024) // 384MB, max 4GB
			throw new IOException("Download too large");
		File dir = new File(Helper.getOutPath());
		if (!dir.isDirectory() && !dir.mkdirs())
			throw new IOException("Cannot create output directory");
		session.closeDownload();
		session.downloadFile = null;
		session.downloadNamed = false;
		session.downloadTarget = new File(dir, System.currentTimeMillis() + "-fastboot.bin");
		if (!session.downloadTarget.createNewFile())
			throw new IOException("Download filename already exists");
		session.downloadOut = new BufferedOutputStream(new FileOutputStream(session.downloadTarget));
		session.downloadSize = size;
		session.downloadReceived = 0;
		session.mode = SessionMODE.DOWNLOAD;
		resultDATA(session, size);
		doLog(session, "OO Receiving " + size + " bytes");
		if (size == 0)
			downloadFinish(session);
	}
	private void downloadFinish(Session session) throws IOException {
		session.downloadOut.close();
		session.downloadOut = null;
		session.downloadFile = session.downloadTarget;
		session.downloadTarget = null;
		session.mode = SessionMODE.COMMAND;
		doLog(session, "OO Received: " + session.downloadFile.getName());
		resultOKAY(session);
	}
	private void downloadRename(Session session, String content) {
		if (session.downloadFile == null || session.downloadNamed)
			return;
		File source = session.downloadFile;
		String name = source.getName();
		File target = new File(source.getParentFile(), name.substring(0, name.length() - "-fastboot.bin".length()) + "-" + content + ".bin");
		try {
			Files.move(source.toPath(), target.toPath());
			session.downloadFile = target;
			session.downloadNamed = true;
			doLog(session, "OO Renamed: " + target.getName());
		} catch (IOException ex) {
			doLog(session, "XX Rename failed: " + source.getAbsolutePath() + " -> " + target.getAbsolutePath() + " (" + ex.getMessage() + ")");
		}
	}
	private void downloadFirmware(Session session, String cmd) {
		if (session.downloadFile == null) {
			resultFAIL(session, "Firmware download incomplete");
			return;
		}

		switch (cmd) {
			case "fw_flash":
				String slot = envGet(session, "linux_fs_start");
				// deleted before fw_flash !!
				if (slot == null)
					slot = "0";
				// fw_flash sets other slot
				if (slot.equals("1"))
					slot = "0";
				else
					slot = "1";
				envSet(session, "linux_fs_start", slot);

				downloadRename(session, "fitimg");
				resultOKAY(session,"Device needs reboot");
				break;
			case "fw_ramload":
				downloadRename(session, "ramload");
				resultOKAY(session);
				break;
			case "bootloader_update":
				downloadRename(session, "bootloader");
				resultOKAY(session);
				break;
		}
	}
	private byte[] downloadedMeta(Session session) throws IOException {
		if (session.downloadFile == null)
			throw new IOException("Config download incomplete");
		if (session.downloadFile.length() > 1 * 1024 * 1024) // 1MB, max 4GB
			throw new IOException("Config download too large");
		return Files.readAllBytes(session.downloadFile.toPath());
	}

	private void fastbootOEM(Session session, String arg) throws IOException {

		int sep = arg.indexOf(':');
		final String cmd;
		final String val;
		if (sep == -1) {
			cmd = arg;
			val = "";
		} else {
			cmd = arg.substring(0, sep);
			val = arg.substring(sep + 1);
		}

		switch (cmd) {
			case "feature_flags":
				String provider = envGet(session, "provider");
				if (provider != null && !provider.isEmpty())
					resultOKAY(session, "KEEP_PROVIDER_AT_FACTORY_RESET");
				else
					resultOKAY(session, "NO_FEATURES");
				//	resultFAIL(session, "unrecognized command");
				break;
			case "reset_run_counter":
				counterReset(session);
				resultOKAY(session);
				break;
			case "reset_provider":
				envSet(session, "provider", "");
				resultOKAY(session);
				break;
			case "finalize":
				resultFAIL(session, "Device is finalized");
				break;
			case "test":
				resultOKAY(session, val);
				break;
			case "get_env_all":
				String selectedData = "";
				for (Map.Entry<String, String> entry : envAll(session).entrySet())
					selectedData += entry.getKey() + " " + entry.getValue() + "\n";
				session.selectedData = Helper.string2bytes(selectedData);
				session.selectedName = "environment";
				resultOKAY(session);
				break;
			case "get_env":
				String value = envGet(session, val);
				if (value == null)
					resultFAIL(session, "Variable not found");
				else
					resultOKAY(session, value);
				break;
			case "set_env":
				int i = val.indexOf(':');
				if (i > 0) {
					if (envSet(session, val.substring(0, i), val.substring(i + 1)))
						resultOKAY(session);
					else
						resultFAIL(session, "Invalid environment variable");
				} else {
					resultFAIL(session, "Invalid set_env arguments");
				}
				break;
			case "set_env_all":
				downloadRename(session, "environment");
				LinkedHashMap<String, String> env = envParse(downloadedMeta(session));

				// clean all variables
				for (String key : envAll(session).keySet())
					envSet(session, key, "");

				// set new variables
				String badName = "";
				for (Map.Entry<String, String> entry : env.entrySet())
					if (!envSet(session, entry.getKey(), entry.getValue()))
						badName += " " + entry.getKey();

				if (badName.isEmpty())
					resultOKAY(session);
				else
					resultFAIL(session, "Invalid environment variable(s):" + badName);
				break;
			case "tffs_read":
				session.selectedData = new byte[0];
				session.selectedName = "TFFS " + val;
				int rID = -1;
				try { rID = Integer.parseInt(val); }
				catch (NumberFormatException e) {}
				if (rID < 1024 || rID > 1031){
					resultFAIL(session, "Invalid TFFS ID");
					break;
				}

				byte[] data = tffs.get(rID);
				if (data == null)
					data = counterTffs(session, rID);
				session.selectedData = data != null ? data.clone() : new byte[0];
				if (data == null)
					resultFAIL(session, "Reading TFFS entry failed");
				else
					resultOKAY(session); // upload may be empty
				break;
			case "tffs_write":
				int wID = -1;
				try { wID = Integer.parseInt(val); }
				catch (NumberFormatException e) {}
				if (wID < 2 || wID > 255){
					resultFAIL(session, "Invalid TFFS ID");
					break;
				}

				downloadRename(session, "tffs-" + wID);
				tffs.put(wID, downloadedMeta(session));
				resultOKAY(session);
				break;
			case "factory_defaults":
				// unimplemented: load some default-environment, should keep reboot_ver
				if (val.equals("wipe_env"))
					counterReset(session);
				resultOKAY(session);
				break;
			case "fw_wipe":
				resultOKAY(session);
				break;
			case "fw_flash":
				downloadFirmware(session, cmd);
				break;
			case "fw_ramload":
				if (val.equals("wipe_env"))
					doLog(session, "OO Ignoring bootloader update");
				downloadFirmware(session, cmd);
				break;
			case "bootloader_update":
				downloadFirmware(session, cmd);
				break;
			case "boot":
				boolean boot_valid = true;
				switch (val) {
					case "":
						doLog(session, "OO Booting active slot");
						break;
					case "0":
						doLog(session, "OO Booting slot 0");
						break;
					case "1":
						doLog(session, "OO Booting slot 1");
						break;
					default:
						boot_valid = false;
						break;
				}
				if (boot_valid) {
					sessionExpireForce(session);
					resultOKAY(session);
				} else
					resultFAIL(session, "Invalid partition provided");
				break;
			case "reboot_without_wait":
				resultOKAY(session);
				sessionExpireForce(session);
				break;
			case "get_mac_addresses":
				session.selectedData = new byte[0];
				session.selectedName = arg;
				doLog(session, "XX Not emulated: oem " + arg);
				resultFAIL(session, "Not emulated: " + cmd);
				break;
			case "set_mac_addresses":
				downloadRename(session, "mac-addresses");
				doLog(session, "XX Not emulated: oem " + arg);
				resultFAIL(session, "Not emulated: " + cmd);
				break;
			case "get_calib":
				session.selectedData = new byte[0];
				session.selectedName = arg;
				doLog(session, "XX Not emulated: oem " + arg);
				resultFAIL(session, "Not emulated: " + cmd);
				break;
			case "set_calib":
				downloadRename(session, "calibration");
				doLog(session, "XX Not emulated: oem " + arg);
				resultFAIL(session, "Not emulated: " + cmd);
				break;
			case "backup_calib":
				session.selectedData = new byte[0];
				session.selectedName = arg;
				doLog(session, "XX Not emulated: oem " + arg);
				resultFAIL(session, "Not emulated: " + cmd);
				break;
			case "erase_calib":
				doLog(session, "XX Not emulated: oem " + arg);
				resultFAIL(session, "Not emulated: " + cmd);
				break;
			default:
				doLog(session, "XX Unknown OEM command: " + arg);
				resultFAIL(session, "Unknown OEM command");
				break;
		}
	}
	private void fastbootCMD(Session session, String line) throws IOException {
		doLog(session, "<< " + line);

		// reset sessionExpireForce on further commands after re/boot
		sessionExpireReset(session);

		if (line.startsWith("oem ")) {
			fastbootOEM(session, line.substring(4));
			return;
		}

		int sep = line.indexOf(':');
		final String cmd;
		final String arg;
		if (sep == -1) {
			cmd = line;
			arg = "";
		} else {
			cmd = line.substring(0, sep);
			arg = line.substring(sep + 1);
		}

		switch (cmd) {
			case "getvar":
				String value = envGet(session, "oem__" + arg);
				if (value == null) {
					doLog(session, "XX Variable missing: " + arg);
					resultFAIL(session, "Variable not found");
				} else
					resultOKAY(session, value);
				break;
			case "download":
				downloadStart(session, arg);
				break;
			case "upload":
				session.uploadData = session.selectedData;
				session.uploadOffset = 0;
				doLog(session, "OO Sending: " + session.selectedName + " (" + session.uploadData.length + " bytes)");
				resultDATA(session, session.uploadData.length);
				if (session.uploadData.length == 0)
					resultOKAY(session);
				else
					session.mode = SessionMODE.UPLOAD;
				break;
			case "boot":
				downloadRename(session, "boot-image");
			case "continue":
			case "reboot":
				doLog(session, "OO Emulating " + cmd.toUpperCase());
				resultOKAY(session);
				sessionExpireForce(session);
				break;
			default:
				doLog(session, "XX Unknown command: " + line);
				resultFAIL(session, "Unknown command");
				break;
		}
	}

	private static final class FrameID {
		static final int ERROR = 0;
		static final int QUERY = 1;
		static final int INIT = 2;
		static final int FASTBOOT = 3;
	}
	private byte[] frame(int id, int flags, int sequence, byte[] data) {
		byte[] ret = new byte[4 + data.length];
		ret[0] = (byte) id;
		ret[1] = (byte) flags;
		ret[2] = (byte) (sequence >> 8);
		ret[3] = (byte) sequence;
		System.arraycopy(data, 0, ret, 4, data.length);
		return ret;
	}
	private byte[] fastbootData(Session session, int flags, int seq, byte[] data) throws IOException {

		if (data.length == 0) {
			// empty payload: host polls for a response or upload data

			if (!session.replies.isEmpty()) {
				// send queued responses before upload data

				byte[] reply = session.replies.remove();
				int max = session.packetSize - 4;

				if (reply.length <= max) {
					// short response, fits in 1 packet
					return frame(FrameID.FASTBOOT, 0, seq, reply);
				} else {
					// long response, fragment; remaining packets at queue head
					ArrayDeque<byte[]> rest = new ArrayDeque<>();
					rest.add(Arrays.copyOfRange(reply, max, reply.length));
					rest.addAll(session.replies);
					session.replies.clear();
					session.replies.addAll(rest);
					return frame(FrameID.FASTBOOT, 1, seq, Arrays.copyOf(reply, max));
				}
			} else {
				if (session.mode == SessionMODE.UPLOAD) {
					// upload: send next data block to host

					int end = Math.min(session.uploadData.length, session.uploadOffset + session.packetSize - 4);
					byte[] reply = Arrays.copyOfRange(session.uploadData, session.uploadOffset, end);
					session.uploadOffset = end;
					boolean more = end < session.uploadData.length;
	
					// last block: queue OKAY for the next poll
					if (!more) {
						session.mode = SessionMODE.COMMAND;
						resultOKAY(session);
					}
					return frame(FrameID.FASTBOOT, more ? 1 : 0, seq, reply);
				} else {
					// nothing pending: send empty response
					return frame(FrameID.FASTBOOT, 0, seq, new byte[0]);
				}
			}
		} else {
			// non-empty payload: host sends command or download data

			if (!session.replies.isEmpty() || session.mode == SessionMODE.UPLOAD) {
				// host must read pending responses/upload before sending new data
				return frame(FrameID.ERROR, 0, seq, Helper.string2bytes("Poll pending response first"));
			}

			if (session.mode == SessionMODE.DOWNLOAD) {
				// download: receive data from host

				if (data.length > session.downloadSize - session.downloadReceived) {
					// reject data exceeding the announced download size
					session.closeDownload();
					session.mode = SessionMODE.COMMAND;
					resultFAIL(session, "Download exceeds declared size");
				} else {
					session.downloadOut.write(data);
					session.downloadReceived += data.length;

					// completion is determined by byte count, not continuation flag
					if (session.downloadReceived == session.downloadSize)
						downloadFinish(session);
				}
			} else {
				// command mode: decode payload and execute command
				fastbootCMD(session, Helper.bytes2string(data));
			}

			// acknowledge received packet; command result follows on a poll
			return frame(FrameID.FASTBOOT, 0, seq, new byte[0]);
		}

	}
	private byte[] fastbootHead(Session session, byte[] request) {
		byte[] reply;

		// ID: 0=error 1=query 2=init 3=fastboot
		int id = request[0] & 255;
		// Flags: 0=single/last 1=more follows
		int flags = request[1] & 255;
		// Sequence number: overflow 65535 -> 0
		int seq = ((request[2] & 255) << 8) | (request[3] & 255);

		// ID=1: query sequence number - independent of sequence number and replay
		if (id != 1) {
			// duplicate request, replay response
			if (session.lastRequest != null && Arrays.equals(session.lastRequest, request))
				return session.lastResponse;

			// unexpected sequence number, ignore
			if (seq != session.sequence)
				return null;
		}

		// Flags: 0 flag or 1 for continuation, nothing else allowed
		if (flags > 1) {
			reply = frame(FrameID.ERROR, 0, seq, Helper.string2bytes("Invalid flags"));
		}

		// ID=1: query expected sequence number
		else if (id == 1) {
			reply = frame(FrameID.QUERY, 0, seq, new byte[] { (byte) (session.sequence >> 8), (byte) (session.sequence) });
		}

		// ID=2: session init: negotiate protocol version and maximum packet size
		else if (id == 2) {
			if (request.length != 8 || request[4] != 0 || request[5] != 1)
				reply = frame(FrameID.ERROR, 0, seq, Helper.string2bytes("Unsupported initialization"));
			else {
				// host's maximum packet size, including the header
				int max = ((request[6] & 255) << 8) | (request[7] & 255);
				if (max < 8)
					reply = frame(FrameID.ERROR, 0, seq, Helper.string2bytes("Packet size too small"));
				else {
					session.closeDownload();
					session.replies.clear();
					session.mode = SessionMODE.COMMAND;
					session.selectedData = new byte[0];
					session.downloadFile = null;

					// use the smaller of host and server packet limits
					session.packetSize = Math.min(udpFastbootPacketMax, max);
					session.initialized = true;

					// show size + min-mtu
					int mtu = session.packetSize + (session.remoteIP.contains(":") ? 48 : 28);
					doLog(session, "OO Packet size: " + session.packetSize + " bytes; min MTU: " + mtu);

					// reply with protocol version 1 and negotiated packet size
					reply = frame(FrameID.INIT, 0, seq, new byte[] { 0, 1, (byte) (session.packetSize >> 8), (byte) (session.packetSize) });
				}
			}
		}

		// only fastboot packets are handled after initialisation
		else if (id != 3 || !session.initialized) {
			reply = frame(FrameID.ERROR, 0, seq, Helper.string2bytes("Initialize first"));
		}

		// enforce negotiated packet size
		else if (request.length > session.packetSize) {
			reply = frame(FrameID.ERROR, 0, seq, Helper.string2bytes("Packet too large"));
		}

		// ID=3: finally fastboot
		else {
			try {
				// process payload without the 4-byte UDP header
				reply = fastbootData(session, flags, seq, Arrays.copyOfRange(request, 4, request.length));
			} catch (Exception e) {
				doLog(session, "XX Fastboot command failed: " + e.toString());
				session.closeDownload();
				session.mode = SessionMODE.COMMAND;

				resultFAIL(session, "Command failed: " + e.getMessage());
				reply = frame(FrameID.FASTBOOT, 0, seq, new byte[0]);
			}
		}

		// ID=1 (query) is independent of sequence number and replay
		if (id != 1) {
			// remember for retransmissions
			session.lastRequest = request.clone();
			session.lastResponse = reply;

			// advance expected sequence number, wrapping from 65535 to 0
			session.sequence = (session.sequence + 1) & 0xffff;
		}

		return reply;
	}

	@Override
	public void run() {
		final int fastbootPort = 5554;
		final long nanoSec = 1000000000;
		final long expireFast = nanoSec * 10; // 10sec
		final long expireSlow = nanoSec * 10 * 60; // 10min
		doLog("-- Fastboot starting on " + fastbootPort + "/udp");
		try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress("::", fastbootPort))) {
			socket.setSoTimeout(1000);
			ready = true;
			byte[] buffer = new byte[udpFastbootPacketMax + 1]; // detect to much data
			while (true) {

				// expire session
				final long now = System.nanoTime();
				Iterator<Map.Entry<String, Session>> iterator = sessions.entrySet().iterator();
				while (iterator.hasNext()) {
					Map.Entry<String, Session> entry = iterator.next();
					if ((entry.getValue().expiring && now - entry.getValue().expireStarted >= expireFast)
							|| now - entry.getValue().lastSeen >= expireSlow) {
						Session session = entry.getValue();
						session.closeDownload();
						iterator.remove();
						doLog(session, "-- Session expired: " + Helper.beautifyIP(session.remoteIP, session.remotePort));
					}
				}

				// receive data
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				try {
					socket.receive(packet);
				} catch (SocketTimeoutException e) {
					continue; // socket timeout out
				}
				if (packet.getLength() < 4)
					continue; // incomplete fastboot header

				// new session
				String remote = packet.getAddress().getHostAddress() + ":" + packet.getPort();
				Session session = sessions.get(remote);
				if (session == null) {
					if (sessions.size() > 99)
						continue; // no hammer time

					session = new Session(device, packet.getAddress().getHostAddress(), packet.getPort());
					sessions.put(remote, session);

					Helper.doLog();
					doLog(session, "++ Client from " + Helper.beautifyIP(session.remoteIP, session.remotePort));
				}
				session.lastSeen = System.nanoTime();

				// send reply
				byte[] reply = fastbootHead(session, Arrays.copyOfRange(packet.getData(), packet.getOffset(),
						packet.getOffset() + packet.getLength()));
				if (reply == null)
					continue; // Unexpected sequence number
				socket.send(new DatagramPacket(reply, reply.length, packet.getAddress(), packet.getPort()));
			}
		} catch (Exception e) {
			doLog("XX Fastboot listener failed: " + e.toString());
			e.printStackTrace();
			System.exit(1);
		}
	}

}

