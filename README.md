# Simple HTTP Proxy
Written in Java

How the proxy works:

1. Server starts up either with a specified port, or will default to port 8000.

2. Server listens for incoming connections at that specified port.

3. Server accepts a incoming connection.

4. Server reads the request from the user and extracts the destination URL 
and modifies the HTTP request. 

5. Once the HTTP request has been modified, the request is relayed in 
byteform to the destination.

6. Once the destination receives the request, the destination opens a 
connection with the proxy server to transmit back the requested data.

7. Following that, the proxy server relays it back to the client.

Repeat 1-7 for every new connection.

To initialize with a special port, java -jar HttpProxy.jar [PORT NUMBER]




