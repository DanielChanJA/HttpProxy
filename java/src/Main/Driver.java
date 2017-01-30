package Main;

/**
 * Created by Daniel Chan on 27/1/17.
 */
public class Driver {


  public static void main(String[] args) {

    if (args.length == 1 && Integer.parseInt(args[0]) >= 1 && Integer
        .parseInt(args[0]) <= 65535) {

      Proxy proxee = new Proxy(Integer.parseInt(args[0]));

    } else {

      Proxy.printToConsole("Defaulting to port 8000. Because we couldn't " +
          "find a valid integer to instantiate the proxy on.");
      Proxy proxee = new Proxy(8000);

    }

  }

}
