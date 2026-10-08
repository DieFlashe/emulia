package boxenluther.emulia;

public class Main {

	public static void main(String[] args) {

		String confFile = "_Generic";
		if (args != null && args.length > 0)
			confFile = args[0];

//TODO  load custom device-config on start -> discarded -> use args + collected ^^
		confFile = Helper.confFile(confFile); // DEBUG
//		confFile = "DEVICE";	//DEVEL

		int i = -1;
		i = confFile.lastIndexOf("\\");
		if (i > 0)
			confFile = confFile.substring(i);
		i = confFile.lastIndexOf("/");
		if (i > 0)
			confFile = confFile.substring(i);
		i = confFile.lastIndexOf(".");
		if (i > 0)
			confFile = confFile.substring(0, i);
		confFile = Helper.chkConfigFile(confFile);
		Helper.setConfigFile(confFile + ".txt");

		final Device device = new Device(true);		// reload env on program start
//		final Device device = null;					// reload env on every connect
		Helper.doLog();


		final Searcher searcher = new Searcher();
		searcher.start();
		while (!searcher.ready) {
			try { Thread.sleep(9); }
			catch (Exception e) {}
		}

		final Fastboot fastboot = new Fastboot(device);
		fastboot.start();

		final Dispatcher dispatcher = new Dispatcher(device);
		dispatcher.start();

		while (!fastboot.ready && !dispatcher.ready) {
			try { Thread.sleep(9); }
			catch (Exception e) {}
		}
		searcher.running = true;


	}

}
