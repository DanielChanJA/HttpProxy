package Main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;

/**
 * Created by Daniel Chan on 28/1/2017.
 *
 * Supports HTTP 1.1 GET/POST requests, DOES NOT SUPPORT HTTPS. WILL BREAK!
 */
public class Proxy {

  private int localPortNumber, remotePortNumber;
  private String hostName, destinationUrl;

  // Initialize the proxy server, by default webpages are on port 80, but the client can
  // specify others if he wants to.
  public Proxy(int localPort) {
    this.localPortNumber = localPort;
    this.remotePortNumber = 80;
    this.initialize();

  }

  private byte[] extractBytes(InputStream clientRequest, boolean replaceHost) {

    String requestContent;
    byte[] buffer = new byte[100000];

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int len = 0;
      while((len = clientRequest.read(buffer)) > 0) {
        baos.write(buffer, 0, len);
        baos.flush();
        break;
      }
      requestContent = new String(baos.toByteArray());

      // Debugging purposes, seeing the POST and GET requests from client.
//      System.out.println(requestContent);

      if(replaceHost) {
        this.hostName = extractHost(requestContent);

        return removeHost(baos);
      }
    } catch (IOException ioException) {
      System.out.println("Failed to close stream" + ioException);
    }
    return buffer;
  }


  private byte[] removeHost(ByteArrayOutputStream request) {

    String clientReq = new String(request.toByteArray());
    int startingIndex = clientReq.indexOf(this.destinationUrl);
    int endingIndex = clientReq.indexOf(this.hostName) + this.hostName.length();

    if(startingIndex <= 0) {
      return clientReq.getBytes();
    }

    // Debug message.
//    System.out.println("REMOVING: " + clientReq.substring(startingIndex - 1,
//        endingIndex));

    StringBuilder sb = new StringBuilder(clientReq);

    sb.delete(startingIndex - 1, endingIndex);

    // The case where the user inputs http://google.com with no / at the end
    // of the url.
    if(clientReq.charAt(endingIndex) == ' ') {
      sb.insert(4, '/');
    }

    // Debug message.
//    System.out.println("----- ADJUSTED HTTP REQ ------");
//    System.out.println(sb.toString());
//    System.out.print("----- END OF ADJUSTED   ------\n");

    return sb.toString().getBytes();

  }

  /*
  In a URL like http://www.httpbin.org/get we can split it up using regular
  expressions, where the '/' occurs. Which would result in an array of the
  following [http:, , www.httpbin.org, get] where we would always want the
  index of 2, if the first 5 characters of the string is http:
   */
  private String extractHost(String requestContent) {

    String[] requestSplit = requestContent.split(" ");

    this.destinationUrl = requestSplit[1].substring(1, requestSplit[1].length());

    String[] urlSplit = this.destinationUrl.split("/");

    if(urlSplit[0].equalsIgnoreCase("http:")) {
      return urlSplit[2];
    } else {
      return urlSplit[0];
    }
  }

  private void initialize() {

    Timestamp timestamp = new Timestamp(System.currentTimeMillis());

    try {
      ServerSocket serverListen = new ServerSocket(this.localPortNumber);
      System.out.println("["+ timestamp.toString() + "]" +" Listening on " +
          "port: " + this.localPortNumber);


      while (true) {

        Socket server = null;
        Socket client = null;
        try {

          client = serverListen.accept();

          System.out.println("["+ timestamp.toString() + "]" + " Incoming " +
              "request" +
              " " +
              "from " + client
              .getRemoteSocketAddress().toString());

          final InputStream fromClient = client.getInputStream();
          final OutputStream toClient = client.getOutputStream();

          final byte[] req = extractBytes(fromClient, true);

//          System.out.println(requestContent);

          // Debugging purpose, to see what the host is being set to.
          System.out.println("["+ timestamp.toString() + "] " +client
              .getRemoteSocketAddress().toString() + " " +
              "attempting to connect to " + this.destinationUrl);

          try {
            server = new Socket(this.hostName, this.remotePortNumber);
          } catch (IOException ioError) {
            System.out.println("Unable to connect to " + this.hostName + " on port " +
                this.remotePortNumber);
            client.close();
            continue;
          }

          System.out.println("["+ timestamp.toString() + "] " +"Successfully connected to " + this.destinationUrl);

          final InputStream fromServer = server.getInputStream();
          final OutputStream toServer = server.getOutputStream();

          Thread tunnelClientInfo = new Thread() {
            public void run() {
              try {
                toServer.write(req, 0, req.length);
                toServer.flush();

              } catch (IOException ioError) {
                System.out.println("Something happened to the client's socket. Unable" +
                    " to read info." + ioError);
              }
            }
          };

          // Initialize the thread. So both client -> proxy and server ->
          // proxy streams start at the same time.
          tunnelClientInfo.start();


          byte[] response = extractBytes(fromServer, false);

          // Hangs here for some weird reason, unable to debug why it hangs
          // here. FIXED.
          try {
              toClient.write(response, 0, response.length);
              toClient.flush();

          } catch (IOException ioError) {
            System.out.println("Something happened to the server's socket. Unable to read" +
                " from it." + ioError);
          }

        } finally{
            try {
              if (server != null) {
                server.close();
              }
              if (client != null) {
                client.close();
              }
            } catch (IOException e) {
              System.out.println("Failed to close server/client sockets." + e);
            }
          }

        }

      } catch(IOException ioError){
        System.out.println(ioError);
        System.out.println("ERROR: Port in use, failed to initialize.");
        System.exit(1);
      }


    }


  }
