{% extends "basic_servlet.java" %}

{% block imports %}
import org.apache.struts.action.ActionServlet;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionForward;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
{% endblock %}

{% block inner_class_gen %}
  public static class SimpleActionServlet extends ActionServlet {
    public void init() throws ServletException { }
    public void destroy() { }
  }

  {% for action_servlet in action_servlets %}
  public static class {{ action_servlet.type }} extends SimpleActionServlet {
	public {{ action_servlet.action_type }} delegate;
	public {{ action_servlet.type }}() {
	  delegate = new {{ action_servlet.action_type }}();
      delegate.setServlet(this);
	}

	public void service(ServletRequest req, ServletResponse resp) throws IOException, ServletException {
      this.service((HttpServletRequest)req, (HttpServletResponse)resp);
    }
	public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
      try {
        delegate.{{ action_servlet.action_method }}(new ActionMapping(), {{ action_servlet.action_form }}, req, resp);
      } catch(Exception e) { }
	}
  }
  {% endfor %}

  public static abstract class UrlDispatchActionForward extends ActionForward {
    public UrlDispatchActionForward() {
     super();
    }
    public abstract void doForwardNow(ServletContext context, HttpServletRequest req, HttpServletResponse resp);
  }

  public static class NonDetForward extends UrlDispatchActionForward {
    public NonDetForward() { super(); }
    public void doForwardNow(ServletContext context, HttpServletRequest req, HttpServletResponse resp) {
      try {
        context.getRequestDispatcher(System.currentTimeMillis() + "").forward(req, resp);
      } catch(Exception e) { }
    }
  }

  {% for forward_action in forward_actions %}
  public static class {{ forward_action.type }} extends UrlDispatchActionForward {
    public {{ forward_action.type }}() { super(); }
	public void doForwardNow(ServletContext context, HttpServletRequest req, HttpServletResponse resp) {
     try {
	   ((MyContext)context).getRequestDispatcher("{{ forward_action.url }}").forward(req, resp);
     } catch(Exception e) { }
	}
  }
  {% endfor %}
{% endblock %}
