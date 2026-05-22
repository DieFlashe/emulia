package boxenluther.emulia;

import java.net.ServerSocket;
import java.net.Socket;

public class Main {
	static public boolean running = true;

	static final private String tag = "SRV";
	static private void doLog(String txt) {
		Helper.doLog();
		Helper.doLog(tag, txt);
	}

	public static void main(String[] args) {
		String confFile = "_Generic";
		if (args != null && args.length > 0)
			confFile = args[0];
	
//TODO  load custom device-config on start -> discarded -> use args + collected ^^
//		confFile = "DEVICE";	//DEVEL

		int i = -1;
		i = confFile.lastIndexOf("\\");
		if (i>0)
			confFile=confFile.substring(i);
		i = confFile.lastIndexOf("/");
		if (i>0)
			confFile=confFile.substring(i);
		i = confFile.lastIndexOf(".");
		if (i>0)
			confFile=confFile.substring(0,i);
		confFile = Helper.chkConfigFile(confFile);
		Helper.setConfigFile(confFile + ".txt");

		final Device device = new Device(true);		// reload env on program start
//		final Device device = null;					// reload env on every connect

		new Searcher().start();

		ServerSocket listener = null;

		try {
			final int ftpcontrolPort = 21;
			doLog("-- FTP-Server starting on " + ftpcontrolPort + "/tcp");
			listener = new ServerSocket(ftpcontrolPort);
		} catch (Exception e) {
			doLog("XX Error creating listener: " + e.toString());
			e.printStackTrace();
			System.exit(1);
			return;
		}

		while (running) {
			try {
				Socket socket = listener.accept();
				doLog("<< Client connected from " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
				new Worker(device,socket).start();
			} catch (Exception e) {
				doLog("XX Error creating worker: " + e.toString());
				e.printStackTrace();
				running=false;
			}
		}

		try {
			doLog("-- FTP-Server stopping");
			listener.close();
		} catch (Exception e) {}

	}


}
