

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class AsynchBFS {

	public static final String delimeter = ", ";
	public static final int maxMessageDelay = 2000;
	public static int messageCount = 0;

	public static int[][] spanningTreeMatrix;
	public static int[] parentNode;
	public static int[] distanceToNode;
	public static int[] countRequiredToSendResponse;

	private static int countOfProcessors;
	private static double countOflinks;

	public static class ServerThread implements Runnable {
		private int ownId;
		private int[] ownNeigh;
		private int root;
		
		Socket client = null;
		InputStreamReader in = null;
		BufferedReader bf = null;
		String strMessage = null;
		Server ss = null;
		String[] message = null;
		
		

		ServerThread(Socket Client, int id, int[] neigh, int root, Server server) throws IOException {
			this.client = Client;
			this.ownNeigh = neigh;
			this.ownId = id;
			this.ss = server;
			this.root = root;

			// receiving message from the client thread
			in = new InputStreamReader(client.getInputStream());
			bf = new BufferedReader(in);
			strMessage = bf.readLine();
			message = strMessage.split(delimeter);
		}

		@Override
		public void run() {
			// message[0] = Sender's node
			// message[1] = Distance between sender and receiver nodes (or) -1 in case of
			// sending ACK/NACK
			// message[2] = 'NORMAL' (or) 'ACK/NACK' value

			// Asynchronous BFS algorithm
			if (Integer.parseInt(message[1]) != -1
					&& (Integer.parseInt(message[1]) + 1) < AsynchBFS.distanceToNode[this.ownId]) {
				if (AsynchBFS.parentNode[this.ownId] != -1) {
					countRequiredToSendResponse[this.ownId] = 0;
					AsynchBFS.spanningTreeMatrix[this.ownId][AsynchBFS.parentNode[this.ownId]] = 0;
					AsynchBFS.spanningTreeMatrix[AsynchBFS.parentNode[this.ownId]][this.ownId] = 0;
				}

				AsynchBFS.distanceToNode[this.ownId] = Integer.parseInt(message[1]) + 1;
				AsynchBFS.parentNode[this.ownId] = Integer.parseInt(message[0]);

				AsynchBFS.spanningTreeMatrix[this.ownId][AsynchBFS.parentNode[this.ownId]] = 1;
				AsynchBFS.spanningTreeMatrix[AsynchBFS.parentNode[this.ownId]][this.ownId] = 1;

				String str = Integer.toString(AsynchBFS.distanceToNode[this.ownId]);

				for (int m = 0; m < ownNeigh.length; m++) {
					if (this.ownNeigh[m] != (8000 + AsynchBFS.parentNode[this.ownId])) {
						try {
							Socket s = new Socket("localhost", ownNeigh[m]);
							System.out.println(
									"Process " + this.ownId + " sends message to " + (this.ownNeigh[m] - 8000));
							PrintWriter pr = new PrintWriter(s.getOutputStream());

							// random delay to the message
							Random rand = new Random();
							int randValue = rand.nextInt(maxMessageDelay);
							Thread.sleep(randValue);

							AsynchBFS.messageCount++;
							pr.println(this.ownId + delimeter + str + delimeter + "NORMAL");
							pr.flush();
							s.close();
						} catch (IOException | InterruptedException e) {
							System.out.println("ERROR: " + e.getMessage());
						}
					}
				}
			}

			// Convergecast - termination detection
			if ("ack".equals(message[2]) || "nack".equals(message[2])) {
				countRequiredToSendResponse[this.ownId]++;

				if (root == this.ownId && countRequiredToSendResponse[this.ownId] == this.ownNeigh.length) {
					// Root node receives all ack's from its neighbors
					System.out.println("root node received all acknowledgements");
					// method to print final output matrix and average number of messages
					printResult();
				} else if (root != this.ownId && countRequiredToSendResponse[this.ownId] == this.ownNeigh.length - 1) {
					// Received all responses(ACK/NACK) from its neighbors except its parent node,
					// send ACK to its parent
					try {
						Socket s = new Socket("localhost", (parentNode[ownId] + 8000));
						PrintWriter pr = new PrintWriter(s.getOutputStream());
						Thread.sleep(new Random().nextInt(maxMessageDelay));
						AsynchBFS.messageCount++;
						System.out.println("Process " + this.ownId + " sends ACK to " + (parentNode[ownId]));
						pr.println(this.ownId + delimeter + (-1) + delimeter + "ack");
						pr.flush();
						s.close();
					} catch (IOException | InterruptedException e) {
						System.out.println("ERROR : " + e.getMessage());
					}

				}
			} else if (ownNeigh.length == 1 && "NORMAL".equals(message[2])) {
				// reached end of nodes, send ACK to its parent, no other node to send request
				try {
					Socket s = new Socket("localhost", (parentNode[ownId] + 8000));
					PrintWriter pr = new PrintWriter(s.getOutputStream());
					Thread.sleep(new Random().nextInt(maxMessageDelay));
					AsynchBFS.messageCount++;
					System.out.println("Process " + this.ownId + " sends ACK to " + (parentNode[ownId]));
					pr.println(this.ownId + delimeter + (-1) + delimeter + "ack");
					pr.flush();
					s.close();
				} catch (IOException | InterruptedException e) {
					System.out.println("ERROR: " + e.getMessage());
				}
			} else if (ownNeigh.length > 1 && Integer.parseInt(message[1]) != -1
					&& parentNode[this.ownId] != Integer.parseInt(message[0])) {
				// if the current node already had parent and then received request by other
				// node send NACK as response to the sender
				try {
					Socket s = new Socket("localhost", (Integer.parseInt(message[0]) + 8000));
					PrintWriter pr = new PrintWriter(s.getOutputStream());
					Thread.sleep(new Random().nextInt(maxMessageDelay));
					AsynchBFS.messageCount++;
					System.out.println("Process " + this.ownId + " sends NACK to " + (Integer.parseInt(message[0])));
					pr.println(this.ownId + delimeter + (-1) + delimeter + "nack");
					pr.flush();
					s.close();
				} catch (IOException | InterruptedException e) {
					System.out.println("ERROR: " + e.getMessage());
				}
			}

		}
	}

	public static class Server extends Thread {
		ExecutorService pool = null;
		private int ownId;
		private int[] ownNeigh;
		private int ownRoot;

		public Server(int id, int[] neigh, int root) {
			this.ownRoot = root;
			this.ownNeigh = neigh;
			this.ownId = id;
			pool = Executors.newFixedThreadPool(10);
		}

		public void run() {
			try {
				@SuppressWarnings("resource")
				ServerSocket ss = new ServerSocket(8000 + ownId);
				while (true) {
					Socket s = ss.accept();
					ServerThread runnable = new ServerThread(s, ownId, ownNeigh, ownRoot, this);
					pool.execute(runnable);
				}
			} catch (IOException e) {
			}
		}
	}

	public static class slaveThread extends Thread {
		private int ownId;
		private int[] ownNeigh;
		private int ownRoot;
		private String message;
		private Queue<Integer> ownQueue = new LinkedList<>();

		public slaveThread(int id, int[] neigh, int root, Queue<Integer> queue) {
			this.ownRoot = root;
			this.ownNeigh = neigh;
			this.ownId = id;
			this.ownQueue = queue;
		}

		@Override
		public void run() {
			try {
				// creating server threads for each process
				Server server = new Server(ownId, ownNeigh, ownRoot);
				server.start();

				Thread.sleep(1000);

				if (ownId == ownRoot && this.ownQueue.size() != 0) {
					distanceToNode[this.ownId] = 0;
					message = Integer.toString(this.ownQueue.remove());
					for (int m = 0; m < ownNeigh.length; m++) {
						try {
							Socket s = new Socket("localhost", ownNeigh[m]);
							System.out.println(
									"Process " + this.ownId + " sends message to " + (this.ownNeigh[m] - 8000));
							PrintWriter pr = new PrintWriter(s.getOutputStream());
							AsynchBFS.messageCount++;

							// random delay to the message
							Thread.sleep(new Random().nextInt(maxMessageDelay));

							pr.println(this.ownId + delimeter + message + delimeter + "NORMAL");
							pr.flush();
							s.close();
						} catch (IOException | InterruptedException e) {
						}
					}
				}
			} catch (InterruptedException e) {
			}
		}

	}

	public static void main(String[] args) {

		double sum = 0;
		
		String FILE_PATH = "data/inputForAsynchBFS.txt";
		File file = new File(FILE_PATH);

		BufferedReader br;

		try {
			br = new BufferedReader(new FileReader(file));

			int noOfProcessors = Integer.parseInt(br.readLine());
			int root = Integer.parseInt(br.readLine());
			int[][] adj_mat = new int[noOfProcessors][noOfProcessors];

			for (int i = 0; i < noOfProcessors; i++) {
				String[] row = br.readLine().split(delimeter);
				for (int j = 0; j < noOfProcessors; j++) {
					adj_mat[i][j] = Integer.parseInt(row[j]);
				}
			}

			// initialise data
			countOfProcessors = noOfProcessors;

			spanningTreeMatrix = new int[noOfProcessors][noOfProcessors];
			parentNode = new int[noOfProcessors];
			distanceToNode = new int[noOfProcessors];
			countRequiredToSendResponse = new int[noOfProcessors];

			for (int i = 0; i < noOfProcessors; i++) {
				parentNode[i] = -1;
				distanceToNode[i] = Integer.MAX_VALUE;
			}

			for (int i = 0; i < noOfProcessors; i++)
				for (int j = 0; j < noOfProcessors; j++)
					if (adj_mat[i][j] == 1)
						sum++;

			AsynchBFS.countOflinks = sum;
			
			ArrayList<Integer> myList = new ArrayList<Integer>();
			for (int i = 0; i < noOfProcessors; i++) {
				// finding the neighbors of the nodes
				myList.clear();
				for (int j = 0; j < noOfProcessors; j++) {
					if (adj_mat[i][j] == 1) {
						myList.add(8000 + j);
					}
				}
				int[] neigh = new int[myList.size()];
				for (int m = 0; m < neigh.length; m++) {
					neigh[m] = myList.get(m).intValue();
				}

				// message order: id, neighbor, root, queue
				Queue<Integer> q = new LinkedList<>();

				if (i == root) {
					q.add(0);
					Thread newThread = new slaveThread(i, neigh, root, q);
					newThread.start();
				} else {
					Thread newThread = new slaveThread(i, neigh, root, q);
					newThread.start();
				}
			}
		} catch (FileNotFoundException e1) {
			System.out.println("File Not found at " + file.getAbsolutePath());
		} catch (NumberFormatException e) {
			System.out.println("Invalid data provided in input file");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void printResult() {
		System.out.println("Output Adjacency Matrix:");
		for (int i = 0; i < AsynchBFS.countOfProcessors; i++) {
			for (int j = 0; j < AsynchBFS.countOfProcessors; j++) {
				System.out.print(spanningTreeMatrix[i][j] + " ");
			}
			System.out.println();
		}
		
		System.out.println("\nAverage messages per link: " + (AsynchBFS.messageCount) / (AsynchBFS.countOflinks / 2));
		System.exit(0);
	}
}