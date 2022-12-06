import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class SClientRun {

  private int serverPort;
  private String host;

  private Socket socket;
  private ObjectInputStream ois;
  private ObjectOutputStream oos;

  private ClientListener clientListener;
  private Client client;
  private Thread clientThread;

  public SClientRun(int serverPort, String host, ClientListener clientListener) {
    this.serverPort = serverPort;
    this.host = host;
    this.clientListener = clientListener;
  }

  public void start() {
    client = new Client();
    clientThread = new Thread(client);
    clientThread.start();
  }

  public void close() {
    if (client != null) {
      client.stop();
    }
    if (clientThread != null && clientThread.isAlive()) {
      clientThread.interrupt();
    }
  }

  public void sendData(byte[] datum) {
    client.sendData(datum);
  }

  private class Client implements Runnable {

    @Override
    public void run() {
      try {
        System.out.println("Waiting for connection");
        socket = new Socket(host, serverPort);
        clientListener.onConnected();
        oos = new ObjectOutputStream(socket.getOutputStream());
        ois = new ObjectInputStream(socket.getInputStream());
        System.out.println("Got streams");
        while (true) {
          System.out.println("Waiting for data");
          byte[] datum = (byte[]) ois.readObject();
          System.out.println("Data received");
          clientListener.onDataReceived(datum);
        }
      } catch (EOFException eof) {
        clientListener.onDisconnected();
      } catch (IOException | ClassNotFoundException ex) {
        if (ex != null && ex.getMessage().contains("Socket closed")) {
          return;
        }
        clientListener.onFailed();
      } finally {
        try {
          if (socket != null)
            socket.close();
        } catch (IOException e) {
          e.printStackTrace();
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
            e.printStackTrace();
          }
        }
      });
    }

    public void stop() {
      try {
        if (socket != null)
          socket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public interface ClientListener {
    public void onConnected();

    public void onDisconnected();

    public void onDataReceived(byte[] datum);

    public void onFailed();

  }
}
