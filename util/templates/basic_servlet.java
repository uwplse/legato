package {{ output_package }};

import static edu.washington.cse.servlet.Util.nondetBool;
import static edu.washington.cse.servlet.Util.nondetInt;

import edu.washington.cse.servlet.*;
import edu.washington.cse.servlet.jsp.*;

import java.io.IOException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.RequestDispatcher;

{% block imports %}{% endblock %}

{% macro indexed_arg_list(iter_list) -%}
  {% for it in iter_list -%}
	{{ caller(it, loop.index0) }}{{ ", " if not loop.last }}
  {%- endfor %}
{% endmacro -%}

{% macro arg_list(iter_list) -%}
{% for it in iter_list -%}{{ caller(it) }}{{ ", " if not loop.last }}{% endfor -%}
{%- endmacro %}

{% macro simple_list(iter_list) -%}
{% call(it) arg_list(iter_list) -%}{{ it }}{% endcall -%}
{% endmacro -%}

{% macro named_list(iter_list, prop) -%}
{% call(it) arg_list(iter_list) -%}{{ it[prop] }}{% endcall -%}
{% endmacro -%}

{% macro nondet_chain(iter, nondet_arg="disp") -%}
{% if iter|length == 1 -%}{{ caller(iter|first, 0) }}
{% else -%}
  int {{ nondet_arg }} = nondetInt();
{% for it in iter -%}
  {% if loop.first -%}if({{nondet_arg}} == {{ loop.index0}}) {
  {% elif not loop.last -%}else if({{nondet_arg}} == {{ loop.index0 }}) {
  {% else -%} else { {% endif -%}
    {{ caller(it, loop.index0) }}
  }
{% endfor -%}
{% endif -%}
{% endmacro -%}

{% macro _dispatch_chain(m_name) -%}
public RequestDispatcher {{m_name}}(String url) {
  {% call(servlet, _) nondet_chain(servlets) -%}return new {{ servlet.dispatcher_class}}(({{servlet.type }})this.servlets[0]);{% endcall -%}
}
{% endmacro -%}

{% macro object_inst(type, var_name, args = []) -%}
{{ type }} {{ var_name }} = new {{ type }}({{ simple_list(args) }});
{%- endmacro %}

public class PseudoMain {
  public static class MyContext extends SimpleContext {
    {% for event in events -%}
	public void {{ event.notify_name }}({{ simple_list(event.notify_args) }}) {
	  {% for l in listeners[event.tag] -%}
	  {{ l.field_name }}.{{ event.listener_method }}(new {{ event.event_type }}({{ simple_list(event.event_args) }}));
	  {% endfor %}
	}
    {% endfor %}
    {% for l in all_listeners -%}
    public {{ l.type }} {{ l.field_name }};
    {% endfor %}

	{{ _dispatch_chain("getRequestDispatcher") }}
	{{ _dispatch_chain("getNamedDispatcher") }}

	{% if error_servlets|count > 0 %}
	public void handlePageException(Throwable t, SimpleHttpRequest req, SimpleHttpResponse resp) throws IOException, ServletException {
	  {% call(serv, _) nondet_chain(error_servlets) %}
		(({{serv}})this.servlets[0]).service(req, resp);
	  {% endcall %}
    }
    {% endif %}
	{% block contextextend %}{% endblock %}
  }
  
{% for servlet in servlets %}
  public static class {{ servlet.dispatcher_class }} implements RequestDispatcher {
    private {{ servlet.type }} delegate;
    public {{ servlet.dispatcher_class }}({{ servlet.type }} delegate) { this.delegate = delegate; }
    public void forward(ServletRequest req, ServletResponse resp) throws IOException, ServletException {
      this.delegate.service(req, resp);
    }
    public void include(ServletRequest req, ServletResponse resp) throws IOException, ServletException {
      this.delegate.service(req, resp);
    }
  }
{% endfor %}

  {% block inner_class_gen %}{% endblock %}
  public static void main(String[] args) throws ServletException, IOException {
    javax.servlet.jsp.JspFactory.setDefaultFactory(new SimpleJspFactory());
    MyContext context = new MyContext();
    {% for l in all_listeners %}
	context.{{ l.field_name }} = new {{ l.type }}();
    {% endfor %}
    context.notifyContextInitialized();
	{% for servlet in servlets %}
    // {{ servlet.name }}
	{{ object_inst(servlet.type, servlet.var_name) }}
    {{ servlet.var_name }}.init(new SimpleServletConfig(context));
    context.servlets[0] = {{ servlet.var_name }};
    {% endfor %}

	{% for f in filters %}
    {{ object_inst(f.type, f.var_name) }}
    {{ f.var_name }}.init(new SimpleFilterConfig(context));
    {% endfor %}
	{% block initialization %}{% endblock %}

	while(nondetBool()) {
       SimpleHttpRequest sr = new SimpleHttpRequest(context);
       SimpleHttpResponse sp = new SimpleHttpResponse();
       context.notifyRequestInitialized(sr);
       AllServletsHandlers {{ servlet_handler }} = new AllServletsHandlers({{ named_list(servlets, "var_name") }});

	   {% for f in filters|reverse %}
	   {{ object_inst(f.chain_type, f.chain_name, [ f.var_name, f.prev_handler ]) }}
       {% endfor %}
       {{ final_handler }}.doFilter(sr, sp);

	   LegatoKillRequest();

	   context.notifyRequestDestroyed(sr);
	   context.destroySession();
	   context.actSession();
	   context.passSession();
    }

    LegatoKillSession();

    {% for f in filters %}
    {{ f.var_name }}.destroy();
    {%- endfor %}

	{% for s in servlets %}
	{{ s.var_name }}.destroy();
    {%- endfor %}

    context.notifyContextDestroyed();
  }

  public static void LegatoKillSession() { }
  public static void LegatoKillRequest() { }
}

class AllServletsHandlers implements FilterChain {
  {% for s in servlets %}
  private {{ s.type }} sub_handler_{{loop.index0}};
  {%- endfor %}

  public AllServletsHandlers({% call(it, ind) indexed_arg_list(servlets) %}{{ it.type }} h{{ ind }}{% endcall %}) {
{% for s in servlets %}
    this.sub_handler_{{loop.index0}} = h{{loop.index0}};
{%- endfor %}
  }
  public void doFilter(ServletRequest r1, ServletResponse r2) throws IOException, ServletException {
{% call(it, ind) nondet_chain(servlets) %}{% if it.name not in ignored_tls %}this.sub_handler_{{ind}}.service(r1, r2);{% endif %}{% endcall %}
  }
}

{% for f in filters %}
class {{ f.chain_type }} implements FilterChain {
  private {{ f.prev_type }} delegate;
  private {{ f.type }} filter;
  {{ f.chain_type }}({{ f.type }} filter, {{ f.prev_type }} delegate) {
    this.delegate = delegate;
    this.filter = filter;
  }
  public void doFilter(ServletRequest r1, ServletResponse r2) throws IOException, ServletException {
    this.filter.doFilter(r1, r2, this.delegate);
  }
}
{% endfor %}

{% block private_classes %}{% endblock %}
