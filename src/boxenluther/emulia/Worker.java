package boxenluther.emulia;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.CRC32;

public class Worker extends Thread {
	public boolean running = true;

	private String tag;	//= "T" + this.threadId();  // deprecated: .getId()
	private Method myIdMethod() {
		try {
			return Thread.class.getMethod("threadId");	// since Java 19
		} catch (Exception e) {}
		try {
			return this.getClass().getMethod("getId");	// up to Java 1.8
		} catch (Exception e) {}
		return null;
	}
	private String myIdString() {
		try {
			return "T" + ( (Long)myIdMethod().invoke(this) ).toString();
		} catch (Exception e) {}
		return null;
	}
	private void doLog(String txt) {
		if (tag == null)
			tag = myIdString();
		Helper.doLog(tag, txt);
	}

	public Worker(Device device, Socket socket) {
		super();
		if (device!=null)
			this.device = device;
		else
			this.device = new Device();
		this.ctlSocket = socket;
	}

	private Socket ctlSocket;
	private PrintWriter ctlOutWriter;
	private BufferedReader ctlInReader;

	private ServerSocket datServer;
	private Socket dataSocket;
	private PrintWriter datWriter;

	final private Device device;
	final private String validUser = "adam2";
	final private String validPass = "adam2";
	
	private String currentUser = "";
	private String currentMode = "BINARY";
	private String currentMedia = "FLASH";

//	private Boolean debugging = false;
	Map<String, String> lastHash = new HashMap<>();
	Map<String, String> lastFile = new HashMap<>();

	private void sendLine(String msg) {
		doLog(">> " + msg);
		ctlOutWriter.println(msg);
	}
	private void sendLine(int val, String txt) {
		sendLine(val + " " + txt);
	}

	private void datClose() {
		try {
			datWriter.close();
		} catch (Exception e) {
		}
		datWriter = null;
		try {
			dataSocket.close();
		} catch (Exception e) {
		}
		dataSocket = null;
		try {
			datServer.close();
		} catch (Exception e) {
		}
		datServer = null;
		doLog("-- DAT closed");
	}

	private void fileTX(String arg) {
		String fileName = arg.toLowerCase();
		
		if (dataSocket == null) {
			sendLine(501, "Error, no transfer mode");
			return;
		}
		
		switch (currentMode) {
			case "BINARY":
				break;
			case "ASCII":
				break;
			default:
				sendLine(425, "Use PORT or PASV first.");
				return;
		}
		doLog("OO Sending: " + fileName);

		List<String> content = null;
		switch (fileName) {
			case "env":
				//TODO: Recovery of some FIT do 'RETR env ; SETENV subsys_id 1 ; RETR env'
				content = device.getEnv();
				break;
			case "count":
				content = device.getCount();
				break;
		}

		if (content==null) {
			if (!new File(fileName).exists()) {
				sendLine(501, "Error, file does not exist");
				return;
			}
		}

		sendLine(150, "Opening " + currentMode + " data connection");

		if (currentMode.equals("BINARY")) {
//			sendLine(150, "Opening BINARY data connection");
			BufferedOutputStream fout = null;
			BufferedInputStream fin = null;
			try {
				if (content == null)
					fin = new BufferedInputStream(new FileInputStream(lastFile.get(fileName)));
				else
					fin = new BufferedInputStream(new ByteArrayInputStream(String.join("\r\n", content).getBytes(StandardCharsets.UTF_8)));
				fout = new BufferedOutputStream(dataSocket.getOutputStream());

				byte[] buf = new byte[1024];
				int l = 0;
				while ((l = fin.read(buf, 0, 1024)) != -1) {
					fout.write(buf, 0, l);
				}
			} catch (Exception e) {
				doLog("XX BIN failes: " + e.getMessage());
				e.printStackTrace();
			}
			try {
				fin.close();
			} catch (Exception e) {}
			try {
				fout.close();
			} catch (Exception e) {}
		}

		if (currentMode.equals("ASCII")) {
//			sendLine(150, "Opening ASCII data connection");
			BufferedReader rin = null;
			PrintWriter rout = null;
			try {
				if (content == null)
					rin = new BufferedReader(new FileReader(lastFile.get(fileName)));
				else
					rin = new BufferedReader(new StringReader(String.join("\r\n", content)));
				rout = new PrintWriter(dataSocket.getOutputStream(), true);

				String s;
				while ((s = rin.readLine()) != null)
					rout.println(s);
			} catch (Exception e) {
				doLog("XX ASC failed: " + e.getMessage());
				e.printStackTrace();
			}
			try {
				rin.close();
			} catch (Exception e) {}
			try {
				rout.close();
			} catch (Exception e) {}
		}

		sendLine(226, "Transfer complete");
		datClose();
	}

