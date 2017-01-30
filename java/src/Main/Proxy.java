package Main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.time.LocalDateTime;

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
      while ((len = clientRequest.read(buffer)) > 0) {
        baos.write(buffer, 0, len);
        baos.flush();
        break;
      }
      requestContent = new String(baos.toByteArray());

      // Debugging purposes, seeing the POST and GET requests from client.
//      System.out.println(requestContent);
      if (replaceHost) {
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

    if (startingIndex <= 0) {
      return clientReq.getBytes();
    }
    // Debug message.
//    System.out.println("REMOVING: " + clientReq.substring(startingIndex - 1,
//        endingIndex));

    StringBuilder sb = new StringBuilder(clientReq);

    sb.delete(startingIndex - 1, endingIndex);

    // The case where the user inputs http://google.com with no / at the end
    // of the url.
    if (clientReq.charAt(endingIndex) == ' ') {
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

    if (urlSplit[0].equalsIgnoreCase("http:")) {
      return urlSplit[2];
    } else {
      return urlSplit[0];
    }
  }

  public static void printToConsole(String text) {
    System.out.println("[" + LocalDateTime.now() + "] " + text);
  }

  private void initialize() {
    try {
      ServerSocket serverListen = new ServerSocket(this.localPortNumber);
      printToConsole("Listening on port: " + this.localPortNumber);


      while (true) {

        Socket server = null;
        Socket client = null;
        try {

          client = serverListen.accept();

          String clientIp = client.getRemoteSocketAddress().toString();


          printToConsole("Incoming request from " + clientIp);

          final InputStream fromClient = client.getInputStream();
          final OutputStream toClient = client.getOutputStream();

          final byte[] req = extractBytes(fromClient, true);

          printToConsole(clientIp +
              " attempting to connect to " + this.destinationUrl);

          try {
            server = new Socket(this.hostName, this.remotePortNumber);
          } catch (IOException ioError) {
            printToConsole("Unable to connect to " + this.hostName +
                " on port " + this.remotePortNumber);
            client.close();
            continue;
          }

          printToConsole("Successfully connected to " +
              this.destinationUrl);

          final InputStream fromServer = server.getInputStream();
          final OutputStream toServer = server.getOutputStream();

          /*
          Partial reads problems occur here, the same problem occurs below as
          well. If all the data isn't read in one cycle, we have a problem
          and we won't get the complete data from the client/user.

          Originally wanted to create a while loop inside each one of the
          writes, so that we loop through the entire InputStreams until the
          socket closes, that way we won't run into the partial reads issue.
          */
          Thread tunnelClientInfo = new Thread() {
            public void run() {

              try {
                  toServer.write(req, 0, req.length);
                  toServer.flush();

              } catch (IOException ioError) {
                printToConsole("ERROR: Something happened to the client's "
                    + "socket. Unable to read info." + ioError);
              }
            }
          };

          // Initialize the thread. So both client -> proxy and server ->
          // proxy streams start at the same time.
          tunnelClientInfo.start();

          // Partial reads problem occur here. Same issue as the above thread.
          byte[] response = extractBytes(fromServer, false);

          try {

              toClient.write(response, 0, response.length);
              toClient.flush();

          } catch (IOException ioError) {
            printToConsole("ERROR: Something happened to the server's socket" +
                ". Unable to read from it." + ioError);
          }

        } finally {
          try {
            if (server != null) {
              server.close();
            }
            if (client != null) {
              client.close();
            }
          } catch (IOException e) {
            printToConsole("ERROR: Failed to close server/client sockets."
                + e);
          }
        }
      }

    } catch (IOException ioError) {
      printToConsole("ERROR: Port in use, failed to initialize." + ioError);
      System.exit(1);
    }
  }


}
