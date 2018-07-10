package net.ewant.rolling.support.rest;

import net.ewant.rolling.transaction.TransactionContext;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@WebFilter(displayName = "transactionServletInterceptFilter", urlPatterns = "/*" ,dispatcherTypes = {DispatcherType.REQUEST, DispatcherType.FORWARD})
public class ServletInterceptFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        String currentTransactionId = servletRequest.getParameter(TransactionContext.TRANSACTION_ID_PARAMETER_NAME);
        if(currentTransactionId != null && currentTransactionId.trim().length() > 0){
            TransactionContext.getContext().setTransactionId(currentTransactionId.trim());
        }
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String queryString = request.getQueryString();
        StringBuilder builder = new StringBuilder(request.getScheme());
        builder.append("://");
        builder.append(request.getServerName());
        builder.append(":");
        builder.append(request.getServerPort());
        builder.append(request.getRequestURI());
        if(queryString != null){
            builder.append("?");
            builder.append(queryString);
        }
        TransactionContext.getContext().setHttpEnterUrl(builder.toString());

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
    }
}
