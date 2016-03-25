package greetings;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

public class HelloJava extends HttpServlet {

  private String message;

  public void init() throws ServletException
  {
      message = "Hello from a Java servlet implementation!";
  }

  public void doGet(HttpServletRequest request,
                    HttpServletResponse response)
            throws ServletException, IOException
  {
      response.setContentType("text/html");

      PrintWriter out = response.getWriter();
      out.println("<h1>" + message + "</h1>");
  }

  public void destroy()
  {
  }
}
