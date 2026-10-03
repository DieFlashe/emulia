package boxenluther.emulia;

public class Main {

	public static void main(String[] args) {

		String confFile = "_Generic";
		if (args != null && args.length > 0)
			confFile = args[0];

//TODO  load custom device-config on start -> discarded -> use args + collected ^^
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

		final WorkerDispatcher worker = new WorkerDispatcher(device);
		worker.start();
		while (!worker.ready) {
			try {
				Thread.sleep(9);
			} catch (Exception e) {}
		}

		new Searcher().start();

	}

}
