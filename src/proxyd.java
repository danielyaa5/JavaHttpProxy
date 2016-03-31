import java.util.Arrays;

public class proxyd {
	private static Proxy theProxy = null;
	private static final int DEFAULT_PORT = 5041;
	
	public static void main(String[] args) {
		if (args.length > 0) {
			if (!args[0].equals("-port") || args.length != 2) {
				System.out.println("Invalid arguments supplied. Should be in following form 'java proxyd -port <your_assigned_port>'");
			} else {
				theProxy = new Proxy(Integer.parseInt(args[1]));
			}
		} else {
			theProxy = new Proxy(DEFAULT_PORT);
		}
	}
}