	private void fileRX(String arg) {
		String fileName = Helper.getOutPath() + Long.toString(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() / 1000L);
		if (arg != null && !arg.isEmpty())
			fileName = fileName + "-" + arg
				.replace("=", "13")
				.replace(">", "14")
				.replace(";", "11")
				.replace("<", "12")
				.replace(" ", "_");
		switch (currentMode) {
			case "BINARY":
				fileName = fileName + ".bin";
				break;
			case "ASCII":
				fileName = fileName + ".txt";
				break;
			default:
				sendLine(501, "Error, invalid transfer mode");
				return;
		}
		doLog("OO Receiving: " + fileName);

		File file = new File(fileName);
		if (file.exists()) {
			sendLine(501, "Error, file yet exists");
			return;
		}

		sendLine(150, "Opening " + currentMode + " data connection");

		if (currentMode.equals("BINARY")) {
			BufferedOutputStream fout = null;
			BufferedInputStream fin = null;
			try {
				fout = new BufferedOutputStream(new FileOutputStream(file));
				fin = new BufferedInputStream(dataSocket.getInputStream());

				byte[] buf = new byte[1024];
				int l = 0;
				while ((l = fin.read(buf, 0, 1024)) != -1) {
					fout.write(buf, 0, l);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				fin.close();
			} catch (Exception e) {}
			try {
				fout.close();
			} catch (Exception e) {}
		}

		if (currentMode.equals("ASCII")) {
			BufferedReader rin = null;
			PrintWriter rout = null;
			try {
				rin = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
				rout = new PrintWriter(new FileOutputStream(file), true);

				String s;
				while ((s = rin.readLine()) != null) {
					rout.println(s);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				rout.close();
			} catch (Exception e) {}
			try {
				rin.close();
			} catch (Exception e) {}
		}

		String hash = fileCrc(arg, fileName);
		if (arg != null && !arg.isEmpty() && !hash.isEmpty()) {
			lastHash.put(arg.toLowerCase(), hash);
			lastFile.put(arg.toLowerCase(), fileName);
		}
		doLog("OO Received: " + hash);
		sendLine(226, "Transfer complete");
		datClose();
	}

	private String fileCrc(String envKey, String fileName) {
		String hash = "";

		try {
			Long imgSize = 0L;
			Long mtdSize = 0L;
			Long filler = 0L;

			// crc of file itself
			InputStream is = new BufferedInputStream(new FileInputStream(fileName));
			CRC32 crc = new CRC32();
			int c;
			while ((c = is.read()) != -1) {
				crc.update(c);
				imgSize++;
			}
			is.close();

			// mtdSize from env
			envKey = envKey
					.replace("=", "13")
					.replace(">", "14")
					.replace(";", "11")
					.replace("<", "12")
					.replace(" ", "_");
			if (device.hasEnvVar(envKey)) {
				try {
					final String[] offsets = device.getEnvVal(envKey).split(",");
					if (offsets.length == 2) {
						final Long offset0 = Long.parseLong(offsets[0].replace("0x", ""), 16);
						final Long offset1 = Long.parseLong(offsets[1].replace("0x", ""), 16);
						mtdSize = offset1 - offset0;
						filler = mtdSize - imgSize;
					}
				} catch (Exception e) {}
			}

			// crc with filler FFs
			for (int i = 0; i < filler; i++)
				crc.update((byte) 255);					

			doLog("%% imgSize :=" + dottedNum(imgSize));
			doLog("%% mtdSize :=" + dottedNum(mtdSize));
			doLog("%% filler  :=" + dottedNum(filler));

			hash = "0x" + Long.toHexString(crc.getValue()).toUpperCase();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return hash;
	}

	private String dottedNum(Long arg) {
		int insertAt;
		String ret = " " + arg;
		insertAt = 7;
		if (ret.length()>insertAt)
			ret=ret.substring(0, ret.length()-insertAt+1) +"."+ ret.substring(ret.length()-insertAt+1);
		insertAt = 4;
		if (ret.length()>insertAt)
			ret=ret.substring(0, ret.length()-insertAt+1) +"."+ ret.substring(ret.length()-insertAt+1);
		while (ret.length()<12)
			ret = " " + ret;
		return ret;		
	}

	public void run() {
		try {
			ctlInReader = new BufferedReader(new InputStreamReader(ctlSocket.getInputStream()));
			ctlOutWriter = new PrintWriter(ctlSocket.getOutputStream(), true);
			sendLine(220, device.dAdam());

			while (running) {
				String line = ctlInReader.readLine();
				String cmd = "";
				String arg = "";
				int sepidx = -1;
				if (line != null) {
					doLog("<< " + line);
					if (line.toUpperCase().startsWith("QUOTE "))
						line = line.substring(6);
					sepidx = line.indexOf(' ');
					cmd = (((sepidx == -1) ? line : (line.substring(0, sepidx)))).toUpperCase();
					arg = ((sepidx == -1) ? "" : line.substring(sepidx + 1));
				}

				switch (cmd) {
					case "USER":
						currentUser = arg;
						sendLine(331, "Password required for adam2");
//						sendLine(331, "Password required for " + currentUser);
						break;
					case "PASS":
						if (arg.equals(validPass) && currentUser.equals(validUser)) {
							sendLine(230, "User adam2 successfully logged in");
//							sendLine(230, "User " + currentUser + " successfully logged in");
						} else {
							sendLine(230, "User adam2 successfully logged in");
//							sendLine(530, "Not logged in");
						}
						break;
					case "SYST":
						sendLine(215, device.dEva());
						break;
					case "TYPE":
						switch (arg.toUpperCase()) {
						case "I":
							currentMode = "BINARY";
							sendLine(200, "Type set to BINARY");
							break;
						case "A":
						default:
							currentMode = "ASCII";
							sendLine(200, "Type set to ASCII");
							break;
						}
						break;
					case "MEDIA":
						switch (arg.toUpperCase()) {
						case "FLSH":
						case "FLASH":
							currentMedia = "FLASH";
							break;
						case "SDRAM":
						default:
							currentMedia = "SDRAM";
							break;
						}
						sendLine(200, "Media set to MEDIA_" + currentMedia);
						break;
					case "GETENV":
						if (device.hasEnvVar(arg)) {
							sendLine(device.getEnvVar(arg));
							sendLine("");
							sendLine(200, "GETENV command successful");
						}
						else
							sendLine(501, "environment variable not set");
						break;
					case "SETENV":
						String key = arg;
						String val = "";
						final int i = arg.indexOf(' ');
						if (i!=-1) {
							key = arg.substring(0,i).trim();
							val = arg.substring(i).trim();
						}
						if (device.hadEnvVar(key) || Helper.allEnvVars.contains(key)) {
							device.setEnvVar(key, val);
							sendLine(200, "SETENV command successful");
						} else
							sendLine(501, "environment variable not set");
						break;
					case "UNSETENV":
						if (device.hasEnvVar(arg)) {
							device.delEnvVar(arg);
						}
						sendLine(200, "UNSETENV command successful");
						break;
					case "P@SW":
					case "PASV":
					case "EPSV":
						try {
							datServer = new ServerSocket(0);
							final String host = ctlSocket.getLocalAddress().getHostAddress().replace('.', ',');
							final String port = (int) (datServer.getLocalPort() / 256) + "," + (int) (datServer.getLocalPort() % 256);
							sendLine(227, "Entering Passive Mode (" + host + "," + port + ")");
							dataSocket = datServer.accept();
							datWriter = new PrintWriter(dataSocket.getOutputStream(), true);
							doLog("-- PAS opened");
						} catch (Exception e) {
							doLog("XX PAS opening failed: " + e.getMessage());
							e.printStackTrace();
						}
						break;
					case "PORT":
					case "EPRT":
						final String[] argparts = arg.split(",");
						final String host = argparts[0] + "." + argparts[1] + "." + argparts[2] + "." + argparts[3];
						final int port = Integer.parseInt(argparts[4]) * 256 + Integer.parseInt(argparts[5]);
						try {
							dataSocket = new Socket(host, port);
							datWriter = new PrintWriter(dataSocket.getOutputStream(), true);
							doLog("-- ACT opened");
							sendLine(200, "Command OK");
						} catch (Exception e) {
							doLog("XX ACT opening failed: " + e.getMessage());
							e.printStackTrace();
						}
						break;
					case "CHECK":
						if (arg.isEmpty()) {
							sendLine(501, "Syntax error: Invalid number of parameters");
						} else {
							final String hash = lastHash.get(arg.toLowerCase());
							if (hash == null) {
								sendLine(501, "unknown variable " + arg);
							} else
								sendLine(150, "Flash check " + hash);
						}
						break;
					case "GET":
					case "RETR":
						fileTX(arg);
						break;
					case "PUT":
					case "STOR":
						fileRX(arg);
						break;
					case "REBOOT":
					case "QUIT":
					case "BYE":
						sendLine(221, "Thank you for using the FTP service on ADAM2");
						sendLine(221, "Goodbye.");
						running = false;
						break;
					case "V":
					case "VER":
					case "VERSION":
						sendLine(200, "Emulia v1.0"); 
						break;
//					case "DEBUG":
//						debugging=!debugging;
//						sendLine(200, "Debugging " + (debugging ? "ON" : "OFF") );
//						break;
//					case "PWD":
//						sendLine(257, "\"/\" is your current location");
//						break;
//					case "CWD":
//					case "CD":
//						sendLine(250, "OK. Current directory is " + arg); 
//						break;
//					case "LS":
//					case "DIR":
//					case "LIST":
//						sendLine(550, "No files found");
//						break;
//					case "NLST":
//						sendLine(425, "No data connection.");
//						break;
//					case "OPTS":
//						sendLine(501, "OPTS: " + arg.split(" ")[0] + " not understood");
//						break;
//					case "FEAT":
//						sendLine("211-Extensions supported:");
//						sendLine(211, "End.");
//						break;
//					case "HELP":
//						sendLine("214-The following SITE commands are recognized");
//						sendLine(214, "End.");
//						break;
					case "":
						break;
					default:
						sendLine(502, "Command not implemented");
						break;
				}
			}
		} catch (Exception e) {
			doLog("XX CTL failed: " + e.getMessage());
//			e.printStackTrace();
		}
	
		try {
			ctlOutWriter.close();
		} catch (Exception e) {}
		try {
			ctlInReader.close();
		} catch (Exception e) {}
		try {
			ctlSocket.close();
		} catch (Exception e) {}
		doLog("-- CTL closed");
	}


}
