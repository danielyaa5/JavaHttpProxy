import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Proxy {
	// Port is 5000 + 41
	private final int PORT;
	private ServerSocket welcomeSocket = null;
	private boolean listening = true;

	public Proxy(int port) {
		PORT = port; 
		
		try {
			welcomeSocket = new ServerSocket(PORT);
			listenForConnectionRequests();
		} catch (IOException e) {
			System.err.println("There was a problem while creating the welcome socket");
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	public void listenForConnectionRequests() {
		System.out.println("Listening for connection requests at " + PORT + "\n");
		
		while (listening) {
			try {
				new ProxyThread(welcomeSocket.accept()).start();
			} catch (IOException e) {
				System.err.println("I/O error occured while waiting for a connection.");
				e.printStackTrace();
			}
		}
	}
}
