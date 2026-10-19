package boxenluther.emulia;

public class Main {

	public static void main(String[] args) {

		String confFile = "_Generic";
		confFile = Helper.confFile(confFile); // DEBUG
		if (args != null && args.length > 0)
			confFile = args[0];

//TODO  load custom device-config on start -> discarded -> use args + collected ^^
//		confFile = "DEVICE";	// DEVEL

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


		final Boolean runFastboot = device == null || device.isSubmarine();
		final Boolean runDispatcher = device == null || device.isParadise();

		final Searcher searcher = new Searcher(runDispatcher, runFastboot);
		searcher.start();
		while (!searcher.ready) {
			try { Thread.sleep(9); }
			catch (Exception e) {}
		}

		Fastboot fastboot = null;
		if (runFastboot) {
			fastboot = new Fastboot(device);
			fastboot.start();
		}

		Dispatcher dispatcher = null;
		if (runDispatcher) {
			dispatcher = new Dispatcher(device);
			dispatcher.start();
		}

		while ((runFastboot && !fastboot.ready) || (runDispatcher && !dispatcher.ready)) {
			try { Thread.sleep(9); }
			catch (Exception e) {}
		}
		searcher.running = true;


	}

}
