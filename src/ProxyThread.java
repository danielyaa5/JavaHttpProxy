import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Arrays;

/**
 * This class manages a socket connection to the proxy.
 * Every request to Proxy creates a ProxyThread which inturn
 * forwards the request to the requested server waits for a response
 * and returns that response back to the client.
 */
public class ProxyThread extends Thread {

	private Socket clientSocket;
	private Socket serverSocket;

	//	connection timeout 
	private static final int TO = 10 * 1000;
	// buffer size used by body read
	private static final int BUFFER = 1024;

	/**
	 * The constructor, takes the clients socket
	 * that should be coming from the Proxy class's
	 * welcome socket.
	 */
	public ProxyThread(Socket connectionToProxy) {
		super("ProxyThread");
		this.clientSocket = connectionToProxy;
		this.serverSocket = null;
	}

	/**
	 * Heres where the action happens
	 * 
	 * Parse request from client --> 
	 * If the request is a POST request, parse the 
	 * body of the requestPull the hostname --> 
	 * Use DNS cache to find serverAddress -->  
	 * opens socket connection to server --> 
	 * forward the clients request
	 * 
	 * Wait for the server to respond with data --> 
	 * relay response back to client
	 */
	@Override
	public void run() {

		// Byte arrays to hold the request and response
		byte[] clientRequest;
		byte[] serverResponse;

		// Buffered input/output streams from socket connections
		// between proxy and client and between proxy and server
		BufferedInputStream clientBis, serverBis;
		BufferedOutputStream clientBos, serverBos;

		// store parsed client request and parsed server response 
		ByteArrayOutputStream request, response;

		// Variables for setting up the server socket
		String hostname = "";
		String requestType = "N/A";
		InetAddress serverAddress;

		// Variable for parsing the response body
		int bodySize = 0;
		
		// Variable for parsing the request body (in case of post)
		int requestCl = 0;

		try {
			// Setup in/out to the client socket
			clientBis = new BufferedInputStream(clientSocket.getInputStream());
			clientBos = new BufferedOutputStream(clientSocket.getOutputStream());
			request = new ByteArrayOutputStream();

			// Parse the header for the hostname and the entire request
			String[] returnField = parseHeader(clientBis, request).toString().split(" ");
			// Uncomment for debugging
			System.out.println(Arrays.toString(returnField));
			if (returnField.length > 1) {
				requestType = returnField[0];
				hostname = returnField[1];
			} else {
				// In case the request is neither post nor get
				hostname = returnField[0];
			}
			clientRequest = request.toByteArray();

			if (requestType.equals("POST")) {
				// Parse the POST request body by appending it to the byte array
				// stream
				
				// Should have a content-length field for us, check just to make sure
				if (returnField.length > 2) {
					requestCl = Integer.parseInt(returnField[2]);
				}
				parseBody(clientBis, request, requestCl);
			}

			// Get the address from the hostname and setup the server socket
			
			// Check the cache for the address of the hostname
			if (DnsCache.getInstance().containsKey(hostname)) {
				serverAddress = DnsCache.getInstance().get(hostname);
			} else {
				// add to cache if it doesn't exist
				serverAddress = InetAddress.getByName(hostname);
				DnsCache.getInstance().put(hostname, serverAddress);
			}
			
			// Open connection to server
			this.serverSocket = new Socket(serverAddress.getHostAddress(), 80);
			this.serverSocket.setSoTimeout(TO);

			// Setup in/out streams to server socket
			serverBos = new BufferedOutputStream(serverSocket.getOutputStream());
			serverBis = new BufferedInputStream(serverSocket.getInputStream());
			response = new ByteArrayOutputStream();

			// Request from client is sent through connection to server
			serverBos.write(clientRequest);
			serverBos.flush();

			// Now that request is sent, parse the header of the response and grab content length of response
			String responseCl = parseHeader(serverBis, response).toString();
			
			// Just making sure our response has content length field
			if (!responseCl.isEmpty())
				bodySize = Integer.parseInt(responseCl);

			// Parse the response body by appending it to the byte array stream
			parseBody(serverBis, response, bodySize);

			// Our response is now built, send it to the client
			serverResponse = response.toByteArray();
			clientBos.write(serverResponse);
			clientBos.flush();

			// We are now done with our sockets and streams
			request.close();
			response.close();
			clientBis.close();
			clientBos.close();
			serverBis.close();
			serverBos.close();

			serverSocket.close();
			clientSocket.close();

		} catch (IOException e) {
			//e.printStackTrace();
		}

	}

