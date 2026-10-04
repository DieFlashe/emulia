package boxenluther.emulia;

public class Fastboot extends Thread {

	public volatile boolean ready = false;

	public Fastboot(Device device) {
		super();
	}
}
