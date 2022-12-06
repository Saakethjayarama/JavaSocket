import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class SServerRun  {


  private ServerSocket serverSocket;
  private Socket socket;
  private int port;

  private List<Client> clients = null;
  private ServerListener serverListener = null;
  private Server server;

  private List<Thread> clientThreads = null;
  private Thread serverThread = null;

  public SServerRun(int port, ServerListener serverListener) {
    this.port = port;
    clients = new ArrayList<>();
    clientThreads = new ArrayList<>();
    this.serverListener = serverListener;
  }

  public void start() {
    if (serverThread != null && serverThread.isAlive()) {
      serverThread.interrupt();
    }

    for (Thread t : clientThreads) {
      if (t != null && t.isAlive()) {
        t.interrupt();
      }
    }
    server = new Server();
    serverThread = new Thread(server);
    serverThread.start();

  }

  public void stop() {
    for (Client c : clients) {
      if (c != null) {
        c.stop();
      }
    }

    server.stop();
    serverThread.interrupt();

    for (Thread t : clientThreads) {
      if (t != null) {
        t.interrupt();
      }
    }
  }

  public void sendData(byte[] datum) {
    server.sendData(datum);
  }

  public class Server implements Runnable {
    
    @Override
    public void run() {
      try {
        System.out.println(port);
        serverSocket = new ServerSocket(port);
        while (true) {
          socket = serverSocket.accept();
          serverListener.onConnected();

          ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
          ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

          // Closing all threads of previous clients
          for (Thread t : clientThreads) {
            if (t != null && t.isAlive()) {
              t.interrupt();
            }
          }
          
          // servicing the connections on new threads
          Client client = new Client(socket, in, out);
          Thread clientThread = new Thread(client);
          clientThread.start();

          // adding threads and clients to list
          clients.add(client);
          clientThreads.add(clientThread);
        }
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        serverThread.interrupt();
      }
    }
    
    public void sendData(byte[] datum) {
      for (Client c : clients) {
        c.sendData(datum);
      }
    }

    public void stop() {
      try {
        serverSocket.close();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }


  }

  public class Client implements Runnable {

    private Socket socket;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;

    public Client(Socket socket, ObjectInputStream ois, ObjectOutputStream oos) {
      this.socket = socket;
      this.ois = ois;
      this.oos = oos;
    }

    @Override
    public void run() {
      try {
        while (true) {
          byte[] bytes = (byte[]) ois.readObject();
          System.out.println("Got data " + bytes.length);
          serverListener.onDataReceived(bytes);
        }
      } catch (EOFException e) {
        serverListener.onDisconnected();
      } catch (ClassNotFoundException | IOException e) {
        e.printStackTrace();
        if (e != null && e.getMessage().contains("Socket closed")) {
          return;
        }
        serverListener.onFailed();
      } finally {
        for (Thread thread : clientThreads) {
          thread.interrupt();
        }
      }
    }

    public void sendData(byte[] datum) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            oos.flush();
            oos.writeObject(datum);
          } catch (IOException e) {
          }
        }
      }).start();
    }

    public void stop() {
      try {
        this.socket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

  }

  public interface ServerListener {
    public void onConnected();

    public void onDisconnected();

    public void onDataReceived(byte[] dataBytes);

    public void onFailed();
  }

}
