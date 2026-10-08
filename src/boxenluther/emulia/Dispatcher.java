package boxenluther.emulia;

import java.net.ServerSocket;
import java.net.Socket;

public class Dispatcher extends Thread {

	public volatile boolean ready = false;

	private final Device device;
	public Dispatcher(Device device) {
		super();
		this.device = device;
	}

	final private String tag = "SRV";
	private void doLog(String txt, boolean init) {
		if (!init)
			Helper.doLog();
		Helper.doLog(tag, txt);
	}
	private void doLog(String txt) {
		doLog(txt, false);
	}

	@Override public void run() {
		final int ftpcontrolPort = 21;
		doLog("-- FTP-Server starting on " + ftpcontrolPort + "/tcp", true);
		try (ServerSocket listener = new ServerSocket(ftpcontrolPort)) {
			ready = true;
			while (true) {
				Socket socket = null;
				// accept
				try {
					socket = listener.accept();
				} catch (Exception e) {
					doLog("XX Error accepting clients: " + e.toString());
					e.printStackTrace();
					System.exit(1);
				}
				// worker
				try {
					doLog("++ Client connected from " + Helper.beautifyIP(socket.getInetAddress().getHostAddress(), socket.getPort()));
					new Worker(device, socket).start();
				} catch (Exception e) {
					doLog("XX Error creating worker: " + e.toString());
					e.printStackTrace();
					try {
						socket.close();
					} catch (Exception ex) {}
				}
			}
		} catch (Exception e) {
			doLog("XX Error creating listener: " + e.toString());
			e.printStackTrace();
			System.exit(1);
		}
	}

}
