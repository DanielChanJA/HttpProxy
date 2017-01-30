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

  /**
   * Default constructor for Proxy, with a parameter of local port, to listen
   * for connections on.
   * @param localPort - The port which we listen for incoming connections.
   */
  public Proxy(int localPort) {
    this.localPortNumber = localPort;
    this.remotePortNumber = 80;
    this.initialize();

  }

  /**
   * This method extracts information from the InputStream either from the
   * client or the server. We extract the destination address, and assign it
   * to this loop iterations destination address.
   *
   * Here we also modify the requests coming from the client by passing it
   * along to another method, removeHost, specifically,
   * we modify the GET/POST parameters to retrieve and access certain
   * documents on the domain.
   *
   * @param incomingStream - The InputStream that we are extracting
   *                      information from.
   * @param replaceHost - A true/false, to indicate whether we are to replace
   *                    the host in the HTTP request/response.
   * @return Returns the content in an array of bytes to return to the
   * client, or for the proxy to pass to the destination.
   */
  private byte[] extractBytes(InputStream incomingStream, boolean replaceHost) {

    String requestContent;

    // Abnormally high because we need to compensate for partial reads issue
    // in initialization.
    byte[] buffer = new byte[100000];

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int len = 0;
      while ((len = incomingStream.read(buffer)) > 0) {
        baos.write(buffer, 0, len);
        baos.flush();
        break;
      }
      requestContent = new String(baos.toByteArray());

      if (replaceHost) {
        this.hostName = extractHost(requestContent);
        return removeHost(baos);
      }
    } catch (IOException ioException) {
      printToConsole("Failed to close stream" + ioException);
    }
    return buffer;
  }


  /**
   * This removes the hostname from the GET/POST request. For example, say we
   * have a GET request with the following format.
   * GET /proxy/http://httpbin.org/get
   * It would reformat to GET /get.
   *
   * @param request The client's InputStream, in ByteArray form to manipulate
   *                with.
   * @return Returns the byte representation of the modified HTTP request.
   */
  private byte[] removeHost(ByteArrayOutputStream request) {

    String clientReq = new String(request.toByteArray());
    int startingIndex = clientReq.indexOf(this.destinationUrl);
    int endingIndex = clientReq.indexOf(this.hostName) + this.hostName.length();

    // It can never be 0. The index has to be greater than the starting index.
    if (endingIndex == 0) {
      endingIndex = clientReq.indexOf(this.destinationUrl) + this.destinationUrl.length();
    } else if (startingIndex <= 0) {
      return clientReq.getBytes();
    }

    StringBuilder sb = new StringBuilder(clientReq);
    sb.delete(startingIndex - 1, endingIndex);

    // The case where the user inputs http://google.com with no / at the end
    // of the url.
    if (clientReq.charAt(endingIndex) == ' ') {
      sb.insert(4, '/');
    }
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

    if(urlSplit[0].equals("proxy") && urlSplit.length >= 2) {
      if (urlSplit[1].equalsIgnoreCase("http:")) {
        return urlSplit[3];
      } else {
        return urlSplit[1];
      }
    } else {
      return "";
    }
  }

  /**
   * Generic method to print to console with the timestamp and the message.
   * @param text - message to be printed to console.
   */
  public static void printToConsole(String text) {
    System.out.println("[" + LocalDateTime.now() + "] " + text);
  }

  /**
   * Initialization method, for initiating the server socket, to whatever was
   * defined in the main method in Driver, otherwise it defaults to the value
   * of 8000.
   *
   * We also listen for any incoming connections, and when we get a incoming
   * connection we process the request content and extract the destination
   * URL and modify the request content, then pass it through to the
   * destination.
   */
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

          if (server != null) {
            server.close();
          }
          if (client != null) {
            client.close();
          }

        } catch (IOException ioE) {

        }
      }
    } catch (IOException ioError) {
      printToConsole("ERROR: Port in use, failed to initialize." + ioError);
      System.exit(1);
    }
  }
}
