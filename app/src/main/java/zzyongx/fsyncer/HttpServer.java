package zzyongx.fsyncer;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
  static final String TAG = "HttpServer";
  static final String MIME_JSON = "applicaion/json";

  static final String AUTH_TOKEN = "authorization";
  UserTokenPool tokenPool;
  Event event;

  public static class UserToken {
    public String token;
    public long   expireAt;
    
    public static UserToken fromString(String s) {
      long now = System.currentTimeMillis()/1000;
      
      String parts[] = s.split(":");
      if (parts.length == 2) {
        long ts = Long.parseLong(parts[1]);
        if (ts > now) {
          UserToken u = new UserToken();
          u.token = parts[0];
          u.expireAt = ts;
          return u;
        }
      }
      return null;
    }
    
    public String toString() {
      return token + ":" + String.valueOf(expireAt);
    }
  }

  public static class UserTokenPool extends HashMap<String, UserToken> {
    public void add(UserToken u) {
      put(u.token, u);
    }
    
    public UserToken getNew() {
      UserToken u = new UserToken();
      u.token = UUID.randomUUID().toString();
      u.expireAt = System.currentTimeMillis()/1000 + 86400;
      put(u.token, u);
      return u;
    }

    public Collection<UserToken> getAll() {
      return values();
    }
  }

  public interface Event {
    public UserTokenPool whenStart();
    public void whenStop(UserTokenPool pool);
    public boolean onNewSession();
  }
  
  public HttpServer(Event event, String ip, int port) throws IOException {
    super(ip, port);
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    this.event = event;
    tokenPool = event.whenStart();
  }

  @Override
  public void stop() {
    super.stop();
    event.whenStop(tokenPool);
  }

  Response.Status checkAuth(IHTTPSession session) {
    CookieHandler handler = session.getCookies();
    String token = handler.read(AUTH_TOKEN);

    if (token != null) {
      if (token != null && tokenPool.get(token) != null) return Response.Status.OK;
    }

    if (!event.onNewSession()) {
      return Response.Status.FORBIDDEN;
    }
    
    return Response.Status.UNAUTHORIZED;
  }

  Response addAuthCookie(Response response) {
    UserToken u = tokenPool.getNew();
    Cookie cookie = new Cookie(AUTH_TOKEN, u.token);
    response.addHeader("Set-Cookie", cookie.getHTTPHeader());
    return response;
  }

  @Override
  public Response serve(IHTTPSession session) {
    Response.Status status = checkAuth(session);
    if (status == Response.Status.FORBIDDEN) {
      return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_HTML, "Forbidden");
    }

    Response response;
    
    String uri = session.getUri();
    if (uri.equals("/all")) {
      response = serveAllFiles();
    } else {
      response = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Not Found");
    }

    if (status == Response.Status.UNAUTHORIZED) {
      response = addAuthCookie(response);
    }
    return response;
  }

  Response serveAllFiles() {
    return newFixedLengthResponse(Response.Status.OK, MIME_JSON, "hello world");
  }
}
