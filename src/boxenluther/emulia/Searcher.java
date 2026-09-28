package boxenluther.emulia;

import java.net.DatagramPacket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Searcher extends Thread {
	public boolean running = true;

	final private String tag = "BCT";
	private void doLog(String txt) {
		doLog(tag, txt);
	}
	private void doLog(String tag, String txt) {
		Helper.doLog(tag, txt);
	}

	private InetAddress getEndpoint(final String remote, final List<InetAddress> addresses) {
		if (!remote.contains("."))
			return null;

		String subnet = remote;
		InetAddress current = null;
		// /24
		subnet = subnet.substring(0, subnet.lastIndexOf('.') + 1);
		for (Iterator<InetAddress> i = addresses.iterator(); i.hasNext();) {
			current = i.next();
			if (current.getHostAddress().startsWith(subnet))
				return (current);
		}
		// /16
		subnet = subnet.substring(0, subnet.lastIndexOf('.') + 1);
		for (Iterator<InetAddress> i = addresses.iterator(); i.hasNext();) {
			current = i.next();
			if (current.getHostAddress().startsWith(subnet))
				return (current);
		}
		// /8
		subnet = subnet.substring(0, subnet.lastIndexOf('.') + 1);
		for (Iterator<InetAddress> i = addresses.iterator(); i.hasNext();) {
			current = i.next();
			if (current.getHostAddress().startsWith(subnet))
				return (current);
		}
		// fallback
		current = null;
		try {
			current = InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 178, (byte) 1 });
		} catch (Exception e) {}
		doLog("XX Fallback to " + current.getHostAddress().toString());
		return current;
	}
	private InetAddress getEndpoint(final InetAddress address, final List<NetworkInterface> interfaces) {
		if (!(address instanceof Inet6Address))
			return null;

		InetAddress current = null;

		// scope matching
		final int scope = ((Inet6Address) address).getScopeId();
		for (NetworkInterface nif : interfaces) {
			if (nif.getIndex() != scope)
				continue;

			for (Enumeration<InetAddress> e = nif.getInetAddresses(); e.hasMoreElements();) {
				current = e.nextElement();
				if (current instanceof Inet6Address && current.isLinkLocalAddress())
					return current;
			}
		}

		// fallback
		current = null;
		try {
			current = InetAddress.getByAddress(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0x01 });
		} catch (Exception e) {}
		doLog("XX Fallback to " + current.getHostAddress().toString());
		return current;
	}

	private List<InetAddress> allEndpoints() {
		List<InetAddress> addresses = new ArrayList<InetAddress>();

		try {
			final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			for (Enumeration<NetworkInterface> ifs = interfaces; ifs.hasMoreElements();) {
				NetworkInterface nif = ifs.nextElement();
				try {
					if (nif.isUp())
						for (Enumeration<InetAddress> ips = nif.getInetAddresses(); ips.hasMoreElements();) {
							InetAddress nip = ips.nextElement();
							if (!nip.isLoopbackAddress() && !nip.isMulticastAddress() && !(nip instanceof Inet6Address && !nip.isLinkLocalAddress()))
								addresses.add(nip);
						}
				} catch (Exception e) {}
			}
		} catch (Exception e) {}

		// fallback
		if (addresses.isEmpty()) {
			try {
				addresses.add(InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 178, (byte) 1 }));
			} catch (Exception e) {}
		}

		for (Iterator<InetAddress> i = addresses.iterator(); i.hasNext();)
			doLog("-- Using IP: " + Helper.beautifyIP(i.next().getHostAddress().toString()));
		return addresses;
	}

	private List<NetworkInterface> allLLinterfaces() {
		List<NetworkInterface> interfaces = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
				NetworkInterface nif = e.nextElement();
				if (nif.isUp() && nif.supportsMulticast()) {
					for (Enumeration<InetAddress> ips = nif.getInetAddresses(); ips.hasMoreElements();) {
						InetAddress ip = ips.nextElement();
						if (ip instanceof Inet6Address && ip.isLinkLocalAddress()) {
							interfaces.add(nif);
							break;
						}
					}
				}
			}
		} catch (Exception e) {}
		return interfaces;
	}

	@Override
	public void run() {
		final int broadcastPort = 5035;
		MulticastSocket socketRX = null;
		DatagramPacket packetRX = null;
		byte[] bufferRX = new byte[16];
		byte[] bufferTX = new byte[16];
		Arrays.fill(bufferTX, (byte) 0);
		bufferTX[2] = (byte) 18;	// static
		bufferTX[3] = (byte) 1;		// static
		bufferTX[4] = (byte) 2;		// answer (1: search)
		InetAddress addressLOC = null;

		Map<String, Long> lastRemotes = new HashMap<>();
		String remote = null;
		Long now = null;
		Long lastAnswer = null;

		List<InetAddress> addresses = allEndpoints();
		List<NetworkInterface> interfaces = allLLinterfaces();

		// ff02::1
		InetAddress mcgroup = null;
		try {
			mcgroup = InetAddress.getByAddress(new byte[] { (byte) 0xff, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });
		} catch (Exception e) {}

		doLog("-- Waiting for broadcasts on " + broadcastPort + "/udp");
		String nifs = "";
		for (NetworkInterface nif : interfaces)
			nifs += " " + "{" + nif.getName() + "}";
		doLog("-- Using ifs: (0.0.0.0)" + nifs);

		// looping
		while (running) {
			try {
				// listening
				if (socketRX == null) {
					socketRX = new MulticastSocket(broadcastPort);
					for (NetworkInterface nif : interfaces) {
						try {
							socketRX.joinGroup(new java.net.InetSocketAddress(mcgroup, broadcastPort), nif);
						} catch (Exception e) {
							doLog("XX Listening failure: " + e.toString());
						}
					}
				}

				// receiving
				packetRX = new DatagramPacket(bufferRX, bufferRX.length);
				socketRX.receive(packetRX);
				doLog(null, ""); // empty line
				doLog("<< Request from " + Helper.beautifyIP(packetRX.getAddress().getHostAddress(), packetRX.getPort()));

				// ratelimit
				remote = packetRX.getAddress().getHostAddress();
				now = System.currentTimeMillis();
				lastAnswer = lastRemotes.get(remote);
				if (lastAnswer != null && lastAnswer + 1000 > now) {
					doLog("OO Ratelimit hit for " + Helper.beautifyIP(packetRX.getAddress().getHostAddress()));
					continue;
				}
				lastRemotes.put(remote, now);

				// requestion
				if (new String(bufferRX, 0, packetRX.getLength()).equals("AVMfritz")) {
					doLog("XX Detected Slint recovery");

					// answering
					addressLOC = getEndpoint(packetRX.getAddress(), interfaces);

					bufferTX[11] = (byte) 0;
					bufferTX[10] = (byte) 0;
					bufferTX[9] = (byte) 0;
					bufferTX[8] = (byte) 0;

				} else {
					doLog("XX Detected Adam2 recovery");

					final byte[] addressBYT = new byte[] { bufferRX[8], bufferRX[9], bufferRX[10], bufferRX[11] };
					final InetAddress addressREQ = InetAddress.getByAddress(addressBYT);
					doLog("XX Requested ip " + Helper.beautifyIP(addressREQ.getHostAddress()));

					// answering
					addressLOC = getEndpoint(remote, addresses);

					byte[] barrayLOC = addressLOC.getAddress();
					bufferTX[11] = (byte) barrayLOC[0];
					bufferTX[10] = (byte) barrayLOC[1];
					bufferTX[9] = (byte) barrayLOC[2];
					bufferTX[8] = (byte) barrayLOC[3];

				}

				if (addressLOC!=null)
					doLog(">> Replying with IP " + addressLOC.getHostAddress().toString());
				DatagramPacket sendPacket = new DatagramPacket(bufferTX, bufferTX.length, packetRX.getAddress(), broadcastPort);
				socketRX.send(sendPacket);
			} catch (Exception e) {
				doLog("XX Shit happend: " + e.toString());
				e.printStackTrace();
				try {
					socketRX.close();
				} catch (Exception ee) {}
				socketRX = null;
			}
		}

		try {
			socketRX.close();
		} catch (Exception e) {}

	}


}
