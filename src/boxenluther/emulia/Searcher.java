package boxenluther.emulia;

import java.net.DatagramPacket;
import java.net.Inet4Address;
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

	private Inet4Address getEndpoint(final String remote, final List<InetAddress> addresses) {
		if (!remote.contains("."))
			return null;

		String subnet = remote;
		InetAddress current = null;
		// /24
		subnet = subnet.substring(0, subnet.lastIndexOf('.', subnet.length() - 2) + 1);
		for (Iterator<InetAddress> i = addresses.iterator(); i.hasNext();) {
			current = i.next();
			if (current instanceof Inet4Address && current.getHostAddress().startsWith(subnet))
				return (Inet4Address) current;
		}
		// /16
		subnet = subnet.substring(0, subnet.lastIndexOf('.', subnet.length() - 2) + 1);
		for (Iterator<InetAddress> i = addresses.iterator(); i.hasNext();) {
			current = i.next();
			if (current instanceof Inet4Address && current.getHostAddress().startsWith(subnet))
				return (Inet4Address) current;
		}
		// /8
		subnet = subnet.substring(0, subnet.lastIndexOf('.', subnet.length() - 2) + 1);
		for (Iterator<InetAddress> i = addresses.iterator(); i.hasNext();) {
			current = i.next();
			if (current instanceof Inet4Address && current.getHostAddress().startsWith(subnet))
				return (Inet4Address) current;
		}
		// fallback
		current = null;
		try {
			current = InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, (byte) 178, (byte) 1 });
		} catch (Exception e) {}
		doLog("XX Fallback to " + current.getHostAddress().toString());
		return (Inet4Address) current;
	}
	private Inet6Address getEndpoint(final InetAddress address, final List<NetworkInterface> interfaces) {
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
					return (Inet6Address) current;
			}
		}

		// fallback
		current = null;
		try {
			current = InetAddress.getByAddress(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0x01 });
		} catch (Exception e) {}
		doLog("XX Fallback to " + current.getHostAddress().toString());
		return (Inet6Address) current;
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
		byte[] bufferTX = null;

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
				switch (packetRX.getLength()) {
					case 8: // Slint IPv6
						if (!new String(bufferRX, 0, 8).equals("AVMfritz")) {
							doLog("XX Invalid Slint packet received");
							continue;
						}
						if (packetRX.getPort() != 5035) {
							doLog("XX Invalid Slint source port.");
							continue;
						}
						if (!(packetRX.getAddress() instanceof Inet6Address)) {
							doLog("XX Invalid Slint ip protocoll.");
							continue;
						}
						doLog("XX Detected Slint recovery");

						// answering
						Inet6Address addressLOC6 = getEndpoint(packetRX.getAddress(), interfaces);

						bufferTX = new byte[]{'f','r','i','t','z','A','V','M'};

						doLog(">> Replying with IP " + addressLOC6.getHostAddress().split("%")[0] + "%" + addressLOC6.getScopeId());
						break;
					case 16: // Adam2 IPv4
						if (	bufferRX[0] != 00 ||
								bufferRX[1] != 00 ||
								bufferRX[2] != 18 || // static
								bufferRX[3] != 01 || // static
								bufferRX[4] != 01 || // search
								bufferRX[5] != 00 ||
								bufferRX[6] != 00 ||
								bufferRX[7] != 00 ||
								bufferRX[12] != 0 ||
								bufferRX[13] != 0 ||
								bufferRX[14] != 0 ||
								bufferRX[15] != 0) {
							doLog("XX Invalid Adam2 packet received");
							continue;
						}
						if (!(packetRX.getAddress() instanceof Inet4Address)) {
							doLog("XX Invalid Adam2 ip protocol.");
							continue;
						}
						doLog("XX Detected Adam2 recovery");

						// desired
						final byte[] addressBYT = new byte[] { bufferRX[8], bufferRX[9], bufferRX[10], bufferRX[11] };
						final InetAddress addressREQ = InetAddress.getByAddress(addressBYT);
						doLog("XX Requested ip " + Helper.beautifyIP(addressREQ.getHostAddress()));

						// answering
						Inet4Address addressLOC4 = getEndpoint(remote, addresses);

						bufferTX = new byte[16];
						Arrays.fill(bufferTX, (byte) 0);
						bufferTX[2] = (byte) 18; // static
						bufferTX[3] = (byte) 01; // static
						bufferTX[4] = (byte) 02; // answer
						byte[] barrayLOC = addressLOC4.getAddress();
						bufferTX[11] = (byte) barrayLOC[0];
						bufferTX[10] = (byte) barrayLOC[1];
						bufferTX[9] = (byte) barrayLOC[2];
						bufferTX[8] = (byte) barrayLOC[3];

						doLog(">> Replying with IP " + addressLOC4.getHostAddress().toString());
						break;
					default:
						doLog("XX Unknown packet length received");
						continue; 
				}

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
