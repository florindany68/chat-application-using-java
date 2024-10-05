package defaultpackage;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Client {

    private String serverAddress;
    private Scanner in;
    private PrintWriter out;
    private String name;
    private String currentCoordinator;

    public Client(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Pass the server IP as the sole command line argument: 127.0.0.1");
            return;
        }
        Client client = new Client(args[0]);
        client.run();
    }

    // First thing the client sees on startup, asks for their name and sends it to server.
    private String getName() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter your name: ");
        return scanner.nextLine();
    }
    
    // After name is entered, will print message explaining how to use the server and run commands
    // Client will then run on its own thread, and will continuously monitor input.
    private void startMessageSendingThread() {
        System.out.println("Available commands:\n- Type any message to send it to all.\n- Type '/rmd' to request member details. If you are not the first client, this will require coordinator approval.\n- Type '/msg [recipient] [message]' to send a private message to a specific user.");
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();
                out.println(input);
            }
        }).start();
    }
    
    // Will process details and clean them up
    private void handleMemberDetails(String details) {
        System.out.println("Member Details:");
        String[] members = details.split(" ");
        for (String member : members) {
            System.out.println(member.replace("|", " | "));
        }
    }

    private void run() throws IOException {
        try (Socket socket = new Socket(serverAddress, 59001)) {
            in = new Scanner(socket.getInputStream());
            out = new PrintWriter(socket.getOutputStream(), true);

            while (true) {
                if (in.hasNextLine()) {
                    String line = in.nextLine();
                    if (line.startsWith("SUBMITNAME")) {
                        this.name = getName();
                        out.println(this.name);
                    } else if (line.startsWith("NAMEACCEPTED")) {
                        System.out.println("You are now connected as " + line.substring(13));
                        startMessageSendingThread();
                    } else if (line.startsWith("MESSAGE")) {
                        System.out.println(line.substring(8));
                    } else if (line.startsWith("YOUARECOORDINATOR")) {
                        currentCoordinator = this.name;
                        System.out.println("You have been designated as the coordinator.");
                    } else if (line.startsWith("COORDINATOR IS")) {
                        currentCoordinator = line.substring(13);
                        System.out.println("The current coordinator is: " + currentCoordinator);
                    } else if (line.startsWith("MEMBERDETAILS")) {
                        handleMemberDetails(line.substring(13));
                    }
                } else {
                    System.out.println("Error connecting to the server, username already exists. ");
                  break; 
                }
            }
        } catch (NoSuchElementException e) {
            System.out.println("Disconnected from server: " + e.getMessage());
        }
    }
}