package zzyongx.fsyncer;

import java.io.IOException;
import java.util.Properties;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
  static final String TAG = "HttpServer";
  static final String MIME_JSON = "applicaion/json";
  
  public HttpServer(String ip, int port) throws IOException {
    super(ip, port);
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
  }

  @Override
  public Response serve(IHTTPSession session) {
    String uri = session.getUri();
    if (uri.equals("/all")) {
      return serveAllFiles();
    }
    return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Not Found");
  }

  Response serveAllFiles() {
    return newFixedLengthResponse(Response.Status.OK, MIME_JSON, "hello world");
  }
}