	// HELPERS

	/**
	 * This helper is responsible for taking HTTP header and parsing important information
	 * and then pushing the request from the proxy to server or client.
	 * The header comes in the form of an input stream
	 * If this is a request header it parses for the request type, hostname and in the
	 * case of a post request, it also attempts to obtain the post's content length
	 * If this is a response, it only attempts to retrieve the response bodys content length
	 */
	private StringBuffer parseHeader(InputStream in, OutputStream out) {

		StringBuffer header = new StringBuffer("");
		// Responsible for creating string returned with all parsed information
		StringBuffer theReturn = new StringBuffer("");
		// Holds each line while going through header
		String line = "";
		// Used for looking for grabbing info from line
		String[] splitLine;
		// Used to indicate whether connection header was found
		boolean connectionHeaderFound = false;
		// Used to indicate whether the header is a request or response
		boolean isRequestHeader = false;
		boolean firstLine = true;

		try {
			// Read until null or line is empty, since it may not necessary terminate until connection ends
			while ((line = readLine(in)) != null && !line.isEmpty()) {
				// Check if its POST or GET request
				if (firstLine) {
					if (line.substring(0, 3).equals("GET")) {
						theReturn.append("GET ");
						isRequestHeader = true;
					} else if (line.substring(0, 4).equals("POST")) {
						theReturn.append("POST ");
						isRequestHeader = true;
					}
					firstLine = false;
				}
				// Fix the request change to connection close to prevent server from hanging
				if (line.contains("Proxy-connection: keep-alive") || line.contains("Connection: keep-alive")) {
					header.append("Connection: close\r\n");
					connectionHeaderFound = true;
				} else {
					header.append(line + "\r\n");
				}
				// For debugging and seeing every header, uncomment the
				// following line
				// System.out.println(data.toString());

				// If we're parsing a request, we need the hostname
				if (line.contains("Host: ") && isRequestHeader) {
					splitLine = line.split("Host: ");
					theReturn.append(splitLine[1] + " ");
				// If this is a response or a post, we want the content-length
				} else if (line.contains("Content-Length: ")) {
					splitLine = line.split("Content-Length: ");
					theReturn.append(splitLine[1]);
				}
			}

			// No connection header was found, if this is a request header, then
			// add connection close
			if (!connectionHeaderFound && isRequestHeader) {
				header.append("Connection: close\r\n");
			}

			// We need a terminating line appended at the end of every body
			header.append("\r\n");
			out.write(header.toString().getBytes(), 0, header.length());
		} catch (Exception e) {
			//e.printStackTrace();
		}
		
		return theReturn;
	}

	/**
	 * Helper method used for parsing a body, although no information is taken from the body.
	 * It simply is forwarded to an output stream which is then sent to client or server.
	 * We write to the output stream until contentSize is reached. OR if there was no
	 * content length provided we attempt to write to output stream until there is a disconnect.
	 */
	private void parseBody(InputStream in, OutputStream out, int contentSize) {
		// The current number of bytes processed to output
		int byteCount = 0;
		boolean waitForDisconnect = true;

		// If we already know the size of the body, then we don't have to wait
		// for the server
		if (contentSize > 0)
			waitForDisconnect = false;

		try {
			// Read data in BUFFER size chunks from the pody
			byte[] buffer = new byte[BUFFER];
			int b = 0;
			while ((waitForDisconnect || byteCount < contentSize) && (b = in.read(buffer)) >= 0) {
				out.write(buffer, 0, b);
				byteCount += b;
			}
			// Push all pending data into the output stream
			out.flush();
		} catch (Exception e) {
			//e.printStackTrace();
		}

	}

	/**
	 * Used for the parseHeader helper. Reads data one line at a time from an inputstream.
	 */
	private String readLine(InputStream in) {
		// Buffer to read bytes b into
		StringBuffer line = new StringBuffer("");
		int b;

		try {
			in.mark(1);
			if (in.read() == -1)
				return null;
			else
				in.reset();

			// While data is still valid and not yet reached an end line
			while ((b = in.read()) > 0 && b != '\r' && b != '\n') {
				line.append((char) b);
			}

			// Wind back if we reach end line
			if (b == '\r') {
				in.mark(1);
				if (!((b = in.read()) == '\n'))
					in.reset();
			}
		} catch (Exception e) {
			//e.printStackTrace();
		}

		return line.toString();
	}

}