package org.col.admin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.col.admin.config.AdminServerConfig;

public class IndexServlet extends HttpServlet {
  private final String monitorPath;

  /**
   * We configure the application context path manually as we run the servlet behind apache and rewrite paths.
   */
  public IndexServlet(AdminServerConfig cfg) {
    this.monitorPath = cfg.monitorPath;
  }

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    String path = req.getContextPath() + req.getServletPath();
    resp.setStatus(200);
    resp.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
    resp.setContentType("text/html");
    PrintWriter writer = resp.getWriter();

    try {
      String template = getResourceAsString("/index.html", "UTF-8");

      writer.println(MessageFormat.format(template, path));
    } finally {
      writer.close();
    }
  }

  String getResourceAsString(String resource, String charSet) throws IOException {
    InputStream in = this.getClass().getResourceAsStream(resource);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int len;
    while ((len = in.read(buffer)) != -1) {
      out.write(buffer, 0, len);
    }
    return out.toString(charSet);
  }
}