package defaultpackage;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Server {

    private static final Set<String> names = Collections.synchronizedSet(new HashSet<>());
    private static Set<Handler> handlers = Collections.synchronizedSet(new HashSet<>());
    private static final List<Integer> clientIDs = Collections.synchronizedList(new ArrayList<>());
    private static int nextClientID = 1;
    private static String coordinatorID = null;
    private static int clientCount = 0;
    private static String requestingClient = null;
    
    // Colour variables, used to set text colours. ANSI_RESET reverts to default
    public static final String COLOUR_DEFAULT = "\u001B[0m";
    public static final String COLOUR_GREEN = "\u001B[32m";

    


	// Main method, creates server on a thread, server socket listens for incoming clients
    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running...");
        ExecutorService pool = Executors.newFixedThreadPool(500);
        try (ServerSocket listener = new ServerSocket(59001)) {
            while (true) {
                pool.execute(new Handler(listener.accept()));
            }
        }
    }

    // Logs message to the server
    public static String formatLogMessage1(String clientId, String name, String message) {
        LocalDateTime timestamp = LocalDateTime.now();
        String formattedTimestamp = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format("%s Client %s, %s: %s", formattedTimestamp, clientId, name, message);
    }

    // If no coordinator exists, will set the first (or next) client as coordinator
    static void setNewCoordinator() {
        if (!handlers.isEmpty()) {
            Handler newCoordinator = handlers.iterator().next();
            coordinatorID = newCoordinator.name;
            newCoordinator.out.println("YOUARECOORDINATOR");
            broadcast("COORDINATORIS " + coordinatorID);
        } else {
            coordinatorID = null;
        }
    }

    // Broadcast sends message to everyone, basis of group chat
    private static void broadcast(String message) {
        for (Handler handler : handlers) {
            handler.out.println(message);
        }
    }

    // Request member details (/rmd) command, prints out names, IDs, IPs and ports of current members
    private static void sendMemberDetails(PrintWriter out) {
        StringBuilder details = new StringBuilder("MEMBERDETAILS");
        for (Handler handler : handlers) {
            details.append(" ").append(handler.clientId)
                    .append("|").append(handler.name).append("|")
                    .append(handler.socket.getInetAddress().getHostAddress()).append("|")
                    .append(handler.socket.getPort());
        }
        details.append(" COORDINATOR:").append(coordinatorID);
        out.println(details.toString());
    }

    // -----------------------------------------------------------------------------------------------
    
    // Handler class runs, handles interactions from clients and provides responses
    static class Handler implements Runnable {
        String name;
        private int clientId;
        private Socket socket;
        private Scanner in;
        PrintWriter out;

        public Handler(Socket socket) {
        	// Each client is given an ID and added to the clientIDs list
            this.socket = socket;
            synchronized (clientIDs) {
                this.clientId = nextClientID++;
                clientIDs.add(this.clientId);
            }
        }

        @Override
        public void run() {
            try {
                in = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);

                // First step is to ask for name
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.nextLine();
                    if (name == null || name.isEmpty() || names.contains(name)) {
                        return;
                    }
                    synchronized (names) {
                        if (!names.contains(name)) {
                            names.add(name);
                            break;
                        }
                    }
                }

                // If name is accepted, will assign you as coordinator if you are the first person to join
                out.println("NAMEACCEPTED " + name);
                handlers.add(this);
                if (coordinatorID == null) {
                    coordinatorID = name;
                    out.println("YOUARECOORDINATOR");
                } else {
                    out.println("COORDINATORIS " + coordinatorID);
                }

                /* If there is more than one client (therefore a coordinator exists), will need permission
                   from coordinator to use /rmd function */
                synchronized (this) {
                    clientCount++;
                    if (clientCount > 1) {
                        out.println("MESSAGE Please ask for permission to request member details.");
                    }
                }
                while (true) {
                    String input = in.nextLine();
                    if (input == null) {
                        return;
                    }

                    /* If user types in /rmd, will request member details from coordinator, they can accept or deny
                       If the user is a coordinator, /rmd can be run without further ado */
                    if ("/rmd".equalsIgnoreCase(input)) {
                        if (clientCount > 1 && !name.equals(coordinatorID)) {
                            requestingClient = name;
                            for (Handler handler : handlers) {
                                if (handler.name.equals(coordinatorID)) {
                                    handler.out.println("MESSAGE Permission request: " + name + " is requesting member details (ACCEPT / DENY).");
                                    break;
                                    
                                }
                            }
                        } else {
                            sendMemberDetails(this.out);
                        }
                    } else if (input.startsWith("ACCEPT") && name.equals(coordinatorID)) {
                        for (Handler handler : handlers) {
                            if (handler.name.equals(requestingClient)) {
                                sendMemberDetails(handler.out);
                                break;
                            }
                        }
                        requestingClient = null;
                    } else if (input.startsWith("DENY") && name.equals(coordinatorID)) {
                        for (Handler handler : handlers) {
                            if (handler.name.equals(requestingClient)) {
                                handler.out.println("MESSAGE Your request for member details has been denied.");
                                break;
                            }
                        }
                        requestingClient = null;
                    }
                    
                    // If user types in /msg, will run the private message method
                    else if (input.startsWith("/msg ")) {
                        sendPrivateMessage(input);
                    } 
                    
                    /* If no special commands are used, any input will be broadcasted to the group chat.
                       In any case, messages are logged to the server. */
                    else {
                        broadcast("MESSAGE " + name + ": " + input);
                    }
                    logMessage("Client " + clientId + ", " + name + ": " + input);
                }
                
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
            	/* If someone leaves, server will log it and display it. If coordinator leaves, the next earliest person
            	   to join is made the new coordinator */
                if (name != null) {
                    names.remove(name);
                    handlers.remove(this);
                    broadcast("MESSAGE " + name + " has left");
                    logMessage("Client " + clientId + ", " + name + " has left");
                    if (name.equals(coordinatorID)) {
                        setNewCoordinator();
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Error closing socket: " + e.getMessage());
                }
            }
        }

        private void logMessage(String string) {
			
		}

		/* Private message method. Will ask for a user ID, and then anything afterwards is treated as the message
           Sender will see what they've sent and to whom, and recipient will see who sent the message
           Text is green to differentiate from regular broadcast messages */
        private void sendPrivateMessage(String input) {
            int firstSpace = input.indexOf(' ');
            int secondSpace = input.indexOf(' ', firstSpace + 1);
            if (secondSpace != -1) {
                String recipient = input.substring(firstSpace + 1, secondSpace);
                String message = input.substring(secondSpace + 1);
                for (Handler handler : handlers) {
                    if (handler.name.equalsIgnoreCase(recipient)) {
                        handler.out.println("MESSAGE " + COLOUR_GREEN + "[Private from " + this.name + "]: " + message + COLOUR_DEFAULT);
                        this.out.println("MESSAGE " + COLOUR_GREEN + "[Private to " + recipient + "]: " + message + COLOUR_DEFAULT);
                        return;
                    }
                }
                this.out.println("MESSAGE [System]: User '" + recipient + "' not found.");
            }
        }
    }




}